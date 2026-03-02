package fr.elias.oreoEssentials.migration.essentialsx;

import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads EssentialsX data files and imports homes, warps, and economy
 * into OreoEssentials using whatever backend (YAML / JSON / MongoDB) is active.
 *
 * EssentialsX layout (2.x branch, Configurate 4 serialisation):
 *   <dataPath>/userdata/{uuid}.yml  → homes + money
 *   <dataPath>/warps/{name}.yml     → one file per warp
 *
 * Location YAML keys (set by LocationTypeSerializer):
 *   world:      <world UUID as string>   ← NOT the world name!
 *   world-name: <human-readable name>    ← what we actually need
 *   x / y / z: double
 *   yaw / pitch: float
 *
 * Economy key:  money  (BigDecimal, stored as a quoted string e.g. '1500.75')
 * Player name:  lastAccountName  (camelCase, Configurate default field mapping)
 */
public class EssentialsXMigrator {

    public record Result(int imported, int skipped, int failed) {
        public Result add(Result other) {
            return new Result(imported + other.imported, skipped + other.skipped, failed + other.failed);
        }
    }

    public enum ConflictPolicy { SKIP, OVERWRITE }

    private final StorageApi storage;
    private final PlayerEconomyDatabase economy;
    private final HomeDirectory homeDirectory;
    private final WarpDirectory warpDirectory;
    private final String serverName;
    private final Logger logger;
    private final ConflictPolicy conflictPolicy;
    private final File essentialsDataDir;

    public EssentialsXMigrator(
            StorageApi storage,
            PlayerEconomyDatabase economy,
            HomeDirectory homeDirectory,
            WarpDirectory warpDirectory,
            String serverName,
            Logger logger,
            ConflictPolicy conflictPolicy,
            File essentialsDataDir
    ) {
        this.storage = storage;
        this.economy = economy;
        this.homeDirectory = homeDirectory;
        this.warpDirectory = warpDirectory;
        this.serverName = serverName;
        this.logger = logger;
        this.conflictPolicy = conflictPolicy;
        this.essentialsDataDir = essentialsDataDir;
    }

    // -------------------------------------------------------------------------
    // Homes
    // -------------------------------------------------------------------------

    /**
     * Imports all player homes from EssentialsX userdata/{uuid}.yml files.
     */
    public Result importHomes() {
        File userdataDir = new File(essentialsDataDir, "userdata");
        if (!userdataDir.isDirectory()) {
            logger.warning("[EssentialsX Import] [Homes] userdata directory not found at: "
                    + userdataDir.getAbsolutePath());
            return new Result(0, 0, 0);
        }

        File[] files = userdataDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.info("[EssentialsX Import] [Homes] No userdata files found.");
            return new Result(0, 0, 0);
        }

        int imported = 0, skipped = 0, failed = 0;

