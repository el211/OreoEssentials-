package fr.elias.oreoEssentials.modules.shards;

import fr.elias.oreoEssentials.modules.shards.config.ShardConfig;
import fr.elias.oreoEssentials.modules.shards.listeners.*;
import fr.elias.oreoEssentials.modules.shards.redis.ShardHandoffManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;


public class OreoShardsModule {

    private final JavaPlugin plugin;
    private ShardConfig config;
    private ShardHandoffManager handoffManager;
    private ShardManager shardManager;
    private BorderDetectionListener borderListener;

    public OreoShardsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getLogger().info("[Sharding] Starting initialization...");

        File serverFolder = new File(plugin.getDataFolder(), "server");
        if (!serverFolder.exists()) serverFolder.mkdirs();
        File configFile = new File(serverFolder, "shards.yml");

        if (!configFile.exists()) {
            try {
                plugin.saveResource("server/shards.yml", false);
                plugin.getLogger().info("[Sharding] Created server/shards.yml from resources");
            } catch (Exception e) {
                plugin.getLogger().info("[Sharding] Creating default server/shards.yml...");
                createDefaultConfig(configFile);
            }
        }

        FileConfiguration configYml = YamlConfiguration.loadConfiguration(configFile);
        this.config = new ShardConfig(configYml);

        if (!config.isEnabled()) {
            plugin.getLogger().info("[Sharding] Disabled in config (sharding.enabled=false)");

            registerShardCommand();
            return;
        }

        ShardConfig.RedisConfig redisConfig = config.getRedis();
        this.handoffManager = new ShardHandoffManager(
                redisConfig.host,
                redisConfig.port,
                redisConfig.password
        );

        if (!handoffManager.testConnection()) {
            plugin.getLogger().severe("[Sharding] Failed to connect to Redis!");
            plugin.getLogger().severe("[Sharding] Check your Redis configuration in server/shards.yml");
            plugin.getLogger().info("[Sharding] Command /shard will still work for configuration");

            registerShardCommand();
            return;
        }

        plugin.getLogger().info("[Sharding] Connected to Redis successfully");

        String shardId = System.getProperty("shardId", "shard-0-0");
        plugin.getLogger().info("[Sharding] Running as shard: " + shardId);

        this.shardManager = new ShardManager(plugin, config, shardId);

        this.borderListener = new BorderDetectionListener(shardManager, handoffManager, config);

        plugin.getServer().getPluginManager().registerEvents(
                borderListener,
                plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
                new ChunkPreloadListener(plugin, redisConfig.host, redisConfig.port, redisConfig.password),
                plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
                new CombatTracker(borderListener),
                plugin
        );
        SafeZoneListener safeZoneListener = new SafeZoneListener(shardManager);
        plugin.getServer().getPluginManager().registerEvents(
                safeZoneListener,
                plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
                new ShardJoinListener(plugin, handoffManager),
                plugin
        );

        registerShardCommand();

        plugin.getLogger().info("[Sharding] OreoEssentials Sharding enabled!");
        plugin.getLogger().info("[Sharding] Proxy: " + config.getProxyType());
        plugin.getLogger().info("[Sharding] Worlds configured: " + getConfiguredWorldsCount());
    }


    private void registerShardCommand() {
        plugin.getLogger().info("[Sharding] Attempting to register /shard command...");

        var shardCmd = new fr.elias.oreoEssentials.modules.shards.commands.ShardCommand(
                plugin,
                shardManager,
                config
        );

        ((fr.elias.oreoEssentials.OreoEssentials) plugin).getCommands().registerLegacy("shard", shardCmd, shardCmd);

        plugin.getLogger().info("[Sharding] ✓ /shard command registered successfully!");
    }

    private void createDefaultConfig(File configFile) {
        try {
            configFile.getParentFile().mkdirs();

            String defaultConfig = """
                # OreoEssentials Sharding Configuration
                # Seamless world sharding like Donut SMP
                
                sharding:
                  enabled: false
                  proxy: VELOCITY
                  
                  # Transfer cooldown (prevents rapid back-and-forth exploits)
                  transfer-cooldown-ms: 3000
                  
                  # Redis connection (for ultra-fast handoff)
                  redis:
                    host: localhost
                    port: 6379
                    password: ""
                  
                  # World configurations
                  # Use /shard create to set up worlds automatically
                  worlds: {}
                
                # Example configuration (created by /shard create command):
                # worlds:
                #   world:
                #     enabled: true
                #     mode: GRID
                #     shard-size: 10000
                #     transfer-buffer: 12
                #     safe-zone: 16
                #     dimension-servers:
                #       overworld: "shard-%x%-%z%"
                """;

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(defaultConfig);
            }

            plugin.getLogger().info("[Sharding] Created default shards.yml at: " + configFile.getPath());
        } catch (Exception e) {
            plugin.getLogger().severe("[Sharding] Failed to create shards.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void disable() {
        if (handoffManager != null) {
            handoffManager.shutdown();
            plugin.getLogger().info("[Sharding] Redis connection closed");
        }
    }

    public ShardHandoffManager getHandoffManager() {
        return handoffManager;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled() && handoffManager != null;
    }

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