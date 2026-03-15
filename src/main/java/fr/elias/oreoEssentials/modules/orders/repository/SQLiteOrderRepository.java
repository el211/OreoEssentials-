package fr.elias.oreoEssentials.modules.orders.repository;

import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.model.OrderStatus;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * SQLite-backed repository for single-server (no MongoDB) deployments.
 * Uses JDBC + serialized transactions for atomic fill/cancel.
 * Cross-server features are NOT available in this mode.
 */
public final class SQLiteOrderRepository implements OrderRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS orders (
                id               TEXT    PRIMARY KEY,
                requester_uuid   TEXT    NOT NULL,
                requester_name   TEXT    NOT NULL,
                item_data        TEXT    NOT NULL,
                display_name     TEXT,
                total_qty        INTEGER NOT NULL,
                remaining_qty    INTEGER NOT NULL,
                currency_id      TEXT,
                unit_price       REAL    NOT NULL,
                escrow_total     REAL    NOT NULL,
                escrow_remaining REAL    NOT NULL,
                status           TEXT    NOT NULL DEFAULT 'ACTIVE',
                created_at       INTEGER NOT NULL,
                updated_at       INTEGER NOT NULL,
                revision         INTEGER NOT NULL DEFAULT 0,
                items_adder_id   TEXT,
                nexo_id          TEXT,
                oraxen_id        TEXT
            );
            """;

    private static final String IDX_STATUS     = "CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);";
    private static final String IDX_REQUESTER  = "CREATE INDEX IF NOT EXISTS idx_orders_req ON orders(requester_uuid, status);";

    private final Connection conn;
    private final Logger     log;

    public SQLiteOrderRepository(File dataFolder, String fileName, Logger log) throws SQLException {
        this.log = log;
        File dbFile = new File(dataFolder, fileName);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found. Add sqlite-jdbc to your plugin jar.", e);
        }

        conn = DriverManager.getConnection(url);
        conn.setAutoCommit(true);

        // WAL mode for better concurrency
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute(CREATE_TABLE);
            st.execute(IDX_STATUS);
            st.execute(IDX_REQUESTER);
        }
    }

    @Override
    public CompletableFuture<Void> save(Order o) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT OR REPLACE INTO orders
                    (id, requester_uuid, requester_name, item_data, display_name,
                     total_qty, remaining_qty, currency_id, unit_price,
                     escrow_total, escrow_remaining, status, created_at, updated_at, revision,
                     items_adder_id, nexo_id, oraxen_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """;
            synchronized (this) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, o.getId());
                    ps.setString(2, o.getRequesterUuid().toString());
                    ps.setString(3, o.getRequesterName());
                    ps.setString(4, o.getItemData());
                    ps.setString(5, o.getDisplayItemName());
                    ps.setInt(6, o.getTotalQty());
                    ps.setInt(7, o.getRemainingQty());
                    ps.setString(8, o.getCurrencyId());
                    ps.setDouble(9, o.getUnitPrice());
                    ps.setDouble(10, o.getEscrowTotal());
                    ps.setDouble(11, o.getEscrowRemaining());
                    ps.setString(12, o.getStatus().name());
                    ps.setLong(13, o.getCreatedAt());
                    ps.setLong(14, o.getUpdatedAt());
                    ps.setInt(15, o.getRevision());
                    ps.setString(16, o.getItemsAdderId());
                    ps.setString(17, o.getNexoId());
                    ps.setString(18, o.getOraxenId());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.severe("[Orders/SQLite] save() failed: " + e.getMessage());
                    throw new RuntimeException("Failed to save order " + o.getId(), e);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                List<Order> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(fromRs(rs));
                } catch (SQLException e) {
                    log.severe("[Orders/SQLite] loadAll() failed: " + e.getMessage());
                }
                return list;
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> loadActive() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                List<Order> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE status='ACTIVE'");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(fromRs(rs));
                } catch (SQLException e) {
                    log.severe("[Orders/SQLite] loadActive() failed: " + e.getMessage());
                }
                return list;
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> loadByRequester(UUID requesterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                List<Order> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM orders WHERE requester_uuid=?")) {
                    ps.setString(1, requesterUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) list.add(fromRs(rs));
                    }
                } catch (SQLException e) {
                    log.severe("[Orders/SQLite] loadByRequester() failed: " + e.getMessage());
                }
                return list;
            }
        });
    }

    @Override
    public CompletableFuture<FillResult> atomicFill(String orderId, int fillQty, double unitPrice) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) { // serialize concurrent local fills
                try {
                    conn.setAutoCommit(false);
                    try {
                        // Check current state
                        Order current;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT * FROM orders WHERE id=?")) {
                            ps.setString(1, orderId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (!rs.next()) { conn.rollback(); return FillResult.notFound(); }
                                current = fromRs(rs);
                            }
                        }
                        if (current.getStatus() != OrderStatus.ACTIVE) {
                            conn.rollback();
                            return FillResult.alreadyClosed();
                        }
                        if (current.getRemainingQty() < fillQty) {
                            conn.rollback();
                            return FillResult.insufficientQty();
                        }

                        long now     = System.currentTimeMillis();
                        double pay   = unitPrice * fillQty;
                        int newQty   = current.getRemainingQty() - fillQty;
                        double newEscrow = current.getEscrowRemaining() - pay;
                        String newStatus = (newQty <= 0) ? "COMPLETED" : "ACTIVE";
                        int newRevision  = current.getRevision() + 1;

                        try (PreparedStatement ps = conn.prepareStatement("""
                                UPDATE orders SET remaining_qty=?, escrow_remaining=?,
                                status=?, revision=?, updated_at=?
                                WHERE id=? AND status='ACTIVE' AND remaining_qty>=?
                                """)) {
                            ps.setInt(1, Math.max(0, newQty));
                            ps.setDouble(2, Math.max(0, newEscrow));
                            ps.setString(3, newStatus);
                            ps.setInt(4, newRevision);
                            ps.setLong(5, now);
                            ps.setString(6, orderId);
                            ps.setInt(7, fillQty);
                            int rows = ps.executeUpdate();
                            if (rows == 0) { conn.rollback(); return FillResult.insufficientQty(); }
                        }

                        conn.commit();

                        // Build updated order snapshot
                        current.setRemainingQty(Math.max(0, newQty));
                        current.setEscrowRemaining(Math.max(0, newEscrow));
                        current.setStatus(OrderStatus.valueOf(newStatus));
                        current.setRevision(newRevision);
                        current.setUpdatedAt(now);
                        return FillResult.success(fillQty, pay, current);

                    } catch (Exception e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } catch (Exception e) {
                    log.severe("[Orders/SQLite] atomicFill() failed: " + e.getMessage());
                    return FillResult.error();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> atomicCancel(String orderId, UUID requesterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE orders SET status='CANCELLED', revision=revision+1, updated_at=?
                        WHERE id=? AND requester_uuid=? AND status='ACTIVE'
                        """)) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, orderId);
                    ps.setString(3, requesterUuid.toString());
                    return ps.executeUpdate() > 0;
                } catch (SQLException e) {
                    log.severe("[Orders/SQLite] atomicCancel() failed: " + e.getMessage());
                    return false;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try { if (conn != null && !conn.isClosed()) conn.close(); }
            catch (SQLException e) { log.warning("[Orders/SQLite] close() error: " + e.getMessage()); }
        });
    }


    private Order fromRs(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getString("id"));
        o.setRequesterUuid(UUID.fromString(rs.getString("requester_uuid")));
        o.setRequesterName(rs.getString("requester_name"));
        o.setItemData(rs.getString("item_data"));
        o.setDisplayItemName(rs.getString("display_name"));
        o.setTotalQty(rs.getInt("total_qty"));
        o.setRemainingQty(rs.getInt("remaining_qty"));
        o.setCurrencyId(rs.getString("currency_id"));
        o.setUnitPrice(rs.getDouble("unit_price"));
        o.setEscrowTotal(rs.getDouble("escrow_total"));
        o.setEscrowRemaining(rs.getDouble("escrow_remaining"));
        o.setStatus(OrderStatus.valueOf(rs.getString("status")));
        o.setCreatedAt(rs.getLong("created_at"));
        o.setUpdatedAt(rs.getLong("updated_at"));
        o.setRevision(rs.getInt("revision"));
        o.setItemsAdderId(rs.getString("items_adder_id"));
        o.setNexoId(rs.getString("nexo_id"));
        o.setOraxenId(rs.getString("oraxen_id"));
        return o;
    }
}
