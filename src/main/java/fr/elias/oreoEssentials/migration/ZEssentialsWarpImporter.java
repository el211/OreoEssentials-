package fr.elias.oreoEssentials.migration;

import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.sql.*;
import java.util.logging.Logger;

/**
 * Imports server warps from zEssentials into OreoEssentials.
 *
 * zEssentials stores warps inside a serialised ConfigStorage object.
 * The exact persistence target depends on the zEssentials storage backend:
 *
 *   SQL backend  → <prefix>storages table, row with name = "ConfigStorage"
 *                  content column holds a Gson-serialised JSON blob.
 *
 *   JSON backend → plugins/zEssentials/users/ (per-player only, no warps there)
 *                  warps stay in the ConfigStorage JSON file.
 *
 * This importer tries the SQL storages table first. If no row is found (or the
 * column content is empty) it falls back to a JSON file whose path is
 * configurable (default: plugins/zEssentials/config_storage.json).
 *
 * Expected JSON structure (Gson default field names from ConfigStorage):
 * {
 *   "warps": [
 *     {
 *       "name": "spawn",
 *       "location": {
 *         "world":  "world",       ← may also be "worldName"
 *         "x": 0.0,
 *         "y": 64.0,
 *         "z": 0.0,
 *         "yaw": 0.0,
 *         "pitch": 0.0
 *       }
 *     }
 *   ]
 * }
 */
public class ZEssentialsWarpImporter {

    private final StorageApi storage;
    private final WarpDirectory warpDirectory;
    private final String serverName;
    private final Logger logger;
    private final String storagesTable;
    private final File fallbackJsonFile;
    private final boolean skipExisting;

    /**
     * @param storage          OreoEssentials storage backend
     * @param warpDirectory    Optional cross-server warp directory (may be null)
     * @param serverName       This server's logical name
     * @param logger           Plugin logger
     * @param tablePrefix      zEssentials table prefix, e.g. "zessentials_"
     * @param fallbackJsonFile Path to config_storage.json (used when SQL row absent)
     * @param skipExisting     Skip warps that already exist in OreoEssentials
     */
    public ZEssentialsWarpImporter(
            StorageApi storage,
            WarpDirectory warpDirectory,
            String serverName,
            Logger logger,
            String tablePrefix,
            File fallbackJsonFile,
            boolean skipExisting
    ) {
        this.storage         = storage;
        this.warpDirectory   = warpDirectory;
        this.serverName      = serverName;
        this.logger          = logger;
        this.storagesTable   = tablePrefix + "storages";
        this.fallbackJsonFile = fallbackJsonFile;
        this.skipExisting    = skipExisting;
    }

    /**
     * Attempts to import warps. Returns the number of warps successfully imported.
     */
    public int importWarps(Connection connection) {
        // 1. Try SQL storages table
        String json = readJsonFromSql(connection);

        // 2. Fallback to JSON file
        if (json == null && fallbackJsonFile != null && fallbackJsonFile.exists()) {
            logger.info("[zEssentials Import] [Warps] SQL row not found — trying JSON file: "
                    + fallbackJsonFile.getAbsolutePath());
            json = readJsonFile(fallbackJsonFile);
        }

        if (json == null) {
            logger.warning("[zEssentials Import] [Warps] No warp data source found. "
                    + "Check that zEssentials has been run at least once so data is persisted, "
                    + "or set 'zessentials-migration.warp-json-path' to the correct file.");
            return 0;
        }

        return parseAndImportWarps(json);
    }



    private String readJsonFromSql(Connection connection) {
        String[] candidates = {"ConfigStorage", "config_storage", "configstorage"};

        for (String key : candidates) {
            try {
                String content = queryStoragesTable(connection, key);
                if (content != null && !content.isBlank()) {
                    logger.info("[zEssentials Import] [Warps] Found ConfigStorage in SQL table '"
                            + storagesTable + "' with key '" + key + "'.");
                    return content;
                }
            } catch (SQLException e) {
                // Table might not exist — suppress and fall through to JSON fallback
                logger.fine("[zEssentials Import] [Warps] SQL lookup failed for key '" + key
                        + "': " + e.getMessage());
            }
        }
        return null;
    }

