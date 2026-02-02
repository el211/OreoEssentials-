package fr.elias.oreoEssentials.db.database;


import fr.elias.oreoEssentials.offline.OfflinePlayerCache;

import java.util.List;
import java.util.UUID;

public interface PlayerEconomyDatabase {

    boolean connect(String url, String user, String password);

    void giveBalance(UUID playerUUID, String name, double amount);
    void takeBalance(UUID playerUUID, String name, double amount);
    void setBalance(UUID playerUUID, String name, double amount);

    double getBalance(UUID playerUUID);
    double getOrCreateBalance(UUID playerUUID, String name);

    void populateCache(OfflinePlayerCache cache);
    void clearCache();
    void close();
    /** Optional: does this backend support leaderboard queries? */
    default boolean supportsLeaderboard() { return false; }

    /** A single row in the leaderboard. */
    record TopEntry(UUID uuid, String name, double balance) {}

    /**
     * Return the top N balances (desc).
     * Only required if supportsLeaderboard() returns true.
     */
    default List<TopEntry> topBalances(int limit) {
        throw new UnsupportedOperationException("Leaderboard not supported by this backend");
    }
}
