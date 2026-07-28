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

    /**
     * Withdraws {@code amount} from the player's balance.
     *
     * <p>The pre-check against the cached balance is a fast-path optimisation only.
     * The actual deduction in {@link fr.elias.oreoEssentials.db.database.MongoDBManager#takeBalance}
     * is atomic at the database level: it uses a conditional {@code findOneAndUpdate}
     * with a {@code balance >= amount} filter so two concurrent withdrawals can never
     * both succeed when only one has sufficient funds (no TOCTOU race).</p>
     */
    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) return false;

        // Fast-path: reject immediately if the cached balance is clearly insufficient.
        // The authoritative atomicity check is inside MongoDBManager.takeBalance().
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