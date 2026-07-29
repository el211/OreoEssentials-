package fr.elias.oreoEssentials.db.database;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * PostgreSQL economy backend.
 *
 * E-3: Thread-safety — each public method opens its own connection from
 * DriverManager (try-with-resources), so no shared Connection is ever used
 * concurrently.  This is safe for the async-call-site pattern used here.
 *
 * E-1/E-2: giveBalance and takeBalance now use single atomic SQL statements
 * (UPDATE … LEAST/GREATEST) so there is no read-modify-write race window.
 */
public class PostgreSQLManager implements PlayerEconomyDatabase {

    // E-3: store credentials instead of a single shared Connection
    private String jdbcUrl;
    private String dbUser;
    private String dbPassword;

    private final OreoEssentials plugin;
    private final RedisManager redis;

    private static final String TABLE = "economy";
    private static final double STARTING_BALANCE = 100.0;
    private static final double MAX_BALANCE = 1_000_000_000.0;

    public PostgreSQLManager(OreoEssentials plugin, RedisManager redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    /** Opens a fresh connection from the driver.  Always use in try-with-resources. */
    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    @Override
    public boolean connect(String url, String user, String password) {
        try {
            Class.forName("org.postgresql.Driver");
            // Validate the credentials once, then store them for per-method use (E-3)
            try (Connection test = DriverManager.getConnection(url, user, password)) {
                String createTableQuery = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                        "player_uuid UUID PRIMARY KEY, " +
                        "name TEXT, " +
                        "balance DOUBLE PRECISION NOT NULL DEFAULT " + STARTING_BALANCE + ")";
                try (Statement statement = test.createStatement()) {
                    statement.executeUpdate(createTableQuery);
                }
            }
            this.jdbcUrl   = url;
            this.dbUser    = user;
            this.dbPassword = password;
            plugin.getLogger().info(" Connected to PostgreSQL database!");
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
        // No persistent connection to close (E-3 — per-method connections)
        plugin.getLogger().info(" PostgreSQL connections are per-operation; nothing to close.");
    }

    @Override
    public double getBalance(UUID playerUUID) {
        Double cachedBalance = redis.getBalance(playerUUID);
        if (cachedBalance != null) return cachedBalance;

        String query = "SELECT balance FROM " + TABLE + " WHERE player_uuid = ?";
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
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
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
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
        // E-1/E-2: Single atomic UPDATE — no read-modify-write race; enforces MAX_BALANCE cap.
        String sql = "INSERT INTO " + TABLE + " (player_uuid, name, balance) VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET name = EXCLUDED.name, " +
                "balance = LEAST(" + TABLE + ".balance + ?, ?)";
        OreScheduler.runAsync(plugin, () -> {
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                double startBal = Math.min(STARTING_BALANCE + amount, MAX_BALANCE);
                ps.setObject(1, playerUUID);
                ps.setString(2, name);
                ps.setDouble(3, startBal);
                ps.setDouble(4, amount);
                ps.setDouble(5, MAX_BALANCE);
                ps.executeUpdate();
                redis.deleteBalance(playerUUID); // invalidate stale cache entry
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error giving balance in PostgreSQL", e);
            }
        });
    }

    @Override
    public void takeBalance(UUID playerUUID, String name, double amount) {
        // E-1: Single atomic UPDATE — floors at 0; only succeeds when balance >= amount.
        String sql = "UPDATE " + TABLE + " SET balance = GREATEST(balance - ?, 0) " +
                "WHERE player_uuid = ? AND balance >= ?";
        OreScheduler.runAsync(plugin, () -> {
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setObject(2, playerUUID);
                ps.setDouble(3, amount);
                int rows = ps.executeUpdate();
                if (rows > 0) redis.deleteBalance(playerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error taking balance in PostgreSQL", e);
            }
        });
    }

    @Override
    public void setBalance(UUID playerUUID, String name, double amount) {
        double clamped = Math.max(0, Math.min(amount, MAX_BALANCE));
        String query = "INSERT INTO " + TABLE + " (player_uuid, name, balance) VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET name = EXCLUDED.name, balance = EXCLUDED.balance";
        OreScheduler.runAsync(plugin, () -> {
            try (Connection conn = openConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setObject(1, playerUUID);
                stmt.setString(2, name);
                stmt.setDouble(3, clamped);
                stmt.executeUpdate();
                redis.setBalance(playerUUID, clamped);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error updating balance in PostgreSQL", e);
            }
        });
    }

    @Override
    public void populateCache(OfflinePlayerCache cache) {
        String query = "SELECT player_uuid, name FROM " + TABLE;
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
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
        OreScheduler.runAsync(plugin, () -> {
            try (Connection conn = openConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
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
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
