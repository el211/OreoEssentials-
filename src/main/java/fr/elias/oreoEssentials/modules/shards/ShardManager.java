package fr.elias.oreoEssentials.modules.shards;

import fr.elias.oreoEssentials.modules.shards.config.ShardConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;


public class ShardManager {

    private final Plugin plugin;
    private final ShardConfig config;
    private final String currentShardId;

    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    public ShardManager(Plugin plugin, ShardConfig config, String currentShardId) {
        this.plugin = plugin;
        this.config = config;
        this.currentShardId = currentShardId;

        ShardConfig.RedisConfig redisConfig = config.getRedis();
        this.redisHost = redisConfig.host;
        this.redisPort = redisConfig.port;
        this.redisPassword = redisConfig.password;

    }


    public String getCurrentShardId() {
        return currentShardId;
    }


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


    private String getTargetShardGrid(ShardConfig.WorldConfig worldConfig, double x, double z, int buffer) {
        int shardSize = worldConfig.shardSize;

        int currentShardX = (int) Math.floor(x / shardSize);
        int currentShardZ = (int) Math.floor(z / shardSize);

        double localX = x - (currentShardX * shardSize);
        double localZ = z - (currentShardZ * shardSize);

        int targetShardX = currentShardX;
        int targetShardZ = currentShardZ;

        if (localX < buffer) {
            targetShardX = currentShardX - 1;
        } else if (localX > shardSize - buffer) {
            targetShardX = currentShardX + 1;
        }

        if (localZ < buffer) {
            targetShardZ = currentShardZ - 1;
        } else if (localZ > shardSize - buffer) {
            targetShardZ = currentShardZ + 1;
        }

        if (targetShardX != currentShardX || targetShardZ != currentShardZ) {
            String targetShardId = String.format("shard-%d-%d", targetShardX, targetShardZ);

            if (!targetShardId.equals(currentShardId)) {
                return targetShardId;
            }
        }

        return null;
    }


    private String getTargetShardRadial(ShardConfig.WorldConfig worldConfig, double x, double z, int buffer) {
        int shardSize = worldConfig.shardSize;

        double distance = Math.sqrt(x * x + z * z);

        int currentRing = (int) Math.floor(distance / shardSize);

        double innerBoundary = currentRing * shardSize;
        double outerBoundary = (currentRing + 1) * shardSize;

        String targetShardId = null;

        if (distance < innerBoundary + buffer && currentRing > 0) {
            targetShardId = "shard-ring-" + (currentRing - 1);
        }
        else if (distance > outerBoundary - buffer) {
            targetShardId = "shard-ring-" + (currentRing + 1);
        }

        if (targetShardId != null && !targetShardId.equals(currentShardId)) {
            return targetShardId;
        }

        return null;
    }


    public void transferPlayerToShard(Player player, String targetShard) {
        String message = String.format("%s|%s|%.2f|%.2f|%.2f",
                player.getUniqueId(),
                targetShard,
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
        );

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

            fallbackDirectTransfer(player, targetShard);
        }
    }


    private void fallbackDirectTransfer(Player player, String targetShard) {
        plugin.getLogger().warning("[ShardTransfer] Using fallback direct transfer (will show loading screen)");

        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(targetShard);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }
    }


    public boolean isInSafeZone(String worldName, double x, double z) {
        ShardConfig.WorldConfig worldConfig = config.getWorld(worldName);

        if (worldConfig == null) {
            return false;
        }

        int safeZone = worldConfig.safeZone;

        String nearBorder = getTargetShardAtBorder(worldName, x, z, safeZone);

        return nearBorder != null;
    }
}