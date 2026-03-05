package fr.elias.oreoEssentials.modules.orders.gui;

import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fill flow:
 *   Step 1 → order details GUI with fill button
 *   Step 2 → chat: enter fill quantity
 *   Step 3 → confirm GUI
 *   On confirm → items taken, seller paid
 */
public final class FillOrderMenu implements InventoryProvider {

    // ── Pending fill quantity chat input ──────────────────────────────────────

    public record PendingFill(Order order) {}
    private static final Map<UUID, PendingFill> waitingFill = new ConcurrentHashMap<>();

    public static boolean isWaitingForFillQty(UUID uuid) { return waitingFill.containsKey(uuid); }

    public static boolean consumeFillQtyInput(OrdersModule module, Player p, String raw) {
        PendingFill pf = waitingFill.remove(p.getUniqueId());
        if (pf == null) return false;

        int qty;
        try { qty = Integer.parseInt(raw.trim()); } catch (NumberFormatException e) {
            waitingFill.put(p.getUniqueId(), pf);
            p.sendMessage(module.getConfig().msg("fill.invalid-qty"));
            return true;
        }

        Order order = pf.order();
        if (qty <= 0 || qty > order.getRemainingQty()) {
            // Re-check live remaining
            Order live = module.getService().findOrder(order.getId()).orElse(null);
            int maxQty = live != null ? live.getRemainingQty() : order.getRemainingQty();
            if (qty <= 0 || qty > maxQty) {
                waitingFill.put(p.getUniqueId(), pf);
                p.sendMessage(module.getConfig().msg("fill.invalid-qty-range",
                        Map.of("max", String.valueOf(maxQty))));
                return true;
            }
        }

        final int finalQty = qty;
        org.bukkit.Bukkit.getScheduler().runTask(module.getPlugin(), () ->
                ConfirmFillMenu.getInventory(module, order, finalQty).open(p));
        return true;
    }

    // ── Step 1: Order details ─────────────────────────────────────────────────

    private final OrdersModule module;
    private final Order order;

    private FillOrderMenu(OrdersModule module, Order order) {
        this.module = module;
        this.order  = order;
    }

