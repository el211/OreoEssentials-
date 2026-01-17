package fr.elias.oreoEssentials.economy;

import net.milkbowl.vault.economy.Economy;

import java.util.UUID;

public class VaultEconomyService implements EconomyService {
    private final Economy eco;

    public VaultEconomyService(Economy eco) {
        this.eco = eco;
    }

    @Override public double getBalance(UUID player) {
        return eco.getBalance(BukkitPlayerAdapter.nameOf(player));
    }

    @Override public boolean deposit(UUID player, double amount) {
        if (amount <= 0) return false;
        return eco.depositPlayer(BukkitPlayerAdapter.nameOf(player), amount).transactionSuccess();
    }

    @Override public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) return false;
        return eco.withdrawPlayer(BukkitPlayerAdapter.nameOf(player), amount).transactionSuccess();
    }

    private static final class BukkitPlayerAdapter {
        static String nameOf(UUID uuid) {
            var off = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            var name = off.getName();
            return (name != null) ? name : uuid.toString();
        }
    }
}
