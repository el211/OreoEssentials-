package fr.elias.oreoEssentials.modules.shards.config;



import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for world sharding system
 */
public class ShardConfig {

    private final boolean enabled;
    private final String proxyType;
    private final RedisConfig redis;
    private final Map<String, WorldConfig> worlds;
    private final long transferCooldown;

    public ShardConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("sharding.enabled", false);
        this.proxyType = config.getString("sharding.proxy", "VELOCITY");
        this.transferCooldown = config.getLong("sharding.transfer-cooldown-ms", 3000);

        // Redis config
        ConfigurationSection redisSection = config.getConfigurationSection("sharding.redis");
        if (redisSection != null) {
            this.redis = new RedisConfig(
                    redisSection.getString("host", "localhost"),
                    redisSection.getInt("port", 6379),
                    redisSection.getString("password", "")
            );
        } else {
            this.redis = new RedisConfig("localhost", 6379, "");
        }

        // World configs
        this.worlds = new HashMap<>();
        ConfigurationSection worldsSection = config.getConfigurationSection("sharding.worlds");

        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
                if (worldSection != null) {
                    worlds.put(worldName, new WorldConfig(worldSection));
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProxyType() {
        return proxyType;
    }

    public RedisConfig getRedis() {
        return redis;
    }

    public WorldConfig getWorld(String worldName) {
        return worlds.get(worldName);
    }

    public long getTransferCooldown() {
        return transferCooldown;
    }

    /**
     * Redis connection config
     */
    public static class RedisConfig {
        public final String host;
        public final int port;
        public final String password;

        public RedisConfig(String host, int port, String password) {
            this.host = host;
            this.port = port;
            this.password = password;
        }
    }

    /**
     * Per-world sharding config
     */
    public static class WorldConfig {
        public final boolean enabled;
        public final ShardMode mode;
        public final int shardSize;
        public final int transferBuffer;
        public final int safeZone;
        public final Map<String, String> dimensionServers;

        public WorldConfig(ConfigurationSection section) {
            this.enabled = section.getBoolean("enabled", true);

            String modeStr = section.getString("mode", "GRID");
            try {
                this.mode = ShardMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid shard mode: " + modeStr);
            }

            this.shardSize = section.getInt("shard-size", 10000);
            this.transferBuffer = section.getInt("transfer-buffer", 12);
            this.safeZone = section.getInt("safe-zone", 16);

            this.dimensionServers = new HashMap<>();
            ConfigurationSection dimSection = section.getConfigurationSection("dimension-servers");
            if (dimSection != null) {
                for (String dim : dimSection.getKeys(false)) {
                    dimensionServers.put(dim, dimSection.getString(dim));
                }
            }
        }
    }

    /**
     * Sharding modes
     */
    public enum ShardMode {
        /**
         * Grid mode: World divided into square regions
         * Example: 4 shards = 2x2 grid
         */
        GRID,

        /**
         * Radial mode: Concentric rings from spawn
         * Example: Shard 0 = center, 1 = first ring, etc.
         */
        RADIAL
    }
}
