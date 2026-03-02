package fr.elias.oreoEssentials.modules.shop.gui;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.modules.shop.protection.AntiDupeProtection;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class TransactionProcessor {

    private final ShopModule       module;
    private final AntiDupeProtection antiDupe;

    public TransactionProcessor(ShopModule module) {
        this.module    = module;
        this.antiDupe  = new AntiDupeProtection(module);
    }

    public AntiDupeProtection getAntiDupe() { return antiDupe; }



    public boolean processBuy(Player player, ShopItem shopItem, int quantity) {
        if (!shopItem.canBuy()) return false;
        if (!antiDupe.beginTransaction(player)) return false;

        try {
            int    totalItems = shopItem.getAmount() * quantity;
            double price = module.getPriceModifierManager()
                    .getEffectiveBuyPrice(player.getUniqueId(), shopItem) * quantity;

            if (!module.getEconomy().has(player, price)) {
                send(player, module.getShopConfig().getMessage("buy-not-enough-money")
                        .replace("{price}", module.getEconomy().format(price)));
                return false;
            }

            ItemStack proto = shopItem.buildItemStack();
            if (!antiDupe.verifyHasSpace(player, proto, totalItems)) {
                send(player, module.getShopConfig().getMessage("buy-inventory-full"));
                return false;
            }

            module.getEconomy().withdraw(player, price);

            int remaining = totalItems;
            while (remaining > 0) {
                int stackAmt = Math.min(remaining, proto.getMaxStackSize());
                ItemStack stack = shopItem.buildItemStack();
                stack.setAmount(stackAmt);
                player.getInventory().addItem(stack).forEach((idx, leftover) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                remaining -= stackAmt;
            }

            module.getDynamicPricingManager().recordBuy(shopItem, totalItems);

            module.getTransactionLogger().logTransaction(
                    player.getName(), "BOUGHT", totalItems,
                    shopItem.getMaterial().name(), price,
                    module.getEconomy().getEconomyName());

            send(player, module.getShopConfig().getMessage("buy-success")
                    .replace("{amount}", String.valueOf(totalItems))
                    .replace("{item}",   formatName(shopItem))
                    .replace("{price}",  module.getEconomy().format(price)));
            return true;

        } finally {
            antiDupe.endTransaction(player);
        }
    }


    public boolean processSell(Player player, ShopItem shopItem, int quantity) {
        if (!shopItem.canSell()) {
            send(player, module.getShopConfig().getMessage("sell-no-price"));
            return false;
        }
        if (!antiDupe.beginTransaction(player)) return false;

        try {
            int totalItems = shopItem.getAmount() * quantity;

            if (!antiDupe.verifyHasItem(player, shopItem.buildItemStack(), totalItems)) {
                send(player, module.getShopConfig().getMessage("sell-not-enough"));
                return false;
            }

            double price = module.getPriceModifierManager()
                    .getEffectiveSellPrice(player.getUniqueId(), shopItem) * quantity;

            int toRemove = totalItems;
            for (ItemStack slot : player.getInventory().getContents()) {
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (slot.getType() == shopItem.getMaterial()) {
                    int take = Math.min(toRemove, slot.getAmount());
                    slot.setAmount(slot.getAmount() - take);
                    toRemove -= take;
                    if (toRemove <= 0) break;
                }
            }
            player.updateInventory();

            module.getEconomy().deposit(player, price);

            module.getTransactionLogger().logTransaction(
                    player.getName(), "SOLD", totalItems,
                    shopItem.getMaterial().name(), price,
                    module.getEconomy().getEconomyName());

            send(player, module.getShopConfig().getMessage("sell-success")
                    .replace("{amount}", String.valueOf(totalItems))
                    .replace("{item}",   formatName(shopItem))
                    .replace("{price}",  module.getEconomy().format(price)));
            return true;

        } finally {
            antiDupe.endTransaction(player);
        }
    }


    public double processSellAll(Player player, ItemStack filter) {
        if (!antiDupe.beginTransaction(player)) return -1;

        try {
            double totalEarned = 0;
            int    totalSold   = 0;
            boolean hadSellable = false;

            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (filter != null && slot.getType() != filter.getType()) continue;

                ShopItem shopItem = module.getShopManager().findBestSellItem(slot);
                if (shopItem == null || !shopItem.canSell()) continue;

                hadSellable = true;
                double pricePerStack = module.getPriceModifierManager()
                        .getEffectiveSellPrice(player.getUniqueId(), shopItem);
                double pricePerItem  = pricePerStack / shopItem.getAmount();

                totalEarned += pricePerItem * slot.getAmount();
                totalSold   += slot.getAmount();
                player.getInventory().setItem(i, null);
            }

            player.updateInventory();

            if (!hadSellable) {
                send(player, module.getShopConfig().getMessage("sell-all-nothing"));
                return 0;
            }

            module.getEconomy().deposit(player, totalEarned);

            module.getTransactionLogger().logTransaction(
                    player.getName(), "SOLD_ALL", totalSold, "multiple items",
                    totalEarned, module.getEconomy().getEconomyName());

            send(player, module.getShopConfig().getMessage("sell-all-success")
                    .replace("{amount}", String.valueOf(totalSold))
                    .replace("{price}",  module.getEconomy().format(totalEarned)));

            return totalEarned;

        } finally {
            antiDupe.endTransaction(player);
        }
    }


    private void send(Player player, String msg) {
        player.sendMessage(Lang.color(msg));
    }

    private String formatName(ShopItem item) {
        String dn = item.getDisplayName();
        return (dn != null && !dn.isEmpty())
                ? dn
                : item.getMaterial().name().replace("_", " ").toLowerCase();
    }
}