package fr.elias.oreoEssentials.db.database;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PostgreSQLManager implements PlayerEconomyDatabase {

    private Connection connection;
    private final OreoEssentials plugin;
    private final RedisManager redis;

    private static final String TABLE = "economy";
    private static final double STARTING_BALANCE = 100.0;

    public PostgreSQLManager(OreoEssentials plugin, RedisManager redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    @Override
    public boolean connect(String url, String user, String password) {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(url, user, password);
            plugin.getLogger().info(" Connected to PostgreSQL database!");

            String createTableQuery = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "player_uuid UUID PRIMARY KEY, " +
                    "name TEXT, " +
                    "balance DOUBLE PRECISION NOT NULL DEFAULT " + STARTING_BALANCE + ")";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableQuery);
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Failed to connect to PostgreSQL!");
            e.printStackTrace();
            return false;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info(" PostgreSQL connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Error closing PostgreSQL connection!");
            e.printStackTrace();
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        Double cachedBalance = redis.getBalance(playerUUID);
        if (cachedBalance != null) return cachedBalance;

        String query = "SELECT balance FROM " + TABLE + " WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, playerUUID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double balance = rs.getDouble("balance");
                    redis.setBalance(playerUUID, balance);
                    return balance;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Error fetching balance from PostgreSQL", e);
        }
        return STARTING_BALANCE;
    }

    @Override
    public double getOrCreateBalance(UUID playerUUID, String name) {
        Double cachedBalance = redis.getBalance(playerUUID);
        if (cachedBalance != null) return cachedBalance;

        String query = "SELECT balance FROM " + TABLE + " WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, playerUUID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double balance = rs.getDouble("balance");
                    redis.setBalance(playerUUID, balance);
                    return balance;
                }
            }
            setBalance(playerUUID, name, STARTING_BALANCE);
            return STARTING_BALANCE;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Error fetching balance from PostgreSQL", e);
        }
        return STARTING_BALANCE;
    }

    @Override
    public void giveBalance(UUID playerUUID, String name, double amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double newBalance = getBalance(playerUUID) + amount;
            setBalance(playerUUID, name, newBalance);
            redis.giveBalance(playerUUID, amount);
        });
    }

    @Override
    public void takeBalance(UUID playerUUID, String name, double amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double newBalance = Math.max(0, getBalance(playerUUID) - amount);
            setBalance(playerUUID, name, newBalance);
            redis.takeBalance(playerUUID, amount);
        });
    }

    @Override
    public void setBalance(UUID playerUUID, String name, double amount) {
        String query = "INSERT INTO " + TABLE + " (player_uuid, name, balance) VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET name = EXCLUDED.name, balance = EXCLUDED.balance";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setObject(1, playerUUID);
                stmt.setString(2, name);
                stmt.setDouble(3, amount);
                stmt.executeUpdate();
                redis.setBalance(playerUUID, amount);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error updating balance in PostgreSQL", e);
            }
        });
    }

    @Override
    public void populateCache(OfflinePlayerCache cache) {
        String query = "SELECT player_uuid, name FROM " + TABLE;
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                UUID playerUUID = (UUID) rs.getObject("player_uuid");
                String name = rs.getString("name");
                if (name == null) name = Bukkit.getOfflinePlayer(playerUUID).getName();
                if (name != null) cache.add(name, playerUUID);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Error populating cache in PostgreSQL", e);
        }
    }

    public void deleteBalance(UUID playerUUID) {
        String query = "DELETE FROM " + TABLE + " WHERE player_uuid = ?";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setObject(1, playerUUID);
                stmt.executeUpdate();
                redis.deleteBalance(playerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error deleting balance from PostgreSQL", e);
            }
        });
    }


    @Override
    public boolean supportsLeaderboard() { return true; }

    @Override
    public List<TopEntry> topBalances(int limit) {
        List<TopEntry> out = new ArrayList<>();
        String sql = "SELECT player_uuid, name, balance FROM " + TABLE + " ORDER BY balance DESC LIMIT ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = (UUID) rs.getObject("player_uuid");
                    double bal = rs.getDouble("balance");
                    String name = rs.getString("name");
                    if (name == null || name.isBlank()) {
                        String lookedUp = Bukkit.getOfflinePlayer(uuid).getName();
                        name = (lookedUp != null) ? lookedUp : uuid.toString();
                    }
                    out.add(new TopEntry(uuid, name, bal));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ECON] topBalances failed", e);
        }
        return out;
    }

    @Override
    public void clearCache() {
        redis.clearCache();
    }
}
