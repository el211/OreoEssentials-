package fr.elias.oreoEssentials.modules.shop.protection;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiDupeProtection {

    private static final int MAX_FLAGS = 10;

    private final ShopModule module;

    private final Set<UUID>              locked           = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long>        lastTransaction  = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>>  rateMap          = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>     flags            = new ConcurrentHashMap<>();

    public AntiDupeProtection(ShopModule module) {
        this.module = module;
    }


    public boolean beginTransaction(Player player) {
        UUID uuid = player.getUniqueId();

        if (locked.contains(uuid)) {
            flag(player, "Double-transaction attempt");
            return false;
        }

        if (player.hasPermission("oshopgui.bypass.cooldown")) {
            locked.add(uuid);
            return true;
        }

        if (module.getShopConfig().isAntiDupeEnabled()) {
            long cooldown = module.getShopConfig().getTransactionCooldown();
            Long last = lastTransaction.get(uuid);
            if (last != null && System.currentTimeMillis() - last < cooldown) {
                long remaining = cooldown - (System.currentTimeMillis() - last);
                player.sendMessage(fr.elias.oreoEssentials.util.Lang.color(
                        module.getShopConfig().getMessage("transaction-cooldown")
                                .replace("{time}", String.valueOf(remaining))));
                return false;
            }

            int maxRate = module.getShopConfig().getMaxTransactionsPerSecond();
            List<Long> times = rateMap.computeIfAbsent(uuid, k -> new ArrayList<>());
            long now = System.currentTimeMillis();
            times.removeIf(t -> now - t > 1000);
            if (times.size() >= maxRate) {
                flag(player, "Rate exceeded (" + times.size() + "/s)");
                return false;
            }
            times.add(now);
        }

        locked.add(uuid);
        return true;
    }

    public void endTransaction(Player player) {
        UUID uuid = player.getUniqueId();
        locked.remove(uuid);
        lastTransaction.put(uuid, System.currentTimeMillis());
    }

    public boolean verifyHasItem(Player player, ItemStack item, int amount) {
        if (!module.getShopConfig().isVerifyInventory()) return true;
        int count = 0;
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot == null) continue;
            if (slot.getType() == item.getType() && itemsMatch(slot, item)) count += slot.getAmount();
        }
        return count >= amount;
    }

    public boolean verifyHasSpace(Player player, ItemStack item, int amount) {
        int maxStack = item.getMaxStackSize();
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) free += maxStack;
            else if (itemsMatch(slot, item) && slot.getAmount() < maxStack) free += maxStack - slot.getAmount();
        }
        return free >= amount;
    }

    private boolean itemsMatch(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;
        if (a.hasItemMeta() != b.hasItemMeta()) return !b.hasItemMeta();
        if (!a.hasItemMeta()) return true;
        var ma = a.getItemMeta(); var mb = b.getItemMeta();
        if (ma == null || mb == null) return ma == mb;
        if (ma.hasDisplayName() != mb.hasDisplayName()) return false;
        return !ma.hasDisplayName() || ma.getDisplayName().equals(mb.getDisplayName());
    }

    private void flag(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        int f = flags.merge(uuid, 1, Integer::sum);

        if (module.getShopConfig().isLogSuspicious()) {
            module.getPlugin().getLogger().warning("[Shop/AntiDupe] Suspicious: "
                    + player.getName() + " — " + reason + " (flags=" + f + ")");
        }

        if (f >= MAX_FLAGS) {
            module.getPlugin().getServer().getScheduler().runTask(module.getPlugin(), () -> {
                player.kickPlayer(org.bukkit.ChatColor.RED + "[SHOP] Kicked for suspicious activity.");
                flags.remove(uuid);
            });
        }
    }

    public void cleanupPlayer(UUID uuid) {
        locked.remove(uuid);
        lastTransaction.remove(uuid);
        rateMap.remove(uuid);
        flags.remove(uuid);
    }

    public boolean isLocked(UUID uuid) { return locked.contains(uuid); }
}