    public static SmartInventory getInventory(OrdersModule module, Order order) {
        return SmartInventory.builder()
                .id("oe_orders_fill_" + order.getId())
                .provider(new FillOrderMenu(module, order))
                .manager(module.getPlugin().getInvManager())
                .size(4, 9)
                .title(c(module.getConfig().guiTitle("fill", "&e&lFill Order")))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fillBorders(ClickableItem.empty(glass(Material.YELLOW_STAINED_GLASS_PANE)));

        // Order info panel
        var cfg = module.getConfig();
        var cur = module.getCurrency();
        ItemStack info = named(Material.PAPER, "&f&lOrder Details");
        ItemMeta meta = info.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(cfg.msg("gui.label.requested-by", Map.of("player", order.getRequesterName())));
        lore.add(cfg.msg("gui.label.item", Map.of("item", order.getDisplayItemName() != null ? order.getDisplayItemName() : "")));
        lore.add(cfg.msg("gui.label.remaining", Map.of(
                "remaining", String.valueOf(order.getRemainingQty()),
                "total",     String.valueOf(order.getTotalQty()))));
        lore.add(cfg.msg("gui.label.unit-price",
                Map.of("price", cur.format(order.getCurrencyId(), order.getUnitPrice()))));
        lore.add(cfg.msg("gui.label.currency",
                Map.of("currency", cur.currencyDisplayName(order.getCurrencyId()))));
        lore.add("");
        double earn = cfg.feesEnabled()
                ? order.getUnitPrice() * (1 - cfg.fillFeePercent() / 100.0)
                : order.getUnitPrice();
        lore.add(cfg.msg("gui.label.earn-per-item",
                Map.of("earn", cur.format(order.getCurrencyId(), earn))));
        meta.setLore(lore);
        info.setItemMeta(meta);
        contents.set(1, 4, ClickableItem.empty(info));

        // Show the actual item (using icon resolver for ItemsAdder/Nexo support)
        ItemStack display = OrderBrowserMenu.resolveDisplayIcon(order);
        display.setAmount(Math.min(order.getRemainingQty(), 64));
        contents.set(1, 2, ClickableItem.empty(display));

        // Fill button
        contents.set(2, 3, ClickableItem.of(named(Material.LIME_WOOL, "&a&lFill This Order"),
                e -> {
                    OrderBrowserMenu.click(player);
                    Order live = module.getService().findOrder(order.getId()).orElse(null);
                    if (live == null || live.getRemainingQty() <= 0) {
                        player.closeInventory();
                        player.sendMessage(module.getConfig().msg("fill.order-gone"));
                        return;
                    }
                    player.closeInventory();
                    waitingFill.put(player.getUniqueId(), new PendingFill(live));
                    player.sendMessage(module.getConfig().msg("fill.enter-qty",
                            Map.of("max", String.valueOf(live.getRemainingQty()))));
                }));

        // Back
        contents.set(2, 5, ClickableItem.of(named(Material.ARROW, "&e&lBack"),
                e -> { OrderBrowserMenu.click(player); OrderBrowserMenu.getInventory(module).open(player); }));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    // ── Step 3: Confirm fill ──────────────────────────────────────────────────

    private static final class ConfirmFillMenu implements InventoryProvider {

        private final OrdersModule module;
        private final Order order;
        private final int fillQty;

        private ConfirmFillMenu(OrdersModule module, Order order, int fillQty) {
            this.module  = module;
            this.order   = order;
            this.fillQty = fillQty;
        }

        static SmartInventory getInventory(OrdersModule module, Order order, int fillQty) {
            return SmartInventory.builder()
                    .id("oe_orders_fill_confirm")
                    .provider(new ConfirmFillMenu(module, order, fillQty))
                    .manager(module.getPlugin().getInvManager())
                    .size(3, 9)
                    .title(c(module.getConfig().guiTitle("fill.confirm", "&e&lConfirm Fill")))
                    .build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fillBorders(ClickableItem.empty(glass(Material.ORANGE_STAINED_GLASS_PANE)));

            var cfg = module.getConfig();
            var cur = module.getCurrency();
            double gross = order.getUnitPrice() * fillQty;
            double fee   = cfg.feesEnabled()
                    ? gross * cfg.fillFeePercent() / 100.0 : 0.0;
            double net   = gross - fee;

            ItemStack summary = named(Material.PAPER, "&f&lFill Summary");
            ItemMeta meta = summary.getItemMeta();
            List<String> lore = new ArrayList<>();
            String itemName = order.getDisplayItemName() != null ? order.getDisplayItemName() : "";
            lore.add(cfg.msg("gui.label.deliver",
                    Map.of("item", itemName, "qty", String.valueOf(fillQty))));
            lore.add(cfg.msg("gui.label.you-receive",
                    Map.of("net", cur.format(order.getCurrencyId(), net))));
            if (fee > 0) lore.add(cfg.msg("gui.label.fill-fee",
                    Map.of("fee", cur.format(order.getCurrencyId(), fee))));
            meta.setLore(lore);
            summary.setItemMeta(meta);
            contents.set(1, 4, ClickableItem.empty(summary));

            contents.set(2, 3, ClickableItem.of(named(Material.LIME_WOOL, "&a&lConfirm"),
                    e -> {
                        OrderBrowserMenu.click(player);
                        player.closeInventory();
                        module.getService().fillOrder(player, order.getId(), fillQty)
                                .thenAccept(result -> {
                                    if (result.isSuccess()) {
                                        player.sendMessage(module.getConfig().msg("fill.success",
                                                Map.of("qty",     String.valueOf(fillQty),
                                                       "item",    order.getDisplayItemName(),
                                                       "paid",    module.getCurrency().format(order.getCurrencyId(), result.getPaidToSeller()))));
                                    } else {
                                        String key = switch (result.getOutcome()) {
                                            case NOT_FOUND       -> "fill.order-gone";
                                            case ALREADY_CLOSED  -> "fill.already-closed";
                                            case INSUFFICIENT_QTY-> "fill.stale-qty";
                                            default              -> "fill.error";
                                        };
                                        player.sendMessage(module.getConfig().msg(key));
                                        // Refresh browser on main thread
                                        org.bukkit.Bukkit.getScheduler().runTask(module.getPlugin(), () ->
                                                OrderBrowserMenu.getInventory(module).open(player));
                                    }
                                });
                    }));

            contents.set(2, 5, ClickableItem.of(named(Material.RED_WOOL, "&c&lCancel"),
                    e -> { OrderBrowserMenu.click(player); FillOrderMenu.getInventory(module, order).open(player); }));
        }

        @Override public void update(Player player, InventoryContents contents) {}
    }


    static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    static ItemStack named(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(c(name));
        i.setItemMeta(meta);
        return i;
    }

    static ItemStack glass(Material m) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(" ");
        i.setItemMeta(meta);
        return i;
    }
}
