package fr.elias.oreoEssentials.modules.shards.commands;

import fr.elias.oreoEssentials.modules.shards.ShardManager;
import fr.elias.oreoEssentials.modules.shards.config.ShardConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Arrays;
import java.util.List;


public class ShardCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ShardManager shardManager;
    private final ShardConfig config;

    public ShardCommand(Plugin plugin, ShardManager shardManager, ShardConfig config) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oreo.shard.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "createworld" -> handleCreateWorld(sender, args);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "setup" -> handleSetup(sender, args);
            case "test" -> handleTest(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }


    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /shard create <worldname> <GRID|RADIAL> <size> <totalShards>");
            sender.sendMessage("§cExample: /shard create survival GRID 10000 4");
            return;
        }

        String worldName = args[1];
        String modeStr = args[2].toUpperCase();
        int shardSize;
        int totalShards;

        try {
            shardSize = Integer.parseInt(args[3]);
            totalShards = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSize and totalShards must be numbers!");
            return;
        }

        if (!modeStr.equals("GRID") && !modeStr.equals("RADIAL")) {
            sender.sendMessage("§cMode must be GRID or RADIAL!");
            return;
        }

        if (shardSize < 1000) {
            sender.sendMessage("§cShard size must be at least 1000 blocks!");
            return;
        }

        if (totalShards < 2) {
            sender.sendMessage("§cYou need at least 2 shards!");
            return;
        }

        sender.sendMessage("§6Creating sharded world setup...");

        // Get world seed (or generate new one)
        long seed = System.currentTimeMillis();
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            seed = existingWorld.getSeed();
            sender.sendMessage("§aUsing existing world seed: " + seed);
        } else {
            sender.sendMessage("§aGenerated new seed: " + seed);
        }

        // Update shards.yml
        try {
            File configFile = new File(plugin.getDataFolder(), "shards.yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(configFile);

            yml.set("sharding.enabled", true);
            yml.set("sharding.worlds." + worldName + ".enabled", true);
            yml.set("sharding.worlds." + worldName + ".mode", modeStr);
            yml.set("sharding.worlds." + worldName + ".shard-size", shardSize);
            yml.set("sharding.worlds." + worldName + ".transfer-buffer", 12);
            yml.set("sharding.worlds." + worldName + ".safe-zone", 16);
            yml.set("sharding.worlds." + worldName + ".seed", seed);

            // Generate dimension server mappings
            if (modeStr.equals("GRID")) {
                int gridSize = (int) Math.ceil(Math.sqrt(totalShards));
                yml.set("sharding.worlds." + worldName + ".dimension-servers.overworld", "shard-%x%-%z%");
                sender.sendMessage("§aGrid mode: " + gridSize + "x" + gridSize + " shards");
            } else {
                yml.set("sharding.worlds." + worldName + ".dimension-servers.overworld", "shard-ring-%id%");
                sender.sendMessage("§aRadial mode: " + totalShards + " rings");
            }

            yml.save(configFile);

            sender.sendMessage("§aUpdated shards.yml successfully!");
            sender.sendMessage("");
            sender.sendMessage("§6§lNEXT STEPS:");
            sender.sendMessage("§e1. §fRun §b/shard createworld " + worldName + " §fon ALL shard servers");
            sender.sendMessage("§e2. §fSet world seed in server.properties: §blevel-seed=" + seed);
            sender.sendMessage("§e3. §fSet shard ID on each server: §bjava -DshardId=shard-X-Z -jar paper.jar");
            sender.sendMessage("§e4. §fRestart all servers");
            sender.sendMessage("");
            sender.sendMessage("§6Required shard server names (add to Velocity config.toml):");

            if (modeStr.equals("GRID")) {
                int gridSize = (int) Math.ceil(Math.sqrt(totalShards));
                for (int x = 0; x < gridSize; x++) {
                    for (int z = 0; z < gridSize; z++) {
                        sender.sendMessage("§b  - shard-" + x + "-" + z);
                    }
                }
            } else {
                for (int i = 0; i < totalShards; i++) {
                    sender.sendMessage("§b  - shard-ring-" + i);
                }
            }

        } catch (Exception e) {
            sender.sendMessage("§cFailed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * NEW: /shard createworld <worldname>
     * Actually creates the world dimension on THIS server
     * Run this on each shard server after /shard create
     */
    private void handleCreateWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /shard createworld <worldname>");
            return;
        }

        String worldName = args[1];

        // Check if world already exists
        if (Bukkit.getWorld(worldName) != null) {
            sender.sendMessage("§eWorld '" + worldName + "' already exists on this server.");
            return;
        }

        sender.sendMessage("§6Creating world '" + worldName + "' on this server...");

        // Load config to get seed
        File configFile = new File(plugin.getDataFolder(), "shards.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(configFile);

        long seed = yml.getLong("sharding.worlds." + worldName + ".seed", System.currentTimeMillis());

        try {
            // Create world with seed
            WorldCreator creator = new WorldCreator(worldName);
            creator.seed(seed);
            creator.type(WorldType.NORMAL);
            creator.generateStructures(true);

            sender.sendMessage("§aGenerating world with seed: " + seed);
            sender.sendMessage("§eThis may take a minute...");

            // Create world (synchronously)
            World world = Bukkit.createWorld(creator);

            if (world != null) {
                sender.sendMessage("§a✓ World '" + worldName + "' created successfully!");
                sender.sendMessage("§aSpawn location: " + world.getSpawnLocation());
                sender.sendMessage("");
                sender.sendMessage("§6Next steps:");
                sender.sendMessage("§e1. §fRepeat §b/shard createworld " + worldName + " §fon all other shard servers");
                sender.sendMessage("§e2. §fRestart all servers to activate sharding");
            } else {
                sender.sendMessage("§cFailed to create world! Check console for errors.");
            }

        } catch (Exception e) {
            sender.sendMessage("§cError creating world: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void handleTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName();

        if (args.length > 1) {
            worldName = args[1];
        }

        ShardConfig.WorldConfig worldConfig = config.getWorld(worldName);

        if (worldConfig == null || !worldConfig.enabled) {
            sender.sendMessage("§cWorld '" + worldName + "' is not configured for sharding!");
            return;
        }

        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        String targetShard = shardManager.getTargetShardAtBorder(worldName, x, z, 0);
        String currentShard = shardManager.getCurrentShardId();

        sender.sendMessage("§6§l=== Shard Test ===");
        sender.sendMessage("§eWorld: §f" + worldName);
        sender.sendMessage("§ePosition: §fx=" + (int)x + " z=" + (int)z);
        sender.sendMessage("§eCurrent Server: §b" + currentShard);
        sender.sendMessage("§eShould Be On: §b" + (targetShard != null ? targetShard : currentShard));
        sender.sendMessage("");

        if (targetShard != null && !targetShard.equals(currentShard)) {
            sender.sendMessage("§c⚠ You are on the WRONG shard!");
            sender.sendMessage("§eYou should be transferred to: §b" + targetShard);

            // Check if in buffer zone
            String nearBorder = shardManager.getTargetShardAtBorder(
                    worldName, x, z, worldConfig.transferBuffer
            );

            if (nearBorder != null) {
                sender.sendMessage("§6You are in the transfer buffer zone.");
                sender.sendMessage("§6Walk forward to trigger automatic transfer.");
            }
        } else {
            sender.sendMessage("§a✓ You are on the correct shard!");
        }
    }

    /**
     * /shard info - Show current shard information
     */
    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6§l=== Shard Information ===");
        sender.sendMessage("§eCurrent Shard: §b" + shardManager.getCurrentShardId());
        sender.sendMessage("§eSharding Enabled: §b" + config.isEnabled());
        sender.sendMessage("§eProxy Type: §b" + config.getProxyType());
        sender.sendMessage("");
        sender.sendMessage("§6Configured Worlds:");

        boolean hasConfiguredWorlds = false;

        for (World world : Bukkit.getWorlds()) {
            ShardConfig.WorldConfig wc = config.getWorld(world.getName());
            if (wc != null && wc.enabled) {
                hasConfiguredWorlds = true;
                sender.sendMessage("§e  " + world.getName() + ":");
                sender.sendMessage("§7    Mode: §f" + wc.mode);
                sender.sendMessage("§7    Size: §f" + wc.shardSize + " blocks");
                sender.sendMessage("§7    Buffer: §f" + wc.transferBuffer + " blocks");
                sender.sendMessage("§7    Safe Zone: §f" + wc.safeZone + " blocks");
            }
        }

        if (!hasConfiguredWorlds) {
            sender.sendMessage("§7  No worlds configured yet");
            sender.sendMessage("§7  Use §e/shard create§7 to set up sharding");
        }
    }

    /**
     * /shard reload - Reload configuration
     */
    private void handleReload(CommandSender sender) {
        sender.sendMessage("§6Reloading shard configuration...");
        try {
            plugin.reloadConfig();
            sender.sendMessage("§aConfiguration reloaded successfully!");
            sender.sendMessage("§eNote: Restart required for some changes to take effect.");
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload: " + e.getMessage());
        }
    }

    /**
     * /shard list - List all configured shards
     */
    private void handleList(CommandSender sender) {
        sender.sendMessage("§6§l=== Configured Shards ===");

        boolean found = false;
        for (World world : Bukkit.getWorlds()) {
            ShardConfig.WorldConfig wc = config.getWorld(world.getName());
            if (wc != null && wc.enabled) {
                found = true;
                sender.sendMessage("§e" + world.getName() + " §7(" + wc.mode + ")");
            }
        }

        if (!found) {
            sender.sendMessage("§7No sharded worlds configured");
        }
    }

    /**
     * /shard setup - Interactive setup wizard
     */
    private void handleSetup(CommandSender sender, String[] args) {
        sender.sendMessage("§6§l=== Shard Setup Wizard ===");
        sender.sendMessage("");
        sender.sendMessage("§eThis will guide you through setting up world sharding.");
        sender.sendMessage("");
        sender.sendMessage("§6Step 1: Choose a mode");
        sender.sendMessage("§e  GRID §7- Square grid shards (recommended for most servers)");
        sender.sendMessage("§e  RADIAL §7- Concentric rings from spawn (good for resource worlds)");
        sender.sendMessage("");
        sender.sendMessage("§6Step 2: Decide shard size");
        sender.sendMessage("§e  10000 §7- Standard (10k blocks per shard)");
        sender.sendMessage("§e  5000 §7- Smaller (more servers needed)");
        sender.sendMessage("§e  20000 §7- Larger (fewer servers needed)");
        sender.sendMessage("");
        sender.sendMessage("§6Step 3: Calculate number of shards");
        sender.sendMessage("§e  4 shards §7- 2x2 grid (minimum)");
        sender.sendMessage("§e  9 shards §7- 3x3 grid");
        sender.sendMessage("§e  16 shards §7- 4x4 grid");
        sender.sendMessage("");
        sender.sendMessage("§aRun: §b/shard create <worldname> <mode> <size> <shards>");
        sender.sendMessage("§aExample: §b/shard create world GRID 10000 4");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== Shard Commands ===");
        sender.sendMessage("§e/shard create <world> <mode> <size> <shards> §7- Configure sharding");
        sender.sendMessage("§e/shard createworld <world> §7- Create world on this server");
        sender.sendMessage("§e/shard test [world] §7- Test if you're on correct shard");
        sender.sendMessage("§e/shard info §7- Show current shard info");
        sender.sendMessage("§e/shard list §7- List all configured shards");
        sender.sendMessage("§e/shard setup §7- Interactive setup guide");
        sender.sendMessage("§e/shard reload §7- Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("oreo.shard.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterMatching(args[0], Arrays.asList(
                    "create", "createworld", "test", "info", "list", "setup", "reload"
            ));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            // Suggest world names
            return List.of("world", "survival", "resource", "pvp");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("createworld") || args[0].equalsIgnoreCase("test"))) {
            // Suggest existing world names
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return List.of("GRID", "RADIAL");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return List.of("10000", "5000", "20000");
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("create")) {
            return List.of("4", "9", "16");
        }

        return List.of();
    }

    private List<String> filterMatching(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .toList();
    }
}