package fr.elias.oreoEssentials.migration;

import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Imports player economy balances from zEssentials' SQLite/MySQL database into
 * OreoEssentials using whatever economy backend is currently active.
 *
 * zEssentials table:  <prefix>economies
 * Relevant columns:   unique_id  (UUID text)
 *                     economy_name (VARCHAR 255) — default "money"
 *                     amount     (DECIMAL 65,2)
 *
 * Player names are resolved via a JOIN on <prefix>users.
 */
public class ZEssentialsEconomyImporter {

    private final PlayerEconomyDatabase economy;
    private final Logger logger;
    private final String economiesTable;
    private final String usersTable;
    private final String economyName;
    private final boolean skipExisting;

    /**
     * @param economy       OreoEssentials economy backend (may be null — will be skipped)
     * @param logger        Plugin logger
     * @param tablePrefix   zEssentials table prefix, e.g. "zessentials_"
     * @param economyName   Which economy to import, e.g. "money"
     * @param skipExisting  When true, players who already have a balance > 0 are skipped
     */
    public ZEssentialsEconomyImporter(
            PlayerEconomyDatabase economy,
            Logger logger,
            String tablePrefix,
            String economyName,
            boolean skipExisting
    ) {
        this.economy = economy;
        this.logger = logger;
        this.economiesTable = tablePrefix + "economies";
        this.usersTable     = tablePrefix + "users";
        this.economyName    = economyName;
        this.skipExisting   = skipExisting;
    }

    /**
     * Runs the import. Returns the number of balances successfully imported.
     *
     * @throws SQLException if the query itself fails (caller should catch and report)
     */
    public int importEconomy(Connection connection) throws SQLException {
        if (economy == null) {
            logger.warning("[zEssentials Import] [Economy] No economy backend is configured in OreoEssentials — skipping.");
            return 0;
        }

        String sql =
                "SELECT u.unique_id, u.name, e.amount " +
                "FROM " + economiesTable + " e " +
                "JOIN " + usersTable + " u ON u.unique_id = e.unique_id " +
                "WHERE e.economy_name = ?";

        int imported = 0;
        int skipped  = 0;

        logger.info("[zEssentials Import] [Economy] Querying '" + economiesTable
                + "' for economy_name='" + economyName + "' …");

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, economyName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidStr  = rs.getString("unique_id");
                    String name     = rs.getString("name");
                    double amount   = rs.getDouble("amount");

                    try {
                        UUID uuid = UUID.fromString(uuidStr);

                        if (skipExisting) {
                            double existing = economy.getBalance(uuid);
                            if (existing > 0) {
                                skipped++;
                                continue;
                            }
                        }

                        economy.setBalance(uuid, name != null ? name : uuidStr.substring(0, 8), amount);
                        imported++;

                    } catch (Exception ex) {
                        logger.warning("[zEssentials Import] [Economy] Error for UUID "
                                + uuidStr + ": " + ex.getMessage());
                        skipped++;
                    }
                }
            }
        }

        logger.info("[zEssentials Import] [Economy] Done — imported=" + imported
                + ", skipped=" + skipped);
        return imported;
    }
}
