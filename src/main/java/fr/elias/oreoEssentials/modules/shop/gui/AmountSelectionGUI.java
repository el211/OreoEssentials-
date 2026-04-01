package fr.elias.oreoEssentials.modules.shop.gui;

import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static fr.elias.oreoEssentials.util.Lang.color;

public final class AmountSelectionGUI {

    public enum Mode { BUY, SELL }

    private static final int[] STEPS = {1, 16, 32, 64, 128};

    private final ShopModule module;

    public AmountSelectionGUI(ShopModule module) {
        this.module = module;
    }


    public void open(Player player, ShopItem shopItem, Mode mode, Shop shop, int page) {
        open(player, shopItem, mode, shop, page, shopItem.getAmount());
    }

    public void open(Player player, ShopItem shopItem, Mode mode, Shop shop, int page, int currentQty) {
        String label = mode == Mode.BUY ? "&a&lBUY" : "&c&lSELL";

        SmartInventory.builder()
                .id("oe_shop_confirm_" + shopItem.getId() + "_" + player.getUniqueId())
                .title(color("&8Confirm " + (mode == Mode.BUY ? "Purchase" : "Sale") + " — " + label))
                .size(4, 9)
                .provider(new ConfirmProvider(module, shopItem, mode, shop, page, currentQty))
                .manager(module.getPlugin().getInvManager())
                .closeable(true)
                .updateFrequency(10)
                .build()
                .open(player);
    }


    private static final class ConfirmProvider implements InventoryProvider {

        private final ShopModule module;
        private final ShopItem   shopItem;
        private final Mode       mode;
        private final Shop       shop;
        private final int        page;
        private final int[]      qty;

        ConfirmProvider(ShopModule module, ShopItem shopItem,
                        Mode mode, Shop shop, int page, int startQty) {
            this.module   = module;
            this.shopItem = shopItem;
            this.mode     = mode;
            this.shop     = shop;
            this.page     = page;
            this.qty      = new int[]{Math.max(1, startQty)};
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(ClickableItem.empty(filler(Material.GRAY_STAINED_GLASS_PANE)));
            buildButtons(player, contents);
        }

        @Override
        public void update(Player player, InventoryContents contents) {
            buildButtons(player, contents);
        }


        /** Formats a price using the shop's custom currency or falls back to Vault symbol. */
        private String fmt(double price) {
            String cid = shop.getCurrencyId();
            if (cid != null) {
                CurrencyService cs = module.getPlugin().getCurrencyService();
                if (cs != null) return cs.formatBalance(cid, price);
            }
            return module.getShopConfig().getCurrencySymbol() + String.format("%.2f", price);
        }

