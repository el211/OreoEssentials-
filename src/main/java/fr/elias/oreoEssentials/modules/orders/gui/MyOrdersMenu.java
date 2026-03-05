package fr.elias.oreoEssentials.modules.orders.gui;

import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows the player's own ACTIVE orders.
 * Each order has a cancel button that refunds remaining escrow.
 */
public final class MyOrdersMenu implements InventoryProvider {

    private final OrdersModule module;

    private MyOrdersMenu(OrdersModule module) {
        this.module = module;
    }

    public static SmartInventory getInventory(OrdersModule module) {
        return SmartInventory.builder()
                .id("oe_orders_myorders")
                .provider(new MyOrdersMenu(module))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(c(module.getConfig().guiTitle("my-orders", "&6&lMy Orders")))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();
        contents.fillBorders(ClickableItem.empty(glass(Material.ORANGE_STAINED_GLASS_PANE)));

        List<Order> myOrders = module.getService().getActiveOrdersByPlayer(player.getUniqueId());

        if (myOrders.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER,
                    module.getConfig().msg("my-orders.no-orders"))));
        } else {
            ClickableItem[] items = myOrders.stream()
                    .map(o -> orderItem(player, o))
                    .toArray(ClickableItem[]::new);
            pagination.setItems(items);
            pagination.setItemsPerPage(28);

            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            it.blacklist(1, 8).blacklist(2, 0).blacklist(2, 8)
              .blacklist(3, 0).blacklist(3, 8).blacklist(4, 1);
            pagination.addToIterator(it);
        }

        // Navigation
        if (!pagination.isFirst()) {
            contents.set(5, 3, ClickableItem.of(named(Material.ARROW, "&e&lPrevious Page"),
                    e -> { OrderBrowserMenu.click(player); getInventory(module).open(player, pagination.previous().getPage()); }));
        }
        if (!pagination.isLast()) {
            contents.set(5, 5, ClickableItem.of(named(Material.ARROW, "&e&lNext Page"),
                    e -> { OrderBrowserMenu.click(player); getInventory(module).open(player, pagination.next().getPage()); }));
        }

        // Back to browse
        contents.set(5, 4, ClickableItem.of(named(Material.ARROW, "&f&lBack to Market"),
                e -> { OrderBrowserMenu.click(player); OrderBrowserMenu.getInventory(module).open(player); }));

        // Close
        contents.set(5, 8, ClickableItem.of(named(Material.BARRIER, "&c&lClose"),
                e -> { OrderBrowserMenu.click(player); player.closeInventory(); }));
    }

    @Override public void update(Player player, InventoryContents contents) {}


    private ClickableItem orderItem(Player player, Order order) {
        ItemStack display = OrderBrowserMenu.resolveDisplayIcon(order);
        display.setAmount(1);

        ItemMeta meta = display.getItemMeta();
        meta.setDisplayName(c("&6&l" + order.getDisplayItemName()));

        var cfg = module.getConfig();
        var cur = module.getCurrency();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(cfg.msg("gui.label.remaining", Map.of(
                "remaining", String.valueOf(order.getRemainingQty()),
                "total",     String.valueOf(order.getTotalQty()))));
        lore.add(cfg.msg("gui.label.unit-price",
                Map.of("price", cur.format(order.getCurrencyId(), order.getUnitPrice()))));
        lore.add(cfg.msg("gui.label.escrow-left",
                Map.of("escrow", cur.format(order.getCurrencyId(), order.getEscrowRemaining()))));
        lore.add("");
        lore.add(cfg.msg("gui.action.cancel-order"));
        meta.setLore(lore);
        display.setItemMeta(meta);

        return ClickableItem.of(display, e -> {
            OrderBrowserMenu.click(player);
            // Open cancel confirm
            CancelConfirmMenu.getInventory(module, order).open(player);
        });
    }



    private static final class CancelConfirmMenu implements InventoryProvider {

        private final OrdersModule module;
        private final Order order;

        private CancelConfirmMenu(OrdersModule module, Order order) {
            this.module = module;
            this.order  = order;
        }

        static SmartInventory getInventory(OrdersModule module, Order order) {
            return SmartInventory.builder()
                    .id("oe_orders_cancel_confirm")
                    .provider(new CancelConfirmMenu(module, order))
                    .manager(module.getPlugin().getInvManager())
                    .size(3, 9)
                    .title(c(module.getConfig().guiTitle("cancel-confirm", "&c&lCancel Order?")))
                    .build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fillBorders(ClickableItem.empty(glass(Material.RED_STAINED_GLASS_PANE)));

            var cfg = module.getConfig();
            var cur = module.getCurrency();
            ItemStack info = named(Material.PAPER, "&f&lCancel Order?");
            ItemMeta meta = info.getItemMeta();
            List<String> lore = new ArrayList<>();
            String itemName = order.getDisplayItemName() != null ? order.getDisplayItemName() : "";
            lore.add(cfg.msg("gui.label.item", Map.of("item", itemName)));
            lore.add(cfg.msg("gui.label.qty", Map.of("qty", String.valueOf(order.getRemainingQty()))));
            lore.add(cfg.msg("gui.label.refund",
                    Map.of("refund", cur.format(order.getCurrencyId(), order.getEscrowRemaining()))));
            meta.setLore(lore);
            info.setItemMeta(meta);
            contents.set(1, 4, ClickableItem.empty(info));

            contents.set(2, 3, ClickableItem.of(named(Material.LIME_WOOL, "&a&lYes, Cancel & Refund"),
                    e -> {
                        OrderBrowserMenu.click(player);
                        player.closeInventory();
                        module.getService().cancelOrder(player, order.getId())
                                .thenAccept(ok -> {
                                    if (ok) {
                                        player.sendMessage(module.getConfig().msg("cancel.success",
                                                Map.of("refund", module.getCurrency().format(
                                                        order.getCurrencyId(), order.getEscrowRemaining()))));
                                    } else {
                                        player.sendMessage(module.getConfig().msg("cancel.failed"));
                                    }
                                });
                    }));

            contents.set(2, 5, ClickableItem.of(named(Material.RED_WOOL, "&c&lNo, Keep Order"),
                    e -> { OrderBrowserMenu.click(player); MyOrdersMenu.getInventory(module).open(player); }));
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
