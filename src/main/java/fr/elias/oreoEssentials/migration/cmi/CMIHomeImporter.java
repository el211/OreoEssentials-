package fr.elias.oreoEssentials.migration.cmi;

import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Imports homes from a running CMI instance into OreoEssentials.
 *
 * Uses the live CMI API (reflection-based) so no JDBC / database access
 * is required. CMI must be loaded on the same server.
 *
 * For each player known to CMI it reads the LinkedHashMap&lt;String, CmiHome&gt;
 * returned by {@code CMIUser#getHomes()} and writes each home to the
 * OreoEssentials {@link StorageApi}.
 */
public class CMIHomeImporter {

    private final StorageApi   storage;
    private final HomeDirectory homeDirectory;
    private final String        serverName;
    private final Logger        logger;
    private final boolean       skipExisting;

    public CMIHomeImporter(
            StorageApi   storage,
            HomeDirectory homeDirectory,
            String        serverName,
            Logger        logger,
            boolean       skipExisting
    ) {
        this.storage       = storage;
        this.homeDirectory = homeDirectory;
        this.serverName    = serverName;
        this.logger        = logger;
        this.skipExisting  = skipExisting;
    }

    /**
     * Runs the import and returns the number of homes successfully written.
     *
     * @param cmiPlugin the CMI JavaPlugin instance (obtained from PluginManager)
     */
    public int importHomes(Plugin cmiPlugin) {
        int imported = 0;
        int skipped  = 0;
        int failed   = 0;

        logger.info("[CMI Import] [Homes] Starting home migration…");

        try {
            // CMI.getInstance()
            Object cmiInstance = CMIReflection.getInstance(cmiPlugin);

            // CMI#getPlayerManager()
            Object playerManager = CMIReflection.invoke(cmiInstance, "getPlayerManager");

            // PlayerManager#getAllUsers() → Map<UUID, CMIUser>
            @SuppressWarnings("unchecked")
            Map<UUID, Object> allUsers = (Map<UUID, Object>) CMIReflection.invoke(playerManager, "getAllUsers");

            if (allUsers == null || allUsers.isEmpty()) {
                logger.warning("[CMI Import] [Homes] No users found in CMI.");
                return 0;
            }

            for (Map.Entry<UUID, Object> userEntry : allUsers.entrySet()) {
                Object cmiUser = userEntry.getValue();
                UUID   uuid    = userEntry.getKey();

                if (uuid == null || cmiUser == null) continue;

                // Skip fake / system accounts
                Boolean fake = (Boolean) CMIReflection.invokeQuiet(cmiUser, "isFakeUser");
                if (Boolean.TRUE.equals(fake)) continue;

                String playerName = (String) CMIReflection.invoke(cmiUser, "getName");

                // CMIUser#getHomes() → LinkedHashMap<String, CmiHome>
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Object> homes =
                        (LinkedHashMap<String, Object>) CMIReflection.invoke(cmiUser, "getHomes");

                if (homes == null || homes.isEmpty()) continue;

                for (Map.Entry<String, Object> homeEntry : homes.entrySet()) {
                    Object cmiHome = homeEntry.getValue();
                    if (cmiHome == null) { failed++; continue; }

                    String homeName = (String) CMIReflection.invoke(cmiHome, "getName");
                    if (homeName == null || homeName.isBlank()) { failed++; continue; }

                    homeName = homeName.toLowerCase();

                    // Skip if already exists and skipExisting
                    if (skipExisting && storage.getHome(uuid, homeName) != null) {
                        skipped++;
                        continue;
                    }

                    // CmiHome#getLoc() → CMILocation
                    Object cmiLoc = CMIReflection.invoke(cmiHome, "getLoc");
                    Location loc  = CMIReflection.toLocation(cmiLoc);

                    if (loc == null || loc.getWorld() == null) {
                        logger.warning("[CMI Import] [Homes] Cannot resolve location for home '"
                                + homeName + "' of " + (playerName != null ? playerName : uuid)
                                + " — skipping.");
                        skipped++;
                        continue;
                    }

                    try {
                        boolean ok = storage.setHome(uuid, homeName, loc);
                        if (ok && homeDirectory != null) {
                            homeDirectory.setHomeServer(uuid, homeName, serverName);
                        }
                        imported++;
                    } catch (Exception e) {
                        logger.warning("[CMI Import] [Homes] Failed to save home '"
                                + homeName + "' for " + uuid + ": " + e.getMessage());
                        failed++;
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[CMI Import] [Homes] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }

        logger.info("[CMI Import] [Homes] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return imported;
    }
}
