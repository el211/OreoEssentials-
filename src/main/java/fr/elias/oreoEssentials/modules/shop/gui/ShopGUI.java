package fr.elias.oreoEssentials.modules.shop.gui;

import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopGuiLayout;
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

            ShopGuiLayout layout = shop.getGuiLayout();

            // ── Filler row ───────────────────────────────────────────────────
            ShopGuiLayout.Button fillerBtn = layout.getFiller();
            contents.fillRow(navRow, ClickableItem.empty(
                    navItem(fillerBtn.getMaterial(), fillerBtn.getName(),
                            fillerBtn.getLore(), fillerBtn.getModelData())));

            // ── Back button ──────────────────────────────────────────────────
            ShopGuiLayout.Button backBtn = layout.getBack();
            if (backBtn.isEnabled()) {
                int slot = backBtn.getSlot();
                contents.set(slot / 9, slot % 9, ClickableItem.from(
                        navItem(backBtn.getMaterial(), backBtn.getName(),
                                backBtn.getLore(), backBtn.getModelData()),
                        e -> module.getMainMenuGUI().open(player)));
            }

            // ── Previous page ────────────────────────────────────────────────
            ShopGuiLayout.Button prevBtn = layout.getPrevPage();
            if (prevBtn.isEnabled() && page > 1) {
                int slot = prevBtn.getSlot();
                String name = prevBtn.getName();
                String lore = String.join("\n", prevBtn.getLore())
                        .replace("{page}", String.valueOf(page - 1));
                contents.set(slot / 9, slot % 9, ClickableItem.from(
                        navItem(prevBtn.getMaterial(), name, List.of(lore.split("\n")),
                                prevBtn.getModelData()),
                        e -> module.getShopGUI().open(player, shop, page - 1)));
            }

            // ── Next page ────────────────────────────────────────────────────
            ShopGuiLayout.Button nextBtn = layout.getNextPage();
            if (nextBtn.isEnabled() && page < total) {
                int slot = nextBtn.getSlot();
                String name = nextBtn.getName();
                String lore = String.join("\n", nextBtn.getLore())
                        .replace("{page}", String.valueOf(page + 1));
                contents.set(slot / 9, slot % 9, ClickableItem.from(
                        navItem(nextBtn.getMaterial(), name, List.of(lore.split("\n")),
                                nextBtn.getModelData()),
                        e -> module.getShopGUI().open(player, shop, page + 1)));
            }

            // ── Page indicator ───────────────────────────────────────────────
            ShopGuiLayout.Button pgBtn = layout.getPageIndicator();
            if (pgBtn.isEnabled()) {
                int slot = pgBtn.getSlot();
                String name = pgBtn.getName()
                        .replace("{page}", String.valueOf(page))
                        .replace("{total}", String.valueOf(total));
                List<String> lore = pgBtn.getLore().stream()
                        .map(l -> l.replace("{page}", String.valueOf(page))
                                   .replace("{total}", String.valueOf(total)))
                        .toList();
                contents.set(slot / 9, slot % 9, ClickableItem.empty(
                        navItem(pgBtn.getMaterial(), name, lore, pgBtn.getModelData())));
            }

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


        private static ItemStack navItem(Material mat, String name,
                                          List<String> lore, int modelData) {
            ItemStack item = new ItemStack(mat);
            ItemMeta  meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(name));
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(color(line));
                meta.setLore(colored);
                if (modelData > 0) meta.setCustomModelData(modelData);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                item.setItemMeta(meta);
            }
            return item;
        }

    }
}