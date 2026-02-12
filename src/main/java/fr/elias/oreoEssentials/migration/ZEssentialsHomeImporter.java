package fr.elias.oreoEssentials.migration;

import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

public class ZEssentialsHomeImporter {

    private final StorageApi storage;
    private final HomeDirectory homeDirectory;
    private final String localServerName;
    private final Logger logger;
    private final String tableName;

    public ZEssentialsHomeImporter(
            StorageApi storage,
            HomeDirectory homeDirectory,
            String localServerName,
            Logger logger,
            String tablePrefix
    ) {
        this.storage = storage;
        this.homeDirectory = homeDirectory;
        this.localServerName = localServerName;
        this.logger = logger;
        this.tableName = tablePrefix + "user_homes";
    }


    public int importHomes(Connection connection) throws SQLException {
        String sql = "SELECT unique_id, name, location FROM " + tableName;
        int imported = 0;
        int skipped = 0;

        logger.info("[OreoEssentials] [Import] Starting zEssentials homes import from table '" + tableName + "'");

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String uuidStr = rs.getString("unique_id");
                String homeName = rs.getString("name");
                String locStr = rs.getString("location");

                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Location loc = parseLocationFromZessentials(locStr);

                    if (loc == null || loc.getWorld() == null) {
                        skipped++;
                        logger.warning("[OreoEssentials] [Import] Skipping home '" + homeName +
                                "' for " + uuid + " (invalid location: " + locStr + ")");
                        continue;
                    }

                    String normalizedName = homeName.toLowerCase();

                    boolean ok = storage.setHome(uuid, normalizedName, loc);
                    if (ok && homeDirectory != null) {
                        homeDirectory.setHomeServer(uuid, normalizedName, localServerName);
                    }

                    imported++;

                } catch (Exception ex) {
                    skipped++;
                    logger.warning("[OreoEssentials] [Import] Error importing home row: " + ex.getMessage());
                }
            }
        }

        logger.info("[OreoEssentials] [Import] zEssentials homes import finished. " +
                "Imported=" + imported + ", Skipped=" + skipped);

        return imported;
    }


    private Location parseLocationFromZessentials(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        try {
            String[] parts = raw.split(";");
            if (parts.length < 4) {
                return null;
            }

            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = 0f;
            float pitch = 0f;

            if (parts.length >= 5) {
                yaw = Float.parseFloat(parts[4]);
            }
            if (parts.length >= 6) {
                pitch = Float.parseFloat(parts[5]);
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }

            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
