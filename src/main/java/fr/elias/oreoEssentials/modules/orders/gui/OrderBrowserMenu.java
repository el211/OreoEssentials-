package fr.elias.oreoEssentials.modules.orders.gui;

import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.service.OrderService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main order browser — shows all ACTIVE buy-request orders with pagination.
 * Registered in OrdersGuiManager for live-refresh on cross-server events.
 */
public final class OrderBrowserMenu implements InventoryProvider {

    private final OrdersModule module;

    private OrderBrowserMenu(OrdersModule module) {
        this.module = module;
    }

    public static SmartInventory getInventory(OrdersModule module) {
        var cfg = module.getConfig();
        String title = cfg.guiTitle("browse", "&6&lMarket Orders");

        return SmartInventory.builder()
                .id("oe_orders_browse")
                .provider(new OrderBrowserMenu(module))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(title)
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();

        // Register viewer for live-refresh
        module.getGuiManager().registerViewer(player.getUniqueId(), pagination.getPage());

        // Border
        Material border = module.getConfig().guiBorder("browse", Material.GRAY_STAINED_GLASS_PANE);
        contents.fillBorders(ClickableItem.empty(glass(border)));

        List<Order> orders = module.getService().getActiveOrders();

        if (orders.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER,
                    module.getConfig().msg("browse.no-orders"))));
        } else {
            ClickableItem[] items = orders.stream()
                    .map(o -> orderItem(player, o))
                    .toArray(ClickableItem[]::new);

            pagination.setItems(items);
            pagination.setItemsPerPage(28);

            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            // Blacklist border columns
            it.blacklist(1, 8).blacklist(2, 0).blacklist(2, 8)
              .blacklist(3, 0).blacklist(3, 8).blacklist(4, 1);
            pagination.addToIterator(it);
        }

        renderNav(player, contents, pagination);
        renderControls(player, contents);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        module.getGuiManager().updatePage(player.getUniqueId(), contents.pagination().getPage());
    }


    private ClickableItem orderItem(Player viewer, Order order) {
        ItemStack display = resolveDisplayIcon(order);
        display.setAmount(1);

        ItemMeta meta = display.getItemMeta();
        String dispName = order.getDisplayItemName() != null ? order.getDisplayItemName()
                : display.getType().name().replace('_', ' ');
        meta.setDisplayName(c("&e&l" + dispName));

        var cfg = module.getConfig();
        var cur = module.getCurrency();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(cfg.msg("gui.label.requested-by", Map.of("player", order.getRequesterName())));
        lore.add(cfg.msg("gui.label.remaining", Map.of(
                "remaining", String.valueOf(order.getRemainingQty()),
                "total",     String.valueOf(order.getTotalQty()))));
        lore.add(cfg.msg("gui.label.unit-price",
                Map.of("price", cur.format(order.getCurrencyId(), order.getUnitPrice()))));
        lore.add(cfg.msg("gui.label.currency",
                Map.of("currency", cur.currencyDisplayName(order.getCurrencyId()))));
        lore.add(cfg.msg("gui.label.escrow-left",
                Map.of("escrow", cur.format(order.getCurrencyId(), order.getEscrowRemaining()))));
        if (order.getItemsAdderId() != null)
            lore.add(cfg.msg("gui.label.custom-itemsadder", Map.of("id", order.getItemsAdderId())));
        if (order.getNexoId() != null)
            lore.add(cfg.msg("gui.label.custom-nexo", Map.of("id", order.getNexoId())));
        if (order.getOraxenId() != null)
            lore.add(cfg.msg("gui.label.custom-oraxen", Map.of("id", order.getOraxenId())));
        lore.add("");

        if (order.getRequesterUuid().equals(viewer.getUniqueId())) {
            lore.add(cfg.msg("gui.action.your-order"));
        } else {
            lore.add(cfg.msg("gui.action.fill-order"));
        }
        lore.add("");

        meta.setLore(lore);
        display.setItemMeta(meta);

        return ClickableItem.of(display, e -> {
            click(viewer);
            if (order.getRequesterUuid().equals(viewer.getUniqueId())) {
                MyOrdersMenu.getInventory(module).open(viewer);
            } else {
                FillOrderMenu.getInventory(module, order).open(viewer);
            }
        });
    }

    /**
     * Resolves the display icon for an order.
     * Tries ItemsAdder / Nexo APIs first (by stored ID) so the correct
     * texture is shown even after cross-server sync. Falls back to the
     * deserialized item or PAPER.
     */
    static ItemStack resolveDisplayIcon(Order order) {
        // 1. ItemsAdder
        if (order.getItemsAdderId() != null) {
            try {
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = cs.getMethod("getInstance", String.class).invoke(null, order.getItemsAdderId());
                if (stack != null) {
                    ItemStack is = (ItemStack) cs.getMethod("getItemStack").invoke(stack);
                    if (is != null) return is.clone();
                }
            } catch (Throwable ignored) {}
        }
        // 2. Nexo
        if (order.getNexoId() != null) {
            try {
                Class<?> items  = Class.forName("com.nexomc.nexo.api.NexoItems");
                Object   builder = items.getMethod("itemFromId", String.class).invoke(null, order.getNexoId());
                if (builder != null) {
                    Class<?> builderClass = builder.getClass();
                    ItemStack is = (ItemStack) builderClass.getMethod("build").invoke(builder);
                    if (is != null) return is.clone();
                }
            } catch (Throwable ignored) {}
        }
        // 3. Deserialized base64 item
        try {
            ItemStack raw = OrderService.deserializeItem(order.getItemData());
            if (raw != null) return raw.clone();
        } catch (Throwable ignored) {}
        // 4. Fallback
        return new ItemStack(Material.PAPER);
    }


    private void renderNav(Player p, InventoryContents c, Pagination pg) {
        var cfg = module.getConfig();

        if (!pg.isFirst()) {
            int slot = cfg.guiSlot("browse", "prev-page", 48);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "prev-page", Material.ARROW),
                            cfg.guiNameRaw("browse", "prev-page", "&e&lPrevious Page")),
                    e -> { click(p); getInventory(module).open(p, pg.previous().getPage()); }));
        }

        if (!pg.isLast()) {
            int slot = cfg.guiSlot("browse", "next-page", 50);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "next-page", Material.ARROW),
                            cfg.guiNameRaw("browse", "next-page", "&e&lNext Page")),
                    e -> { click(p); getInventory(module).open(p, pg.next().getPage()); }));
        }

        int piSlot = cfg.guiSlot("browse", "page-indicator", 49);
        String piName = cfg.guiNameRaw("browse", "page-indicator", "&ePage &f{page}")
                .replace("{page}", String.valueOf(pg.getPage() + 1));
        c.set(piSlot / 9, piSlot % 9, ClickableItem.empty(named(Material.PAPER, piName)));
    }

    private void renderControls(Player p, InventoryContents c) {
        var cfg = module.getConfig();

        // Create order button
        int createSlot = cfg.guiSlot("browse", "create-order", 45);
        c.set(createSlot / 9, createSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("browse", "create-order", Material.WRITABLE_BOOK),
                        cfg.guiNameRaw("browse", "create-order", "&a&lCreate Order")),
                e -> { click(p); CreateOrderFlow.openItemStep(module, p); }));

        // My orders button
        int mySlot = cfg.guiSlot("browse", "my-orders", 46);
        c.set(mySlot / 9, mySlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("browse", "my-orders", Material.CHEST),
                        cfg.guiNameRaw("browse", "my-orders", "&6&lMy Orders")),
                e -> { click(p); MyOrdersMenu.getInventory(module).open(p); }));

        // Close button
        int closeSlot = cfg.guiSlot("browse", "close", 53);
        c.set(closeSlot / 9, closeSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("browse", "close", Material.BARRIER),
                        cfg.guiNameRaw("browse", "close", "&c&lClose")),
                e -> { click(p); p.closeInventory(); }));
    }


    static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    static ItemStack glass(Material m) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(" ");
        i.setItemMeta(meta);
        return i;
    }

    static ItemStack named(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(c(name));
        i.setItemMeta(meta);
        return i;
    }

    static void click(Player p) {
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .5f, 1f); }
        catch (Throwable ignored) {}
    }
}
