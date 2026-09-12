package fr.elias.oreoEssentials.migration.cmi;

import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Imports player economy balances from a running CMI instance into
 * OreoEssentials using whatever economy backend is currently active.
 *
 * For each CMI user it reads the default-world-group balance via
 * {@code CMIUser#getBalance()} (which returns a {@code Double}).
 * Players with a zero or null balance are skipped unless
 * {@code skipExisting} is {@code false}.
 */
public class CMIEconomyImporter {

    private final PlayerEconomyDatabase economy;
    private final Logger                logger;
    private final boolean               skipExisting;

    public CMIEconomyImporter(
            PlayerEconomyDatabase economy,
            Logger                logger,
            boolean               skipExisting
    ) {
        this.economy      = economy;
        this.logger       = logger;
        this.skipExisting = skipExisting;
    }

    /**
     * Runs the import and returns the number of balances successfully written.
     *
     * @param cmiPlugin the CMI JavaPlugin instance (obtained from PluginManager)
     */
    public int importEconomy(Plugin cmiPlugin) {
        if (economy == null) {
            logger.warning("[CMI Import] [Economy] No economy backend configured in OreoEssentials — skipping.");
            return 0;
        }

        int imported = 0;
        int skipped  = 0;
        int failed   = 0;

        logger.info("[CMI Import] [Economy] Starting economy migration…");

        try {
            Object cmiInstance   = CMIReflection.getInstance(cmiPlugin);
            Object playerManager = CMIReflection.invoke(cmiInstance, "getPlayerManager");

            @SuppressWarnings("unchecked")
            Map<UUID, Object> allUsers =
                    (Map<UUID, Object>) CMIReflection.invoke(playerManager, "getAllUsers");

            if (allUsers == null || allUsers.isEmpty()) {
                logger.warning("[CMI Import] [Economy] No users found in CMI.");
                return 0;
            }

            for (Map.Entry<UUID, Object> entry : allUsers.entrySet()) {
                UUID   uuid    = entry.getKey();
                Object cmiUser = entry.getValue();

                if (uuid == null || cmiUser == null) continue;

                // Skip fake / system accounts
                Boolean fake = (Boolean) CMIReflection.invokeQuiet(cmiUser, "isFakeUser");
                if (Boolean.TRUE.equals(fake)) continue;

                String playerName = (String) CMIReflection.invoke(cmiUser, "getName");

                // CMIUser#getBalance() → Double (default world group)
                Object rawBalance = CMIReflection.invoke(cmiUser, "getBalance");

                if (rawBalance == null) {
                    // Economy may not be enabled for this user
                    continue;
                }

                double balance = ((Number) rawBalance).doubleValue();

                // Skip zero balances when skipExisting is not wanted
                if (balance <= 0) {
                    skipped++;
                    continue;
                }

                if (skipExisting) {
                    try {
                        double existing = economy.getBalance(uuid);
                        if (existing > 0) {
                            skipped++;
                            continue;
                        }
                    } catch (Exception ignored) {}
                }

                try {
                    String name = playerName != null ? playerName : uuid.toString().substring(0, 8);
                    economy.setBalance(uuid, name, balance);
                    imported++;
                } catch (Exception e) {
                    logger.warning("[CMI Import] [Economy] Failed to set balance for "
                            + (playerName != null ? playerName : uuid) + ": " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            logger.severe("[CMI Import] [Economy] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }

        logger.info("[CMI Import] [Economy] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return imported;
    }
}
