package fr.elias.oreoEssentials.shards;


import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.elias.oreoEssentials.shards.config.ShardConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages shard boundaries and player transfers via Velocity
 */
public class ShardManager {

    private final Plugin plugin;
    private final ShardConfig config;
    private final String currentShardId;

    public ShardManager(Plugin plugin, ShardConfig config, String currentShardId) {
        this.plugin = plugin;
        this.config = config;
        this.currentShardId = currentShardId;

        // Register Velocity messaging channel
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    /**
     * Get the current shard ID this server represents
     */
    public String getCurrentShardId() {
        return currentShardId;
    }

    /**
     * Determine which shard a player should be on based on coordinates
     * Returns null if player is safely within current shard
     * Returns target shard ID if player is within buffer zone of border
     */
    public String getTargetShardAtBorder(String worldName, double x, double z, int buffer) {
        ShardConfig.WorldConfig worldConfig = config.getWorld(worldName);

        if (worldConfig == null || !worldConfig.enabled) {
            return null;
        }

        switch (worldConfig.mode) {
            case GRID:
                return getTargetShardGrid(worldConfig, x, z, buffer);
            case RADIAL:
                return getTargetShardRadial(worldConfig, x, z, buffer);
            default:
                return null;
        }
    }

    /**
     * Grid mode: World divided into square shards
     * Example: 4 shards = 2x2 grid
     */
    private String getTargetShardGrid(ShardConfig.WorldConfig worldConfig, double x, double z, int buffer) {
        int shardSize = worldConfig.shardSize;

        // Calculate which shard coordinates we're in
        int currentShardX = (int) Math.floor(x / shardSize);
        int currentShardZ = (int) Math.floor(z / shardSize);

        // Calculate position within current shard
        double localX = x - (currentShardX * shardSize);
        double localZ = z - (currentShardZ * shardSize);

        // Check if within buffer zone of any edge
        int targetShardX = currentShardX;
        int targetShardZ = currentShardZ;

        // Check X boundaries
        if (localX < buffer) {
            // Near left edge
            targetShardX = currentShardX - 1;
        } else if (localX > shardSize - buffer) {
            // Near right edge
            targetShardX = currentShardX + 1;
        }

        // Check Z boundaries
        if (localZ < buffer) {
            // Near top edge
            targetShardZ = currentShardZ - 1;
        } else if (localZ > shardSize - buffer) {
            // Near bottom edge
            targetShardZ = currentShardZ + 1;
        }

        // If we've moved to a different shard
        if (targetShardX != currentShardX || targetShardZ != currentShardZ) {
            // Format: "shard-X-Z" (e.g., "shard-0-1")
            String targetShardId = String.format("shard-%d-%d", targetShardX, targetShardZ);

            // Don't transfer if target is same as current
            if (!targetShardId.equals(currentShardId)) {
                return targetShardId;
            }
        }

        return null;
    }

    /**
     * Radial mode: World divided into concentric rings
     * Shard 0 = center, higher numbers = outer rings
     */
    private String getTargetShardRadial(ShardConfig.WorldConfig worldConfig, double x, double z, int buffer) {
        int shardSize = worldConfig.shardSize;

        // Distance from center (0, 0)
        double distance = Math.sqrt(x * x + z * z);

        // Which ring are we in?
        int currentRing = (int) Math.floor(distance / shardSize);

        // Distance to inner and outer ring boundaries
        double innerBoundary = currentRing * shardSize;
        double outerBoundary = (currentRing + 1) * shardSize;

        String targetShardId = null;

        // Check if near inner boundary
        if (distance < innerBoundary + buffer && currentRing > 0) {
            targetShardId = "shard-ring-" + (currentRing - 1);
        }
        // Check if near outer boundary
        else if (distance > outerBoundary - buffer) {
            targetShardId = "shard-ring-" + (currentRing + 1);
        }

        // Don't transfer if target is same as current
        if (targetShardId != null && !targetShardId.equals(currentShardId)) {
            return targetShardId;
        }

        return null;
    }

    /**
     * Transfer player to target shard via Velocity
     * Uses BungeeCord plugin messaging channel
     */
    public void transferPlayerToShard(Player player, String targetShard) {
        // Get actual server name from shard ID (from config)
        String targetServerName = resolveShardToServerName(targetShard);

        if (targetServerName == null) {
            plugin.getLogger().warning("Could not resolve shard ID to server name: " + targetShard);
            return;
        }

        // Send player to target server via Velocity
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(targetServerName);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        plugin.getLogger().info(String.format(
                "Transferring %s to shard %s (server: %s)",
                player.getName(),
                targetShard,
                targetServerName
        ));
    }

    /**
     * Convert shard ID to actual server name
     * Example: "shard-0-1" -> "smp-01" (based on config)
     */
    private String resolveShardToServerName(String shardId) {
        // This would use your config mapping
        // For now, simple example:

        // Grid format: "shard-X-Z" -> "smp-X-Z"
        if (shardId.startsWith("shard-") && !shardId.contains("ring")) {
            return shardId.replace("shard-", "smp-");
        }

        // Radial format: "shard-ring-N" -> "smp-ring-N"
        if (shardId.startsWith("shard-ring-")) {
            return shardId.replace("shard-", "smp-");
        }

        // Fallback: return as-is
        return shardId;
    }

    /**
     * Calculate safe zone boundaries (where PvP/teleport is disabled)
     */
    public boolean isInSafeZone(String worldName, double x, double z) {
        ShardConfig.WorldConfig worldConfig = config.getWorld(worldName);

        if (worldConfig == null) {
            return false;
        }

        int safeZone = worldConfig.safeZone;

        // Check if within safe zone distance of any border
        String nearBorder = getTargetShardAtBorder(worldName, x, z, safeZone);

        return nearBorder != null;
    }
}