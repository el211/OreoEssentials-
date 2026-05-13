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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            // First pass: collect what to sell WITHOUT removing items yet.
            // This prevents items being lost if a custom-currency deposit fails.
            List<Integer>   slotIndices   = new ArrayList<>();
            List<ShopItem>  shopItems     = new ArrayList<>();
            List<Integer>   amounts       = new ArrayList<>();
            List<String>    currencyKeys  = new ArrayList<>();
            List<Double>    earnings      = new ArrayList<>();

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

                slotIndices.add(i);
                shopItems.add(shopItem);
                amounts.add(slot.getAmount());
                currencyKeys.add(cid != null ? cid : "");
                earnings.add(earned);
            }

            if (slotIndices.isEmpty()) {
                send(player, module.getShopConfig().getMessage("sell-all-nothing"));
                antiDupe.endTransaction(player);
                return 0;
            }

            // Aggregate totals per currency key (preserving insertion order)
            LinkedHashMap<String, Double>       earningsByCurrency = new LinkedHashMap<>();
            LinkedHashMap<String, Integer>      countByCurrency    = new LinkedHashMap<>();
            LinkedHashMap<String, List<Integer>> slotsByCurrency   = new LinkedHashMap<>();

            for (int i = 0; i < slotIndices.size(); i++) {
                String key = currencyKeys.get(i);
                earningsByCurrency.merge(key, earnings.get(i), Double::sum);
                countByCurrency.merge(key, amounts.get(i), Integer::sum);
                slotsByCurrency.computeIfAbsent(key, k -> new ArrayList<>()).add(slotIndices.get(i));
            }

            double totalEarned = 0;
            CurrencyService cs = module.getPlugin().getCurrencyService();
            CompletableFuture<?> chain = CompletableFuture.completedFuture(null);

            for (var entry : earningsByCurrency.entrySet()) {
                String key    = entry.getKey();
                double amount = entry.getValue();
                int    sold   = countByCurrency.getOrDefault(key, 0);
                List<Integer> slots = slotsByCurrency.getOrDefault(key, List.of());
                totalEarned += amount;

                if (key.isEmpty()) {
                    // Vault (synchronous): remove items then deposit — Vault deposit never fails
                    for (int s : slots) player.getInventory().setItem(s, null);
                    player.updateInventory();
                    module.getEconomy().deposit(player, amount);
                    module.getTransactionLogger().logTransaction(
                            player.getName(), "SOLD_ALL", sold, "multiple items",
                            amount, module.getEconomy().getEconomyName());
                    send(player, module.getShopConfig().getMessage("sell-all-success")
                            .replace("{amount}", String.valueOf(sold))
                            .replace("{price}", module.getEconomy().format(amount)));
                } else if (cs != null) {
                    final int soldCount = sold;
                    final List<Integer> finalSlots = slots;
                    chain = chain.thenCompose(ignored ->
                            cs.deposit(player.getUniqueId(), key, amount).thenAccept(success ->
                                    OreScheduler.runForEntity(module.getPlugin(), player, () -> {
                                        if (Boolean.TRUE.equals(success)) {
                                            // Only remove items AFTER deposit succeeds
                                            for (int s : finalSlots) player.getInventory().setItem(s, null);
                                            player.updateInventory();
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