        for (File file : files) {
            // Files are named {uuid}.yml
            String fileName = file.getName().replace(".yml", "");
            UUID uuid;
            try {
                uuid = UUID.fromString(fileName);
            } catch (IllegalArgumentException e) {
                continue; // skip non-UUID files (legacy username-named files, etc.)
            }

            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                if (!cfg.isConfigurationSection("homes")) continue;

                for (String homeName : cfg.getConfigurationSection("homes").getKeys(false)) {
                    String path = "homes." + homeName;

                    World world = resolveWorld(cfg, path);
                    if (world == null) {
                        logger.warning("[EssentialsX Import] [Homes] Could not resolve world for home '"
                                + homeName + "' of " + uuid + " — skipping. "
                                + "(world=" + cfg.getString(path + ".world")
                                + ", world-name=" + cfg.getString(path + ".world-name") + ")");
                        skipped++;
                        continue;
                    }

                    double x     = cfg.getDouble(path + ".x");
                    double y     = cfg.getDouble(path + ".y");
                    double z     = cfg.getDouble(path + ".z");
                    float  yaw   = (float) cfg.getDouble(path + ".yaw");
                    float  pitch = (float) cfg.getDouble(path + ".pitch");

                    String normalizedName = homeName.toLowerCase();

                    if (conflictPolicy == ConflictPolicy.SKIP
                            && storage.getHome(uuid, normalizedName) != null) {
                        skipped++;
                        continue;
                    }

                    Location loc = new Location(world, x, y, z, yaw, pitch);
                    boolean ok = storage.setHome(uuid, normalizedName, loc);
                    if (ok) {
                        if (homeDirectory != null) {
                            homeDirectory.setHomeServer(uuid, normalizedName, serverName);
                        }
                        imported++;
                    } else {
                        failed++;
                    }
                }
            } catch (Exception e) {
                logger.warning("[EssentialsX Import] [Homes] Error reading " + file.getName()
                        + ": " + e.getMessage());
                failed++;
            }
        }

        logger.info("[EssentialsX Import] [Homes] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return new Result(imported, skipped, failed);
    }

    // -------------------------------------------------------------------------
    // Warps
    // -------------------------------------------------------------------------

    /**
     * Imports warps from EssentialsX warps/{name}.yml files.
     *
     * EssentialsX sanitises filenames (spaces → hyphens, slashes removed), so the
     * canonical warp name is taken from the "name" key inside the file, not from
     * the filename itself.
     */
    public Result importWarps() {
        File warpsDir = new File(essentialsDataDir, "warps");
        if (!warpsDir.isDirectory()) {
            logger.warning("[EssentialsX Import] [Warps] warps directory not found at: "
                    + warpsDir.getAbsolutePath());
            return new Result(0, 0, 0);
        }

        File[] files = warpsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.info("[EssentialsX Import] [Warps] No warp files found.");
            return new Result(0, 0, 0);
        }

        int imported = 0, skipped = 0, failed = 0;

        for (File file : files) {
            // Use the "name" key stored inside the file — the filename may be sanitised
            String fileBaseName = file.getName().replace(".yml", "");
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

                // Prefer the stored name key; fall back to the (possibly sanitised) filename
                String warpName = cfg.getString("name", fileBaseName);

                World world = resolveWorld(cfg, null); // top-level keys, no prefix
                if (world == null) {
                    logger.warning("[EssentialsX Import] [Warps] Could not resolve world for warp '"
                            + warpName + "' — skipping. "
                            + "(world=" + cfg.getString("world")
                            + ", world-name=" + cfg.getString("world-name") + ")");
                    skipped++;
                    continue;
                }

                double x     = cfg.getDouble("x");
                double y     = cfg.getDouble("y");
                double z     = cfg.getDouble("z");
                float  yaw   = (float) cfg.getDouble("yaw");
                float  pitch = (float) cfg.getDouble("pitch");

                if (conflictPolicy == ConflictPolicy.SKIP
                        && storage.getWarp(warpName) != null) {
                    skipped++;
                    continue;
                }

                Location loc = new Location(world, x, y, z, yaw, pitch);
                storage.setWarp(warpName, loc);
                if (warpDirectory != null) {
                    warpDirectory.setWarpServer(warpName, serverName);
                }
                imported++;

            } catch (Exception e) {
                logger.warning("[EssentialsX Import] [Warps] Error reading warp '"
                        + fileBaseName + "': " + e.getMessage());
                failed++;
            }
        }

        logger.info("[EssentialsX Import] [Warps] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return new Result(imported, skipped, failed);
    }

    // -------------------------------------------------------------------------
    // Economy
    // -------------------------------------------------------------------------

    /**
     * Imports player balances from EssentialsX userdata/{uuid}.yml files.
     *
     * EssentialsX stores money as a BigDecimal, serialised to YAML as a quoted
     * string (e.g. money: '1500.75') or occasionally as a plain number.
     */
    public Result importEconomy() {
        if (economy == null) {
            logger.warning("[EssentialsX Import] [Economy] No economy backend configured — skipping.");
            return new Result(0, 0, 0);
        }

        File userdataDir = new File(essentialsDataDir, "userdata");
        if (!userdataDir.isDirectory()) {
            logger.warning("[EssentialsX Import] [Economy] userdata directory not found at: "
                    + userdataDir.getAbsolutePath());
            return new Result(0, 0, 0);
        }

        File[] files = userdataDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) return new Result(0, 0, 0);

        int imported = 0, skipped = 0, failed = 0;

        for (File file : files) {
            String fileName = file.getName().replace(".yml", "");
            UUID uuid;
            try {
                uuid = UUID.fromString(fileName);
            } catch (IllegalArgumentException e) {
                continue;
            }

            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                if (!cfg.contains("money")) continue;

                double balance;
                Object raw = cfg.get("money");
                if (raw instanceof Number n) {
                    balance = n.doubleValue();
                } else if (raw instanceof String s) {
                    // EssentialsX serialises BigDecimal as a quoted string
                    balance = new BigDecimal(s.trim()).doubleValue();
                } else {
                    skipped++;
                    continue;
                }

                if (balance < 0) {
                    skipped++;
                    continue;
                }

                // EssentialsX stores the name as "lastAccountName" (camelCase — Configurate
                // default field mapping). We try the camelCase key first; if absent we fall
                // back to the hyphenated variant some older configs may have used.
                String playerName = cfg.getString("lastAccountName");
                if (playerName == null) playerName = cfg.getString("last-account-name");
                if (playerName == null) playerName = uuid.toString().substring(0, 8);

                if (conflictPolicy == ConflictPolicy.SKIP) {
                    double existing = economy.getBalance(uuid);
                    if (existing > 0) {
                        skipped++;
                        continue;
                    }
                }

                economy.setBalance(uuid, playerName, balance);
                imported++;

            } catch (Exception e) {
                logger.warning("[EssentialsX Import] [Economy] Error reading " + fileName
                        + ": " + e.getMessage());
                failed++;
            }
        }

        logger.info("[EssentialsX Import] [Economy] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return new Result(imported, skipped, failed);
    }

    // -------------------------------------------------------------------------
    // World resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves a Bukkit World from EssentialsX location keys, mirroring the
     * logic in EssentialsX's own LazyLocation#location() method.
     *
     * EssentialsX writes:
     *   world:      <UUID string>         (primary, written by LocationTypeSerializer)
     *   world-name: <human-readable name> (fallback, also written by LocationTypeSerializer)
     *
     * Resolution order:
     *   1. Parse "world-name" as a world name (fastest, most reliable)
     *   2. Parse "world" as a UUID → Bukkit.getWorld(UUID)
     *   3. Try "world" as a plain world name (handles pre-2.x files)
     *
     * @param cfg    The loaded YamlConfiguration
     * @param prefix Dot-path prefix (e.g. "homes.home") or null for top-level keys
     */
    private static World resolveWorld(YamlConfiguration cfg, String prefix) {
        String worldNameKey = prefix != null ? prefix + ".world-name" : "world-name";
        String worldIdKey   = prefix != null ? prefix + ".world"      : "world";

        // 1. Try world-name (human-readable)
        String worldName = cfg.getString(worldNameKey);
        if (worldName != null && !worldName.isBlank()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) return w;
        }

        // 2. Try world as UUID
        String worldId = cfg.getString(worldIdKey);
        if (worldId != null && !worldId.isBlank()) {
            try {
                UUID uid = UUID.fromString(worldId);
                World w = Bukkit.getWorld(uid);
                if (w != null) return w;
            } catch (IllegalArgumentException ignored) {
                // Not a UUID — fall through to name lookup
            }

            // 3. Try world as plain name (pre-2.x or custom format)
            World w = Bukkit.getWorld(worldId);
            if (w != null) return w;
        }

        return null;
    }
}
