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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static fr.elias.oreoEssentials.util.Lang.color;

public final class ShopGUI {

    private final ShopModule module;

    public ShopGUI(ShopModule module) {
        this.module = module;
    }


    public void open(Player player, Shop shop, int page) {
        if (shop == null) return;

        int safePage   = Math.max(1, page);
        int totalPages = Math.max(1, shop.getTotalPages());
        if (safePage > totalPages) safePage = totalPages;

        String title = color(shop.getTitle())
                + (totalPages > 1 ? color(" &8[" + safePage + "/" + totalPages + "]") : "");

        SmartInventory.builder()
                .id("oe_shop_" + shop.getId() + "_p" + safePage)
                .title(title)
                .size(shop.getRows(), 9)
                .provider(new ShopProvider(module, shop, safePage))
                .manager(module.getPlugin().getInvManager())
                .closeable(true)
                .updateFrequency(20)
                .build()
                .open(player);
    }


    private static final class ShopProvider implements InventoryProvider {

        private final ShopModule module;
        private final Shop shop;
        private final int  page;

        ShopProvider(ShopModule module, Shop shop, int page) {
            this.module = module;
            this.shop   = shop;
            this.page   = page;
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            final int rows   = Math.max(1, shop.getRows());
            final int navRow = Math.max(0, rows - 1);
            final String sym = module.getShopConfig().getCurrencySymbol();
            final int total  = Math.max(1, shop.getTotalPages());

            final int prevSlot = module.getShopConfig().getPreviousPageSlot(); // default 45 → navRow,0
            final int nextSlot = module.getShopConfig().getNextPageSlot();     // default 53 → navRow,8
            final int backSlot = module.getShopConfig().getBackButtonSlot();   // default 49 → navRow,4

            ItemStack navFill = filler(Material.GRAY_STAINED_GLASS_PANE);
            contents.fillRow(navRow, ClickableItem.empty(navFill));

            if (!shop.isHideBackButton()) {
                contents.set(backSlot / 9, backSlot % 9, ClickableItem.from(
                        simple(Material.ARROW,
                                module.getShopConfig().getRawMessage("gui-back-name", "&6&l← Back to Menu"),
                                module.getShopConfig().getRawMessage("gui-back-lore", "&7Return to main shop menu")),
                        e -> module.getMainMenuGUI().open(player)));
            }

            if (page > 1) {
                String name = module.getShopConfig().getRawMessage("gui-prev-name", "&e&l← Previous Page");
                String lore = module.getShopConfig().getRawMessage("gui-prev-lore", "&7Go to page {page}")
                        .replace("{page}", String.valueOf(page - 1));
                contents.set(prevSlot / 9, prevSlot % 9, ClickableItem.from(
                        simple(Material.ARROW, name, lore),
                        e -> module.getShopGUI().open(player, shop, page - 1)));
            }

            if (page < total) {
                String name = module.getShopConfig().getRawMessage("gui-next-name", "&e&lNext Page →");
                String lore = module.getShopConfig().getRawMessage("gui-next-lore", "&7Go to page {page}")
                        .replace("{page}", String.valueOf(page + 1));
                contents.set(nextSlot / 9, nextSlot % 9, ClickableItem.from(
                        simple(Material.ARROW, name, lore),
                        e -> module.getShopGUI().open(player, shop, page + 1)));
            }

            int pgSlot = (backSlot / 9) * 9 + (backSlot % 9) - 1;
            if (pgSlot < 0) pgSlot = backSlot + 1; // fallback if back is in col 0
            String pgName = module.getShopConfig()
                    .getRawMessage("gui-page-name", "&f&lPage {page} &7of &f{total}")
                    .replace("{page}", String.valueOf(page))
                    .replace("{total}", String.valueOf(total));
            String pgLore = module.getShopConfig()
                    .getRawMessage("gui-page-lore", "&7Use arrows to navigate pages");
            contents.set(pgSlot / 9, pgSlot % 9, ClickableItem.empty(simple(Material.PAPER, pgName, pgLore)));

            boolean amountEnabled = module.getShopConfig().isAmountSelectionEnabled();

            // For rotating shops, resolve which item IDs are active this period.
            // Non-rotating shops: activeRotationIds stays null → no filtering applied.
            Set<String> activeRotationIds = null;
            if (shop.isRotating()) {
                activeRotationIds = module.getRotationManager().getActiveItemIds(shop);
            }

            for (ShopItem shopItem : shop.getItemsForPage(page)) {
                if (shopItem == null) continue;
                // Skip items not in today's rotation
                if (activeRotationIds != null && !activeRotationIds.contains(shopItem.getId())) continue;
                String perm = shopItem.getPermission();
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) continue;

                int slot = shopItem.getSlot();
                if (slot < 0) continue;
                int row = slot / 9, col = slot % 9;
                if (row >= navRow) continue;

                double eBuy  = module.getPriceModifierManager().getEffectiveBuyPrice(player.getUniqueId(), shopItem);
                double eSell = module.getPriceModifierManager().getEffectiveSellPrice(player.getUniqueId(), shopItem);

                ItemStack display = addPriceLore(shopItem.buildItemStack(), shopItem, eBuy, eSell, sym, amountEnabled);

                contents.set(row, col, ClickableItem.from(display, data -> {
                    InventoryClickEvent e = (InventoryClickEvent) data.getEvent();
                    boolean isLeft  = e.isLeftClick();
                    boolean isRight = e.isRightClick();
                    boolean instant = (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP);

                    if (instant) {
                        if (isLeft  && shopItem.canBuy())  module.getTransactionProcessor().processBuy(player,  shopItem, 1);
                        else if (isRight && shopItem.canSell()) module.getTransactionProcessor().processSell(player, shopItem, 1);
                        return;
                    }

                    if (amountEnabled) {
                        AmountSelectionGUI.Mode mode;
                        if (isLeft  && shopItem.canBuy())  mode = AmountSelectionGUI.Mode.BUY;
                        else if (isRight && shopItem.canSell()) mode = AmountSelectionGUI.Mode.SELL;
                        else return;
                        module.getAmountSelectionGUI().open(player, shopItem, mode, shop, page);
                        return;
                    }

                    if (isLeft  && shopItem.canBuy())  module.getTransactionProcessor().processBuy(player,  shopItem, 1);
                    else if (isRight && shopItem.canSell()) module.getTransactionProcessor().processSell(player, shopItem, 1);
                }));
            }
        }

