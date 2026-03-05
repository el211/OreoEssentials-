package fr.elias.oreoEssentials.modules.orders.gui;

import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.modules.orders.OrdersModule;
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
 * Multi-step order creation flow:
 *   Step 1 → ITEM     : player holds item; confirm or pick
 *   Step 2 → QTY      : chat input for quantity
 *   Step 3 → CURRENCY : GUI pick (Vault or custom currency)
 *   Step 4 → PRICE    : chat input for unit price
 *   Step 5 → CONFIRM  : summary → click to submit
 *
 * Pending state is kept in static maps (same pattern as AuctionHouseModule).
 */
public final class CreateOrderFlow {


    public record PendingQty(ItemStack item) {}
    public record PendingPrice(ItemStack item, int qty, String currencyId) {}

    private static final Map<UUID, PendingQty>   waitingQty   = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingPrice> waitingPrice = new ConcurrentHashMap<>();


    /**
     * Opens the item-confirm screen. Player must hold the item they want to buy.
     * If hand is empty, sends a message and returns.
     */
    public static void openItemStep(OrdersModule module, Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            p.sendMessage(module.getConfig().msg("create.hold-item"));
            return;
        }
        ConfirmItemMenu.getInventory(module, hand.clone()).open(p);
    }


    public static void startQtyInput(OrdersModule module, Player p, ItemStack item) {
        p.closeInventory();
        waitingQty.put(p.getUniqueId(), new PendingQty(item));
        p.sendMessage(module.getConfig().msg("create.enter-qty"));
    }

    public static boolean isWaitingForQty(UUID uuid) {
        return waitingQty.containsKey(uuid);
    }

    public static boolean consumeQtyInput(OrdersModule module, Player p, String raw) {
        PendingQty pq = waitingQty.remove(p.getUniqueId());
        if (pq == null) return false;
        int qty;
        try { qty = Integer.parseInt(raw.trim()); } catch (NumberFormatException e) {
            waitingQty.put(p.getUniqueId(), pq);
            p.sendMessage(module.getConfig().msg("create.invalid-qty"));
            return true;
        }
        if (qty <= 0 || qty > module.getConfig().maxQtyPerOrder()) {
            waitingQty.put(p.getUniqueId(), pq);
            p.sendMessage(module.getConfig().msg("create.invalid-qty-range",
                    Map.of("max", String.valueOf(module.getConfig().maxQtyPerOrder()))));
            return true;
        }
        // Move to step 3 — open currency picker on main thread
        final int finalQty = qty;
        org.bukkit.Bukkit.getScheduler().runTask(module.getPlugin(), () ->
                CurrencyPickerMenu.getInventory(module, pq.item(), finalQty).open(p));
        return true;
    }


    public static void startPriceInput(OrdersModule module, Player p,
                                       ItemStack item, int qty, String currencyId) {
        p.closeInventory();
        waitingPrice.put(p.getUniqueId(), new PendingPrice(item, qty, currencyId));
        p.sendMessage(module.getConfig().msg("create.enter-price",
                Map.of("currency", module.getCurrency().currencyDisplayName(currencyId))));
    }

    public static boolean isWaitingForPrice(UUID uuid) {
        return waitingPrice.containsKey(uuid);
    }

    public static boolean consumePriceInput(OrdersModule module, Player p, String raw) {
        PendingPrice pp = waitingPrice.remove(p.getUniqueId());
        if (pp == null) return false;
        double price;
        try { price = Double.parseDouble(raw.trim().replace(',', '.')); }
        catch (NumberFormatException e) {
            waitingPrice.put(p.getUniqueId(), pp);
            p.sendMessage(module.getConfig().msg("create.invalid-price"));
            return true;
        }
        if (price < module.getConfig().minUnitPrice()) {
            waitingPrice.put(p.getUniqueId(), pp);
            p.sendMessage(module.getConfig().msg("create.price-too-low",
                    Map.of("min", String.format("%.2f", module.getConfig().minUnitPrice()))));
            return true;
        }
        final double finalPrice = price;
        org.bukkit.Bukkit.getScheduler().runTask(module.getPlugin(), () ->
                ConfirmOrderMenu.getInventory(module, pp.item(), pp.qty(), pp.currencyId(), finalPrice).open(p));
        return true;
    }


    private static final class ConfirmItemMenu implements InventoryProvider {

        private final OrdersModule module;
        private final ItemStack item;

        private ConfirmItemMenu(OrdersModule module, ItemStack item) {
            this.module = module;
            this.item   = item;
        }

        static SmartInventory getInventory(OrdersModule module, ItemStack item) {
            return SmartInventory.builder()
                    .id("oe_orders_create_item")
                    .provider(new ConfirmItemMenu(module, item))
                    .manager(module.getPlugin().getInvManager())
                    .size(3, 9)
                    .title(c(module.getConfig().guiTitle("create.item", "&a&lConfirm Item")))
                    .build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fillBorders(ClickableItem.empty(glass(Material.GREEN_STAINED_GLASS_PANE)));

            // Show item in center
            ItemStack display = item.clone();
            ItemMeta meta = display.hasItemMeta() ? display.getItemMeta() : display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(c("&7This is the item buyers will deliver."));
            meta.setLore(lore);
            display.setItemMeta(meta);
            contents.set(1, 4, ClickableItem.empty(display));

            // Confirm
            contents.set(2, 3, ClickableItem.of(named(Material.LIME_WOOL, "&a&lConfirm"),
                    e -> { OrderBrowserMenu.click(player); startQtyInput(module, player, item); }));

            // Cancel
            contents.set(2, 5, ClickableItem.of(named(Material.RED_WOOL, "&c&lCancel"),
                    e -> { OrderBrowserMenu.click(player); OrderBrowserMenu.getInventory(module).open(player); }));
        }

        @Override public void update(Player player, InventoryContents contents) {}
    }

    private static final class CurrencyPickerMenu implements InventoryProvider {

        private final OrdersModule module;
        private final ItemStack item;
        private final int qty;

        private CurrencyPickerMenu(OrdersModule module, ItemStack item, int qty) {
            this.module = module;
            this.item   = item;
            this.qty    = qty;
        }

        static SmartInventory getInventory(OrdersModule module, ItemStack item, int qty) {
            return SmartInventory.builder()
                    .id("oe_orders_create_currency")
                    .provider(new CurrencyPickerMenu(module, item, qty))
                    .manager(module.getPlugin().getInvManager())
                    .size(3, 9)
                    .title(c(module.getConfig().guiTitle("create.currency", "&b&lSelect Currency")))
                    .build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fillBorders(ClickableItem.empty(glass(Material.CYAN_STAINED_GLASS_PANE)));

            int col = 1;

            // Vault option
            if (module.getConfig().allowVault()) {
                contents.set(1, col++, vaultButton(player));
            }

            // Custom currencies
            if (module.getConfig().allowCustomCurrencies()
                    && module.getPlugin().getCurrencyService() != null) {
                for (Currency cur : module.getPlugin().getCurrencyService().getAllCurrencies()) {
                    if (col > 7) break;
                    contents.set(1, col++, currencyButton(player, cur));
                }
            }

            // Back
            contents.set(2, 4, ClickableItem.of(named(Material.ARROW, "&e&lBack"),
                    e -> { OrderBrowserMenu.click(player); ConfirmItemMenu.getInventory(module, item).open(player); }));
        }

        @Override public void update(Player player, InventoryContents contents) {}

        private ClickableItem vaultButton(Player p) {
            ItemStack i = named(Material.GOLD_INGOT, "&6Vault Money");
            ItemMeta meta = i.getItemMeta();
            meta.setLore(List.of(c("&7Standard server money"), c(""), c("&aClick to use")));
            i.setItemMeta(meta);
            return ClickableItem.of(i, e -> {
                OrderBrowserMenu.click(p);
                startPriceInput(module, p, item, qty, null);
            });
        }

        private ClickableItem currencyButton(Player p, Currency cur) {
            ItemStack i = named(Material.SUNFLOWER, cur.getDisplayName());
            ItemMeta meta = i.getItemMeta();
            meta.setLore(List.of(c("&7Symbol: &f" + cur.getSymbol()), c(""), c("&aClick to use")));
            i.setItemMeta(meta);
            return ClickableItem.of(i, e -> {
                OrderBrowserMenu.click(p);
                startPriceInput(module, p, item, qty, cur.getId());
            });
        }
    }

    private static final class ConfirmOrderMenu implements InventoryProvider {

        private final OrdersModule module;
        private final ItemStack item;
        private final int qty;
        private final String currencyId;
        private final double unitPrice;

        private ConfirmOrderMenu(OrdersModule m, ItemStack item, int qty, String cid, double price) {
            this.module     = m;
            this.item       = item;
            this.qty        = qty;
            this.currencyId = cid;
            this.unitPrice  = price;
        }

        static SmartInventory getInventory(OrdersModule module, ItemStack item,
                                           int qty, String currencyId, double price) {
            return SmartInventory.builder()
                    .id("oe_orders_create_confirm")
                    .provider(new ConfirmOrderMenu(module, item, qty, currencyId, price))
                    .manager(module.getPlugin().getInvManager())
                    .size(3, 9)
                    .title(c(module.getConfig().guiTitle("create.confirm", "&a&lConfirm Order")))
                    .build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fillBorders(ClickableItem.empty(glass(Material.LIME_STAINED_GLASS_PANE)));

            double total = unitPrice * qty;
            double fee   = module.getConfig().feesEnabled()
                    ? total * module.getConfig().createFeePercent() / 100.0 : 0.0;
            double charge = total + fee;
            String cur = module.getCurrency().currencyDisplayName(currencyId);

            // Summary display
            ItemStack summary = named(Material.PAPER, "&f&lOrder Summary");
            ItemMeta meta = summary.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(c("&7Item: &f" + (item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName() : item.getType().name())));
            lore.add(c("&7Quantity: &f" + qty));
            lore.add(c("&7Unit price: &a" + module.getCurrency().format(currencyId, unitPrice)));
            lore.add(c("&7Currency: &b" + cur));
            lore.add(c("&7Total escrow: &e" + module.getCurrency().format(currencyId, total)));
            if (fee > 0) lore.add(c("&7Creation fee: &c" + module.getCurrency().format(currencyId, fee)));
            lore.add(c("&7You will pay: &c" + module.getCurrency().format(currencyId, charge)));
            meta.setLore(lore);
            summary.setItemMeta(meta);
            contents.set(1, 4, ClickableItem.empty(summary));

            // Confirm
            contents.set(2, 3, ClickableItem.of(named(Material.LIME_WOOL, "&a&lConfirm & Pay"),
                    e -> {
                        OrderBrowserMenu.click(player);
                        player.closeInventory();
                        module.getService()
                                .createOrder(player, item, qty, currencyId, unitPrice)
                                .thenAccept(errKey -> {
                                    if (errKey == null) {
                                        player.sendMessage(module.getConfig().msg("create.success",
                                                Map.of("qty", String.valueOf(qty),
                                                       "price", module.getCurrency().format(currencyId, unitPrice))));
                                        org.bukkit.Bukkit.getScheduler().runTask(module.getPlugin(), () ->
                                                OrderBrowserMenu.getInventory(module).open(player));
                                    } else {
                                        player.sendMessage(module.getConfig().msg(errKey));
                                    }
                                });
                    }));

            // Cancel
            contents.set(2, 5, ClickableItem.of(named(Material.RED_WOOL, "&c&lCancel"),
                    e -> { OrderBrowserMenu.click(player); OrderBrowserMenu.getInventory(module).open(player); }));
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
