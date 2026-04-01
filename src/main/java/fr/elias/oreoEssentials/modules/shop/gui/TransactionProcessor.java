package fr.elias.oreoEssentials.modules.shop.gui;

import fr.elias.oreoEssentials.modules.currency.CurrencyService;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import fr.elias.oreoEssentials.modules.shop.protection.AntiDupeProtection;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

public final class TransactionProcessor {

    private final ShopModule module;
    private final AntiDupeProtection antiDupe;

    public TransactionProcessor(ShopModule module) {
        this.module = module;
        this.antiDupe = new AntiDupeProtection(module);
    }

    public AntiDupeProtection getAntiDupe() { return antiDupe; }

    public boolean processBuy(Player player, ShopItem shopItem, int quantity) {
        if (!shopItem.canBuy()) return false;
        if (!antiDupe.beginTransaction(player)) return false;

        int totalItems = shopItem.getAmount() * quantity;
        double price = module.getPriceModifierManager()
                .getEffectiveBuyPrice(player.getUniqueId(), shopItem) * quantity;

        String currencyId = getShopCurrencyId(shopItem);
        CurrencyService cs = (currencyId != null) ? module.getPlugin().getCurrencyService() : null;

        if (cs == null) {
            try {
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
                finishBuy(player, shopItem, totalItems, price, module.getEconomy().getEconomyName(), module.getEconomy().format(price));
                return true;
            } finally {
                antiDupe.endTransaction(player);
            }
        }

        ItemStack proto = shopItem.buildItemStack();
        cs.getBalance(player.getUniqueId(), currencyId).whenComplete((balance, error) -> {
            if (error != null) {
                OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                    send(player, "<red>Shop transaction failed.</red>");
                    antiDupe.endTransaction(player);
                });
                return;
            }

            OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                if (balance < price) {
                    send(player, module.getShopConfig().getMessage("buy-not-enough-money")
                            .replace("{price}", cs.formatBalance(currencyId, price)));
                    antiDupe.endTransaction(player);
                    return;
                }
                if (!antiDupe.verifyHasSpace(player, proto, totalItems)) {
                    send(player, module.getShopConfig().getMessage("buy-inventory-full"));
                    antiDupe.endTransaction(player);
                    return;
                }

                cs.withdraw(player.getUniqueId(), currencyId, price).whenComplete((success, withdrawError) ->
                        OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                            try {
                                if (withdrawError != null || !Boolean.TRUE.equals(success)) {
                                    send(player, module.getShopConfig().getMessage("buy-not-enough-money")
                                            .replace("{price}", cs.formatBalance(currencyId, price)));
                                    return;
                                }
                                finishBuy(player, shopItem, totalItems, price, currencyId, cs.formatBalance(currencyId, price));
                            } finally {
                                antiDupe.endTransaction(player);
                            }
                        }));
            });
        });

        return true;
    }

    public boolean processSell(Player player, ShopItem shopItem, int quantity) {
        if (!shopItem.canSell()) {
            send(player, module.getShopConfig().getMessage("sell-no-price"));
            return false;
        }
        if (!antiDupe.beginTransaction(player)) return false;

        int totalItems = shopItem.getAmount() * quantity;
        if (!antiDupe.verifyHasItem(player, shopItem.buildItemStack(), totalItems)) {
            send(player, module.getShopConfig().getMessage("sell-not-enough"));
            antiDupe.endTransaction(player);
            return false;
        }

        double price = module.getPriceModifierManager()
                .getEffectiveSellPrice(player.getUniqueId(), shopItem) * quantity;

        String currencyId = getShopCurrencyId(shopItem);
        CurrencyService cs = (currencyId != null) ? module.getPlugin().getCurrencyService() : null;

        if (cs == null) {
            try {
                removeItems(player, shopItem, totalItems);
                module.getEconomy().deposit(player, price);
                finishSell(player, shopItem, totalItems, price, module.getEconomy().getEconomyName(), module.getEconomy().format(price));
                return true;
            } finally {
                antiDupe.endTransaction(player);
            }
        }

        cs.deposit(player.getUniqueId(), currencyId, price).whenComplete((success, error) ->
                OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                    try {
                        if (error != null || !Boolean.TRUE.equals(success)) {
                            send(player, "<red>Shop transaction failed.</red>");
                            return;
                        }
                        if (!antiDupe.verifyHasItem(player, shopItem.buildItemStack(), totalItems)) {
                            send(player, module.getShopConfig().getMessage("sell-not-enough"));
                            return;
                        }
                        removeItems(player, shopItem, totalItems);
                        finishSell(player, shopItem, totalItems, price, currencyId, cs.formatBalance(currencyId, price));
                    } finally {
                        antiDupe.endTransaction(player);
                    }
                }));

        return true;
    }

    public double processSellAll(Player player, ItemStack filter) {
        if (!antiDupe.beginTransaction(player)) return -1;

        try {
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
            CompletableFuture<?> chain = CompletableFuture.completedFuture(null);

            for (var entry : earningsByCurrency.entrySet()) {
                String key = entry.getKey();
                double amount = entry.getValue();
                totalEarned += amount;

                if (key.isEmpty()) {
                    module.getEconomy().deposit(player, amount);
                    module.getTransactionLogger().logTransaction(
                            player.getName(), "SOLD_ALL", totalSold, "multiple items",
                            amount, module.getEconomy().getEconomyName());
                    send(player, module.getShopConfig().getMessage("sell-all-success")
                            .replace("{amount}", String.valueOf(totalSold))
                            .replace("{price}", module.getEconomy().format(amount)));
                } else if (cs != null) {
                    final int soldCount = totalSold;
                    chain = chain.thenCompose(ignored ->
                            cs.deposit(player.getUniqueId(), key, amount).thenAccept(success ->
                                    OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                                        if (Boolean.TRUE.equals(success)) {
                                            module.getTransactionLogger().logTransaction(
                                                    player.getName(), "SOLD_ALL", soldCount, "multiple items", amount, key);
                                            send(player, module.getShopConfig().getMessage("sell-all-success")
                                                    .replace("{amount}", String.valueOf(soldCount))
                                                    .replace("{price}", cs.formatBalance(key, amount)));
                                        } else {
                                            send(player, "<red>Shop transaction failed.</red>");
                                        }
                                    })));
                }
            }

            chain.whenComplete((ignored, error) ->
                    OreScheduler.runForEntity(module.getPlugin(), player, () -> antiDupe.endTransaction(player)));
            return totalEarned;

        } catch (Throwable t) {
            antiDupe.endTransaction(player);
            throw t;
        }
    }

    private void finishBuy(Player player, ShopItem shopItem, int totalItems, double price, String ecoName, String formattedPrice) {
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
        module.getTransactionLogger().logTransaction(
                player.getName(), "BOUGHT", totalItems,
                shopItem.getMaterial().name(), price, ecoName);

        send(player, module.getShopConfig().getMessage("buy-success")
                .replace("{amount}", String.valueOf(totalItems))
                .replace("{item}", formatName(shopItem))
                .replace("{price}", formattedPrice));
    }

    private void finishSell(Player player, ShopItem shopItem, int totalItems, double price, String ecoName, String formattedPrice) {
        module.getTransactionLogger().logTransaction(
                player.getName(), "SOLD", totalItems,
                shopItem.getMaterial().name(), price, ecoName);

        send(player, module.getShopConfig().getMessage("sell-success")
                .replace("{amount}", String.valueOf(totalItems))
                .replace("{item}", formatName(shopItem))
                .replace("{price}", formattedPrice));
    }

    private void removeItems(Player player, ShopItem shopItem, int totalItems) {
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
    }

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