        @Override
        public void update(Player player, InventoryContents contents) {}


        private String formatPrice(double price, String sym) {
            String cid = shop.getCurrencyId();
            if (cid != null) {
                CurrencyService cs = module.getPlugin().getCurrencyService();
                if (cs != null) return cs.formatBalance(cid, price);
            }
            return sym + String.format("%.2f", price);
        }

        private ItemStack addPriceLore(ItemStack item, ShopItem shopItem,
                                       double buyPrice, double sellPrice,
                                       String sym, boolean amountEnabled) {
            if (item == null) item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                    : new ArrayList<>();

            lore.add("");
            lore.add(color("&7Amount: &e" + shopItem.getAmount()));
            lore.add(shopItem.canBuy()
                    ? color("&7Buy:  &a" + formatPrice(buyPrice, sym))
                    : color("&7Buy:  &cNot available"));
            lore.add(shopItem.canSell()
                    ? color("&7Sell: &c" + formatPrice(sellPrice, sym))
                    : color("&7Sell: &cNot available"));

            String trend = module.getDynamicPricingManager().getTrendLore(shopItem);
            if (trend != null) lore.add(color(trend));

            lore.add("");
            if (shopItem.canBuy())
                lore.add(color(module.getShopConfig().getRawMessage("shop-lore-buy", "&aLeft-click &7to buy")));
            if (shopItem.canSell())
                lore.add(color(module.getShopConfig().getRawMessage("shop-lore-sell", "&cRight-click &7to sell")));

            if (amountEnabled && (shopItem.canBuy() || shopItem.canSell())) {
                lore.add(color(module.getShopConfig().getRawMessage("shop-lore-confirm", "&eClick to choose amount")));
                lore.add(color(module.getShopConfig().getRawMessage("shop-lore-instant", "&6Press Q for instant x1")));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }


        private static ItemStack simple(Material mat, String name, String lore) {
            ItemStack item = new ItemStack(mat);
            ItemMeta  meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(name));
                List<String> lines = new ArrayList<>();
                for (String line : lore.split("\n")) lines.add(color(line));
                meta.setLore(lines);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                item.setItemMeta(meta);
            }
            return item;
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