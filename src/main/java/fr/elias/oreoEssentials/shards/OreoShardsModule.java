package fr.elias.oreoEssentials.shards;

import fr.elias.oreoEssentials.shards.config.ShardConfig;
import fr.elias.oreoEssentials.shards.listeners.BorderDetectionListener;
import fr.elias.oreoEssentials.shards.listeners.CombatTracker;
import fr.elias.oreoEssentials.shards.listeners.ShardJoinListener;
import fr.elias.oreoEssentials.shards.redis.ShardHandoffManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Main class for OreoEssentials Sharding Module
 *
 * Integration example - add to your main OreoEssentials plugin
 */
public class OreoShardsModule {

    private final JavaPlugin plugin;
    private ShardConfig config;
    private ShardHandoffManager handoffManager;
    private ShardManager shardManager;
    private BorderDetectionListener borderListener;

    public OreoShardsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the sharding system
     * Call this from your plugin's onEnable()
     */
    public void enable() {
        // 1. Load config
        File configFile = new File(plugin.getDataFolder(), "shards.yml");
        if (!configFile.exists()) {
            plugin.saveResource("shards.yml", false);
        }

        FileConfiguration configYml = YamlConfiguration.loadConfiguration(configFile);
        this.config = new ShardConfig(configYml);

        // Check if sharding is enabled
        if (!config.isEnabled()) {
            plugin.getLogger().info("Sharding is disabled in config");
            return;
        }

        // 2. Initialize Redis handoff manager
        ShardConfig.RedisConfig redisConfig = config.getRedis();
        this.handoffManager = new ShardHandoffManager(
                redisConfig.host,
                redisConfig.port,
                redisConfig.password
        );

        // Test Redis connection
        if (!handoffManager.testConnection()) {
            plugin.getLogger().severe("Failed to connect to Redis! Sharding disabled.");
            plugin.getLogger().severe("Check your Redis configuration in shards.yml");
            return;
        }

        plugin.getLogger().info("Connected to Redis successfully");

        // 3. Determine current shard ID
        // This should come from server.properties or command line arg
        // Example: -DshardId=shard-0-0
        String shardId = System.getProperty("shardId", "shard-0-0");
        plugin.getLogger().info("Running as shard: " + shardId);

        // 4. Initialize shard manager
        this.shardManager = new ShardManager(plugin, config, shardId);

        // 5. Register listeners
        this.borderListener = new BorderDetectionListener(shardManager, handoffManager, config);

        plugin.getServer().getPluginManager().registerEvents(
                borderListener,
                plugin
        );

        plugin.getServer().getPluginManager().registerEvents(
                new CombatTracker(borderListener),
                plugin
        );

        plugin.getServer().getPluginManager().registerEvents(
                new ShardJoinListener(plugin, handoffManager),
                plugin
        );

        plugin.getLogger().info("OreoEssentials Sharding enabled!");
        plugin.getLogger().info("Proxy: " + config.getProxyType());
        plugin.getLogger().info("Worlds configured: " + getConfiguredWorldsCount());
    }

    /**
     * Shutdown the sharding system
     * Call this from your plugin's onDisable()
     */
    public void disable() {
        if (handoffManager != null) {
            handoffManager.shutdown();
            plugin.getLogger().info("Redis connection closed");
        }
    }

    /**
     * Get the handoff manager (for other modules to use)
     */
    public ShardHandoffManager getHandoffManager() {
        return handoffManager;
    }

    /**
     * Get the shard manager (for other modules to use)
     */
    public ShardManager getShardManager() {
        return shardManager;
    }

    /**
     * Check if sharding is enabled and active
     */
    public boolean isEnabled() {
        return config != null && config.isEnabled() && handoffManager != null;
    }

    /**
     * Count configured worlds
     */
    private int getConfiguredWorldsCount() {
        if (config == null) return 0;

        int count = 0;
        for (String worldName : plugin.getServer().getWorlds().stream()
                .map(w -> w.getName())
                .toArray(String[]::new)) {
            if (config.getWorld(worldName) != null) {
                count++;
            }
        }
        return count;
    }
}

