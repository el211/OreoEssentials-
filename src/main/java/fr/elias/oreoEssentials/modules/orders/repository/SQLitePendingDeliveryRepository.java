package fr.elias.oreoEssentials.modules.orders.repository;

import fr.elias.oreoEssentials.modules.orders.model.PendingDelivery;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * SQLite-backed repository for pending item deliveries.
 * Shares the same database file as SQLiteOrderRepository.
 */
public final class SQLitePendingDeliveryRepository implements PendingDeliveryRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS pending_deliveries (
                id          TEXT    PRIMARY KEY,
                player_uuid TEXT    NOT NULL,
                item_data   TEXT    NOT NULL,
                quantity    INTEGER NOT NULL,
                order_id    TEXT,
                created_at  INTEGER NOT NULL
            );
            """;

    private static final String IDX_PLAYER =
            "CREATE INDEX IF NOT EXISTS idx_pdelivery_player ON pending_deliveries(player_uuid);";

    private final Connection conn;
    private final Logger     log;

    public SQLitePendingDeliveryRepository(File dataFolder, String fileName, Logger log) throws SQLException {
        this.log = log;
        File dbFile = new File(dataFolder, fileName);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found.", e);
        }

        conn = DriverManager.getConnection(url);
        conn.setAutoCommit(true);

        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute(CREATE_TABLE);
            st.execute(IDX_PLAYER);
        }
    }

    @Override
    public CompletableFuture<Void> save(PendingDelivery d) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT OR IGNORE INTO pending_deliveries
                    (id, player_uuid, item_data, quantity, order_id, created_at)
                    VALUES (?,?,?,?,?,?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, d.getId());
                ps.setString(2, d.getPlayerUuid().toString());
                ps.setString(3, d.getItemData());
                ps.setInt(4, d.getQuantity());
                ps.setString(5, d.getOrderId());
                ps.setLong(6, d.getCreatedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.severe("[Orders/PendingDelivery] save() failed: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<List<PendingDelivery>> loadForPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<PendingDelivery> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM pending_deliveries WHERE player_uuid=?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PendingDelivery d = new PendingDelivery();
                        d.setId(rs.getString("id"));
                        d.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                        d.setItemData(rs.getString("item_data"));
                        d.setQuantity(rs.getInt("quantity"));
                        d.setOrderId(rs.getString("order_id"));
                        d.setCreatedAt(rs.getLong("created_at"));
                        list.add(d);
                    }
                }
            } catch (SQLException e) {
                log.severe("[Orders/PendingDelivery] loadForPlayer() failed: " + e.getMessage());
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> delete(String deliveryId) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM pending_deliveries WHERE id=?")) {
                ps.setString(1, deliveryId);
                ps.executeUpdate();
            } catch (SQLException e) {
                log.severe("[Orders/PendingDelivery] delete() failed: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try { if (conn != null && !conn.isClosed()) conn.close(); }
            catch (SQLException e) { log.warning("[Orders/PendingDelivery] close() error: " + e.getMessage()); }
        });
    }
}