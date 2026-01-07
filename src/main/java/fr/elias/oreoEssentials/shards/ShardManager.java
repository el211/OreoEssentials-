package fr.elias.oreoEssentials.shards;

import fr.elias.oreoEssentials.shards.config.ShardConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;

/**
 * Manages shard boundaries and player transfers via Velocity
 */
public class ShardManager {

    private final Plugin plugin;
    private final ShardConfig config;
    private final String currentShardId;

    // Redis connection info (from shards.yml)
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    public ShardManager(Plugin plugin, ShardConfig config, String currentShardId) {
        this.plugin = plugin;
        this.config = config;
        this.currentShardId = currentShardId;

        // Get Redis config from shards.yml
        ShardConfig.RedisConfig redisConfig = config.getRedis();
        this.redisHost = redisConfig.host;
        this.redisPort = redisConfig.port;
        this.redisPassword = redisConfig.password;

        // We don't need BungeeCord channel anymore - using Redis instead!
        // plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
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
            targetShardX = currentShardX - 1;
        } else if (localX > shardSize - buffer) {
            targetShardX = currentShardX + 1;
        }

        // Check Z boundaries
        if (localZ < buffer) {
            targetShardZ = currentShardZ - 1;
        } else if (localZ > shardSize - buffer) {
            targetShardZ = currentShardZ + 1;
        }

            // If we've moved to a different shard
        if (targetShardX != currentShardX || targetShardZ != currentShardZ) {
            // Don't use Math.abs() - we need to preserve negative coordinates!
            String targetShardId = String.format("shard-%d-%d", targetShardX, targetShardZ);

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
     * Transfer player to target shard via Velocity (SEAMLESS!)
     * Sends Redis message to Velocity plugin for seamless transfer
     */
    public void transferPlayerToShard(Player player, String targetShard) {
        // Send Redis message to Velocity for seamless transfer
        String message = String.format("%s|%s|%.2f|%.2f|%.2f",
                player.getUniqueId(),
                targetShard,
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
        );

        // Publish to Redis (Velocity will handle the seamless transfer)
        try (Jedis redis = new Jedis(redisHost, redisPort)) {
            if (redisPassword != null && !redisPassword.isEmpty()) {
                redis.auth(redisPassword);
            }

            redis.publish("shard_transfer_requests", message);

            plugin.getLogger().info(String.format(
                    "[ShardTransfer] Requested seamless transfer for %s to %s via Redis",
                    player.getName(),
                    targetShard
            ));
        } catch (Exception e) {
            plugin.getLogger().severe("[ShardTransfer] Failed to send Redis transfer request: " + e.getMessage());
            e.printStackTrace();

            // Fallback to old direct transfer if Redis fails
            fallbackDirectTransfer(player, targetShard);
        }
    }

    /**
     * Fallback to old direct transfer if Redis fails
     */
    private void fallbackDirectTransfer(Player player, String targetShard) {
        plugin.getLogger().warning("[ShardTransfer] Using fallback direct transfer (will show loading screen)");

        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(targetShard);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        // Register channel if not already registered
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }
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