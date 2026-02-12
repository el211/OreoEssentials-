package fr.elias.oreoEssentials.modules.economy;

import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MongoEconomyService implements EconomyService {
    private final PlayerEconomyDatabase database;

    public MongoEconomyService(PlayerEconomyDatabase database) {
        this.database = database;
    }

    @Override
    public double getBalance(UUID player) {
        return database.getBalance(player);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) return false;

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player);
            String name = offlinePlayer.getName();
            if (name == null) name = player.toString();

            database.giveBalance(player, name, amount);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) return false;

        double currentBalance = database.getBalance(player);
        if (currentBalance < amount) return false;

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player);
            String name = offlinePlayer.getName();
            if (name == null) name = player.toString();

            database.takeBalance(player, name, amount);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<TopEntry> topBalances(int limit) {
        if (!database.supportsLeaderboard()) {
            return List.of();
        }

        // Convert PlayerEconomyDatabase.TopEntry to EconomyService.TopEntry
        return database.topBalances(limit).stream()
                .map(entry -> new TopEntry(entry.uuid(), entry.name(), entry.balance()))
                .collect(Collectors.toList());
    }
}