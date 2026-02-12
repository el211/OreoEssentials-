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

        File configFile = new File(plugin.getDataFolder(), "shards.yml");

        if (!configFile.exists()) {
            try {
                plugin.saveResource("shards.yml", false);
                plugin.getLogger().info("[Sharding] Created shards.yml from resources");
            } catch (Exception e) {
                plugin.getLogger().info("[Sharding] Creating default shards.yml...");
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
            plugin.getLogger().severe("[Sharding] Check your Redis configuration in shards.yml");
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

        org.bukkit.command.PluginCommand cmd = plugin.getCommand("shard");

        if (cmd == null) {
            plugin.getLogger().severe("[Sharding] ✗ FAILED: Command 'shard' not found in plugin.yml!");
            plugin.getLogger().severe("[Sharding] ✗ Make sure your plugin.yml contains:");
            plugin.getLogger().severe("[Sharding] ✗   shard:");
            plugin.getLogger().severe("[Sharding] ✗     description: Manage world sharding");
            plugin.getLogger().severe("[Sharding] ✗     permission: oreo.shard.admin");
            return;
        }

        cmd.setExecutor(shardCmd);
        cmd.setTabCompleter(shardCmd);

        plugin.getLogger().info("[Sharding] ✓ /shard command registered successfully!");
        plugin.getLogger().info("[Sharding] ✓ Executor: " + shardCmd.getClass().getSimpleName());
        plugin.getLogger().info("[Sharding] ✓ Command name: " + cmd.getName());
        plugin.getLogger().info("[Sharding] ✓ Aliases: " + cmd.getAliases());

        try {
            if (cmd.getExecutor() == null) {
                plugin.getLogger().severe("[Sharding] ✗ WARNING: Executor is null after registration!");
            } else {
                plugin.getLogger().info("[Sharding] ✓ Executor verified: " + cmd.getExecutor().getClass().getSimpleName());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[Sharding] ✗ Error verifying executor: " + e.getMessage());
        }
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