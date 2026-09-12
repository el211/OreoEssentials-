package fr.elias.oreoEssentials.migration.cmi;

import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Imports global warps from a running CMI instance into OreoEssentials.
 *
 * Reads {@code WarpManager#getWarps()} (a {@code HashMap<String, CmiWarp>})
 * via reflection and writes each warp to {@link StorageApi}.
 */
public class CMIWarpImporter {

    private final StorageApi    storage;
    private final WarpDirectory warpDirectory;
    private final String        serverName;
    private final Logger        logger;
    private final boolean       skipExisting;

    public CMIWarpImporter(
            StorageApi    storage,
            WarpDirectory warpDirectory,
            String        serverName,
            Logger        logger,
            boolean       skipExisting
    ) {
        this.storage       = storage;
        this.warpDirectory = warpDirectory;
        this.serverName    = serverName;
        this.logger        = logger;
        this.skipExisting  = skipExisting;
    }

    /**
     * Runs the import and returns the number of warps successfully written.
     *
     * @param cmiPlugin the CMI JavaPlugin instance (obtained from PluginManager)
     */
    public int importWarps(Plugin cmiPlugin) {
        int imported = 0;
        int skipped  = 0;
        int failed   = 0;

        logger.info("[CMI Import] [Warps] Starting warp migration…");

        try {
            Object cmiInstance  = CMIReflection.getInstance(cmiPlugin);
            Object warpManager  = CMIReflection.invoke(cmiInstance, "getWarpManager");

            // WarpManager#getWarps() → HashMap<String, CmiWarp>
            @SuppressWarnings("unchecked")
            Map<String, Object> warps =
                    (Map<String, Object>) CMIReflection.invoke(warpManager, "getWarps");

            if (warps == null || warps.isEmpty()) {
                logger.info("[CMI Import] [Warps] No warps found in CMI.");
                return 0;
            }

            for (Map.Entry<String, Object> entry : warps.entrySet()) {
                Object cmiWarp = entry.getValue();
                if (cmiWarp == null) { failed++; continue; }

                String warpName = (String) CMIReflection.invoke(cmiWarp, "getName");
                if (warpName == null || warpName.isBlank()) { failed++; continue; }

                if (skipExisting && storage.getWarp(warpName) != null) {
                    skipped++;
                    continue;
                }

                // CmiWarp#getLoc() → CMILocation  (primary location)
                Object cmiLoc = CMIReflection.invoke(cmiWarp, "getLoc");
                Location loc  = CMIReflection.toLocation(cmiLoc);

                if (loc == null || loc.getWorld() == null) {
                    logger.warning("[CMI Import] [Warps] Cannot resolve location for warp '"
                            + warpName + "' — skipping.");
                    skipped++;
                    continue;
                }

                try {
                    storage.setWarp(warpName, loc);
                    if (warpDirectory != null) {
                        warpDirectory.setWarpServer(warpName, serverName);
                    }
                    imported++;
                } catch (Exception e) {
                    logger.warning("[CMI Import] [Warps] Failed to save warp '"
                            + warpName + "': " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            logger.severe("[CMI Import] [Warps] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }

        logger.info("[CMI Import] [Warps] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return imported;
    }
}