        private void buildButtons(Player player, InventoryContents contents) {
            final double pricePerUnit = unitPrice(player);
            final double totalPrice   = pricePerUnit * qty[0];
            final boolean isBuy       = mode == Mode.BUY;

            Material addMat = isBuy ? Material.LIME_STAINED_GLASS_PANE  : Material.GREEN_STAINED_GLASS_PANE;
            Material subMat = isBuy ? Material.RED_STAINED_GLASS_PANE   : Material.ORANGE_STAINED_GLASS_PANE;
            for (int i = 0; i < STEPS.length; i++) {
                final int step = STEPS[i];
                int newQty  = qty[0] + step;
                double cost = pricePerUnit * newQty;
                ItemStack btn = buildButton(addMat, step,
                        "&a&l+" + step,
                        Arrays.asList(
                                "&7Increase by &e" + step,
                                "&7New total: &e" + newQty + " &7items",
                                "&7" + (isBuy ? "Cost" : "Earn") + ": &f" + fmt(cost),
                                "",
                                "&eClick to adjust"));
                contents.set(0, i, ClickableItem.from(btn, e -> qty[0] = Math.max(1, qty[0] + step)));
            }

            contents.set(1, 4, ClickableItem.empty(shopItem.buildItemStack()));

            String totalLabel = isBuy
                    ? "&7Total cost: &a" + fmt(totalPrice)
                    : "&7Total earn: &2" + fmt(totalPrice);
            ItemStack counter = buildButton(Material.PAPER, Math.min(qty[0], 64),
                    "&f&l" + qty[0] + " &7× " + formatName(),
                    Arrays.asList(
                            "&7Quantity: &e" + qty[0],
                            totalLabel, "",
                            "&7Use &a+&7/&c-&7 buttons then click &aConfirm"));
            contents.set(1, 5, ClickableItem.empty(counter));

            for (int i = 0; i < STEPS.length; i++) {
                final int step = STEPS[i];
                int newQty  = Math.max(1, qty[0] - step);
                double cost = pricePerUnit * newQty;
                ItemStack btn = buildButton(subMat, step,
                        "&c&l-" + step,
                        Arrays.asList(
                                "&7Decrease by &e" + step,
                                "&7New total: &e" + newQty + " &7items",
                                "&7" + (isBuy ? "Cost" : "Earn") + ": &f" + fmt(cost),
                                qty[0] - step < 1 ? "&8(clamped to 1)" : "",
                                "",
                                "&eClick to adjust"));
                contents.set(2, i, ClickableItem.from(btn, e -> qty[0] = Math.max(1, qty[0] - step)));
            }

            ItemStack cancelItem = simple(Material.BARRIER,
                    "&c&lCancel", "&7Go back to shop without buying");
            contents.set(3, 1, ClickableItem.from(cancelItem,
                    e -> module.getShopGUI().open(player, shop, page)));

            Material confirmMat   = isBuy ? Material.LIME_WOOL : Material.ORANGE_WOOL;
            String   confirmLabel = isBuy ? "&a&lConfirm Purchase" : "&2&lConfirm Sale";
            ItemStack confirmItem = buildButton(confirmMat, 1, confirmLabel,
                    Arrays.asList(
                            "&7Item:  &f" + formatName(),
                            "&7Qty:   &e" + qty[0],
                            "&7" + (isBuy ? "Cost" : "Earn") + ":  &f" + fmt(totalPrice),
                            "",
                            isBuy ? "&aClick to buy!" : "&2Click to sell!"));
            contents.set(3, 3, ClickableItem.from(confirmItem, e -> {
                int units = Math.max(1, qty[0] / Math.max(1, shopItem.getAmount()));
                if (isBuy) module.getTransactionProcessor().processBuy(player,  shopItem, units);
                else       module.getTransactionProcessor().processSell(player, shopItem, units);
                fr.elias.oreoEssentials.util.OreScheduler.runLater(module.getPlugin(),
                        () -> module.getShopGUI().open(player, shop, page), 1L);
            }));

            ItemStack resetItem = simple(Material.YELLOW_WOOL, "&e&lReset", "&7Reset quantity to &f1");
            contents.set(3, 5, ClickableItem.from(resetItem, e -> qty[0] = shopItem.getAmount()));

            if (mode == Mode.SELL) {
                int owned = countOwned(player);
                ItemStack maxItem = buildButton(Material.HOPPER, 1, "&6&lSell All",
                        Arrays.asList(
                                "&7You have: &e" + owned + " &7of this item",
                                "&7Click to set quantity to &e" + owned,
                                "",
                                "&eClick to auto-fill"));
                contents.set(3, 7, ClickableItem.from(maxItem, e -> qty[0] = Math.max(1, owned)));
            } else {
                // Get balance from the correct economy (custom currency or Vault)
                String cid = shop.getCurrencyId();
                if (cid != null) {
                    ItemStack maxItem = buildButton(Material.GOLD_INGOT, 1, "&6&lMax Affordable",
                            Arrays.asList(
                                    "&7Balance: &eLoading...",
                                    "&7Click to calculate your max affordable amount",
                                    "",
                                    "&eClick to auto-fill"));
                    contents.set(3, 7, ClickableItem.from(maxItem, e -> {
                        CurrencyService cs = module.getPlugin().getCurrencyService();
                        if (cs == null) return;
                        cs.getBalance(player.getUniqueId(), cid).thenAccept(balance ->
                                fr.elias.oreoEssentials.util.OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                                    int maxAfford = pricePerUnit > 0 ? (int) (balance / pricePerUnit) : 0;
                                    qty[0] = Math.max(1, maxAfford);
                                }));
                    }));
                } else {
                    double balance = module.getEconomy().getBalance(player);
                    int maxAfford = pricePerUnit > 0 ? (int)(balance / pricePerUnit) : 0;
                    int finalMax  = Math.max(1, maxAfford);
                    ItemStack maxItem = buildButton(Material.GOLD_INGOT, 1, "&6&lMax Affordable",
                            Arrays.asList(
                                    "&7Balance: &e" + fmt(balance),
                                    "&7You can afford: &e" + finalMax + " &7items",
                                    "",
                                    "&eClick to auto-fill"));
                    contents.set(3, 7, ClickableItem.from(maxItem, e -> qty[0] = finalMax));
                }
            }
        }


        private double unitPrice(Player player) {
            double pricePerStack = mode == Mode.BUY
                    ? module.getPriceModifierManager().getEffectiveBuyPrice(player.getUniqueId(), shopItem)
                    : module.getPriceModifierManager().getEffectiveSellPrice(player.getUniqueId(), shopItem);
            return pricePerStack / Math.max(1, shopItem.getAmount());
        }

        private int countOwned(Player player) {
            int count = 0;
            for (ItemStack s : player.getInventory().getContents()) {
                if (s != null && s.getType() == shopItem.getMaterial()) count += s.getAmount();
            }
            return count;
        }

        private String formatName() {
            String dn = shopItem.getDisplayName();
            return (dn != null && !dn.isEmpty())
                    ? dn
                    : shopItem.getMaterial().name().replace('_', ' ').toLowerCase();
        }


        private static ItemStack buildButton(Material mat, int amount, String name, List<String> lore) {
            ItemStack item = new ItemStack(mat, Math.max(1, amount));
            ItemMeta  meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(name));
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(color(l));
                meta.setLore(colored);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                item.setItemMeta(meta);
            }
            return item;
        }

        private static ItemStack simple(Material mat, String name, String lore) {
            return buildButton(mat, 1, name, List.of(lore));
        }

        private static ItemStack filler(Material mat) {
            ItemStack item = new ItemStack(mat);
            ItemMeta  meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                item.setItemMeta(meta);
            }
            return item;
        }

    }
}