    private String queryStoragesTable(Connection connection, String key) throws SQLException {
        String sql = "SELECT content FROM " + storagesTable + " WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("content");
            }
        }
        return null;
    }



    private String readJsonFile(File file) {
        try (Reader r = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            logger.warning("[zEssentials Import] [Warps] Could not read JSON file: " + e.getMessage());
            return null;
        }
    }


    /**
     * Minimal JSON array parser for the "warps" field.
     * Handles the Gson-serialised ConfigStorage output without pulling in Gson.
     *
     * Expected shape:
     *   { ..., "warps": [ { "name":"x", "location":{ "world":"w","x":0,"y":0,"z":0 } }, ... ] }
     *
     * SafeLocation field names supported:  "world"  OR  "worldName"
     */
    private int parseAndImportWarps(String json) {
        int imported = 0;
        int skipped  = 0;
        int failed   = 0;

        // Find the warps array
        int warpsIdx = json.indexOf("\"warps\"");
        if (warpsIdx < 0) {
            logger.warning("[zEssentials Import] [Warps] JSON does not contain a 'warps' key — no warps to import.");
            return 0;
        }

        int arrayStart = json.indexOf('[', warpsIdx);
        int arrayEnd   = matchingBracket(json, arrayStart, '[', ']');
        if (arrayStart < 0 || arrayEnd < 0) {
            logger.warning("[zEssentials Import] [Warps] Could not parse warps JSON array.");
            return 0;
        }

        String array = json.substring(arrayStart + 1, arrayEnd);

        int depth  = 0;
        int objStart = -1;

        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = array.substring(objStart, i + 1);
                    try {
                        String name    = extractString(obj, "name");
                        String locJson = extractObject(obj, "location");

                        if (name == null || locJson == null) { failed++; continue; }

                        // world field: try "world" first, then "worldName"
                        String worldName = extractString(locJson, "world");
                        if (worldName == null) worldName = extractString(locJson, "worldName");

                        double x     = extractDouble(locJson, "x",     0);
                        double y     = extractDouble(locJson, "y",     64);
                        double z     = extractDouble(locJson, "z",     0);
                        float  yaw   = (float) extractDouble(locJson, "yaw",   0);
                        float  pitch = (float) extractDouble(locJson, "pitch", 0);

                        if (worldName == null) {
                            logger.warning("[zEssentials Import] [Warps] No world found for warp '"
                                    + name + "' — skipping.");
                            skipped++;
                            continue;
                        }

                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            logger.warning("[zEssentials Import] [Warps] World '" + worldName
                                    + "' not loaded — skipping warp '" + name + "'.");
                            skipped++;
                            continue;
                        }

                        if (skipExisting && storage.getWarp(name) != null) {
                            skipped++;
                            continue;
                        }

                        Location loc = new Location(world, x, y, z, yaw, pitch);
                        storage.setWarp(name, loc);
                        if (warpDirectory != null) {
                            warpDirectory.setWarpServer(name, serverName);
                        }
                        imported++;

                    } catch (Exception e) {
                        logger.warning("[zEssentials Import] [Warps] Error parsing warp object: "
                                + e.getMessage());
                        failed++;
                    }
                    objStart = -1;
                }
            }
        }

        logger.info("[zEssentials Import] [Warps] Done — imported=" + imported
                + ", skipped=" + skipped + ", failed=" + failed);
        return imported;
    }



    /** Returns the string value of a JSON key, or null. */
    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    /** Returns the raw JSON of a nested object for a given key, or null. */
    private static String extractObject(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int brace = json.indexOf('{', colon + 1);
        if (brace < 0) return null;
        int end = matchingBracket(json, brace, '{', '}');
        if (end < 0) return null;
        return json.substring(brace, end + 1);
    }

    /** Returns the numeric value of a JSON key, or defaultValue. */
    private static double extractDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return defaultValue;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return defaultValue;

        // skip whitespace
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

        int end = i;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-'
                || json.charAt(end) == 'E' || json.charAt(end) == 'e'
                || json.charAt(end) == '+')) end++;

        if (end == i) return defaultValue;
        try {
            return Double.parseDouble(json.substring(i, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Finds the index of the closing bracket/brace that matches the one at openIdx. */
    private static int matchingBracket(String s, int openIdx, char open, char close) {
        if (openIdx < 0 || openIdx >= s.length()) return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == open)  { depth++; }
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
