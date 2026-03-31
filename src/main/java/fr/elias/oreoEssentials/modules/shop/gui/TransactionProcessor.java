package fr.elias.oreoEssentials.modules.shop.gui;

import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.modules.shop.protection.AntiDupeProtection;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;

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

            String currencyId = getShopCurrencyId(shopItem);
            CurrencyService cs = (currencyId != null) ? module.getPlugin().getCurrencyService() : null;

            if (cs != null) {
                double balance = cs.getBalance(player.getUniqueId(), currencyId).join();
                if (balance < price) {
                    send(player, module.getShopConfig().getMessage("buy-not-enough-money")
                            .replace("{price}", cs.formatBalance(currencyId, price)));
                    return false;
                }
                ItemStack proto = shopItem.buildItemStack();
                if (!antiDupe.verifyHasSpace(player, proto, totalItems)) {
                    send(player, module.getShopConfig().getMessage("buy-inventory-full"));
                    return false;
                }
                cs.withdraw(player.getUniqueId(), currencyId, price).join();
            } else {
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
            }

            int remaining = totalItems;
            while (remaining > 0) {
                int stackAmt = Math.min(remaining, shopItem.buildItemStack().getMaxStackSize());
                ItemStack stack = shopItem.buildItemStack();
                stack.setAmount(stackAmt);
                player.getInventory().addItem(stack).forEach((idx, leftover) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                remaining -= stackAmt;
            }

            module.getDynamicPricingManager().recordBuy(shopItem, totalItems);

            String ecoName       = (cs != null) ? currencyId : module.getEconomy().getEconomyName();
            String formattedPrice = (cs != null) ? cs.formatBalance(currencyId, price) : module.getEconomy().format(price);

            module.getTransactionLogger().logTransaction(
                    player.getName(), "BOUGHT", totalItems,
                    shopItem.getMaterial().name(), price, ecoName);

            send(player, module.getShopConfig().getMessage("buy-success")
                    .replace("{amount}", String.valueOf(totalItems))
                    .replace("{item}",   formatName(shopItem))
                    .replace("{price}",  formattedPrice));
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

            String currencyId = getShopCurrencyId(shopItem);
            CurrencyService cs = (currencyId != null) ? module.getPlugin().getCurrencyService() : null;

            String ecoName;
            String formattedPrice;
            if (cs != null) {
                cs.deposit(player.getUniqueId(), currencyId, price).join();
                ecoName       = currencyId;
                formattedPrice = cs.formatBalance(currencyId, price);
            } else {
                module.getEconomy().deposit(player, price);
                ecoName       = module.getEconomy().getEconomyName();
                formattedPrice = module.getEconomy().format(price);
            }

            module.getTransactionLogger().logTransaction(
                    player.getName(), "SOLD", totalItems,
                    shopItem.getMaterial().name(), price, ecoName);

            send(player, module.getShopConfig().getMessage("sell-success")
                    .replace("{amount}", String.valueOf(totalItems))
                    .replace("{item}",   formatName(shopItem))
                    .replace("{price}",  formattedPrice));
            return true;

        } finally {
            antiDupe.endTransaction(player);
        }
    }


    public double processSellAll(Player player, ItemStack filter) {
        if (!antiDupe.beginTransaction(player)) return -1;

        try {
            // Map from currencyId (null = vault sentinel "") to total earned
            LinkedHashMap<String, Double> earningsByCurrency = new LinkedHashMap<>();
            int totalSold = 0;

            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (filter != null && slot.getType() != filter.getType()) continue;

                ShopItem shopItem = module.getShopManager().findBestSellItem(slot);
                if (shopItem == null || !shopItem.canSell()) continue;

                double pricePerStack = module.getPriceModifierManager()
                        .getEffectiveSellPrice(player.getUniqueId(), shopItem);
                double earned = (pricePerStack / shopItem.getAmount()) * slot.getAmount();

                String cid = getShopCurrencyId(shopItem);
                String key = (cid != null) ? cid : "";
                earningsByCurrency.merge(key, earned, Double::sum);
                totalSold += slot.getAmount();
                player.getInventory().setItem(i, null);
            }

            player.updateInventory();

            if (earningsByCurrency.isEmpty()) {
                send(player, module.getShopConfig().getMessage("sell-all-nothing"));
                return 0;
            }

            double totalEarned = 0;
            CurrencyService cs = module.getPlugin().getCurrencyService();

            for (var entry : earningsByCurrency.entrySet()) {
                String key    = entry.getKey();
                double amount = entry.getValue();
                totalEarned  += amount;

                if (key.isEmpty()) {
                    // Vault
                    module.getEconomy().deposit(player, amount);
                    module.getTransactionLogger().logTransaction(
                            player.getName(), "SOLD_ALL", totalSold, "multiple items",
                            amount, module.getEconomy().getEconomyName());
                    send(player, module.getShopConfig().getMessage("sell-all-success")
                            .replace("{amount}", String.valueOf(totalSold))
                            .replace("{price}",  module.getEconomy().format(amount)));
                } else if (cs != null) {
                    // Custom currency
                    cs.deposit(player.getUniqueId(), key, amount).join();
                    module.getTransactionLogger().logTransaction(
                            player.getName(), "SOLD_ALL", totalSold, "multiple items", amount, key);
                    send(player, module.getShopConfig().getMessage("sell-all-success")
                            .replace("{amount}", String.valueOf(totalSold))
                            .replace("{price}",  cs.formatBalance(key, amount)));
                }
            }

            return totalEarned;

        } finally {
            antiDupe.endTransaction(player);
        }
    }


    /** Returns the custom currencyId for the shop owning this item, or null for Vault. */
    private String getShopCurrencyId(ShopItem shopItem) {
        Shop shop = module.getShopManager().getShop(shopItem.getShopId());
        return shop != null ? shop.getCurrencyId() : null;
    }

    private void send(Player player, String msg) {
        Lang.sendRaw(player, msg);
    }

    private String formatName(ShopItem item) {
        String dn = item.getDisplayName();
        return (dn != null && !dn.isEmpty())
                ? dn
                : item.getMaterial().name().replace("_", " ").toLowerCase();
    }
}
