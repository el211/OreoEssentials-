package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modgui.menu.WorldGamerulesMenu;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.world.VoidChunkGenerator;
import fr.elias.oreoEssentials.world.WorldPreGenerator;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * /oeworld — world creation and management command.
 *
 * Usage:
 *   /oeworld create <name> <normal|nether|end|void> <border> <true|false>
 *   /oeworld cancel <worldname>
 *
 * Permission: oreo.oeworld
 *
 * Persistence: created worlds are tracked in plugins/OreoEssentials/worlds.yml
 * and auto-loaded on server startup via loadCustomWorlds().
 */
public final class OeWorldCommand implements OreoCommand, TabCompleter {

    private static final List<String> TYPES        = List.of("normal", "nether", "end", "void");
    private static final List<String> BORDER_HINTS = List.of("1000", "2000", "5000", "10000", "20000");
    private static final List<String> SUB_COMMANDS = List.of("create", "import", "cancel", "gamerule");

    private final OreoEssentials plugin;
    private final File            worldsFile;

    /** Tracks active pre-generations so they can be cancelled. Key = world name (lower-case). */
    private final Map<String, WorldPreGenerator> activePregens = new ConcurrentHashMap<>();

    public OeWorldCommand(OreoEssentials plugin) {
        this.plugin     = plugin;
        this.worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
    }

    @Override public String       name()       { return "oeworld"; }
    @Override public List<String> aliases()    { return List.of("worldmanager"); }
    @Override public String       permission() { return "oreo.oeworld"; }
    @Override public String       usage()      { return "create|import|cancel ..."; }
    @Override public boolean      playerOnly() { return false; }

    // -----------------------------------------------------------------------
    // Startup: load worlds that were created in previous sessions
    // -----------------------------------------------------------------------

    /**
     * Called from OreoEssentials.onEnable() before initCommands().
     * Reads worlds.yml and loads any worlds not already present in Bukkit.
     */
    public static void loadCustomWorlds(OreoEssentials plugin) {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        if (!file.exists()) return;

        Logger log = plugin.getLogger();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> worlds = cfg.getMapList("worlds");

        for (Map<?, ?> entry : worlds) {
            String name   = String.valueOf(entry.get("name"));
            Object typeObj = entry.get("type");
            String type   = typeObj != null ? String.valueOf(typeObj) : "normal";
            Object bObj   = entry.get("border");
            int    border = bObj != null ? Integer.parseInt(String.valueOf(bObj)) : 0;

            if (Bukkit.getWorld(name) != null) continue; // already loaded

            File worldFolder = new File(Bukkit.getWorldContainer(), name);
            if (!worldFolder.exists()) {
                log.warning("[OeWorld] World folder for '" + name + "' not found — skipping auto-load.");
                continue;
            }

            World world = buildWorld(plugin, name, type);
            if (world == null) {
                log.warning("[OeWorld] Failed to auto-load world '" + name + "'.");
                continue;
            }

            if (border > 0 && border <= 59_999_968) {
                var wb = world.getWorldBorder();
                wb.setCenter(world.getSpawnLocation());
                wb.setSize(border);
            }

            log.info("[OeWorld] Auto-loaded world '" + name + "' (type=" + type + ").");
        }
    }

    // -----------------------------------------------------------------------
    // execute
    // -----------------------------------------------------------------------

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create"   -> handleCreate(sender, args);
            case "import"   -> handleImport(sender, args);
            case "cancel"   -> handleCancel(sender, args);
            case "gamerule" -> handleGamerule(sender, args);
            default         -> sendHelp(sender);
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // /oeworld create <name> <type> <border> <true|false>
    // -----------------------------------------------------------------------

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            Lang.send(sender, "world.create.usage",
                    "<yellow>Usage: /oeworld create <name> <normal|nether|end|void> <border> <true|false></yellow>");
            return;
        }

        String worldName = args[1];
        String typeStr   = args[2].toLowerCase(Locale.ROOT);
        int    border;
        boolean pregenerate;

        // ── Validate world name ────────────────────────────────────────────
        if (!worldName.matches("[a-zA-Z0-9_\\-]+")) {
            Lang.send(sender, "world.create.invalid-name",
                    "<red>Invalid world name. Only letters, digits, _ and - are allowed.</red>");
            return;
        }

        // ── Validate type ──────────────────────────────────────────────────
        if (!TYPES.contains(typeStr)) {
            Lang.send(sender, "world.create.invalid-type",
                    "<red>Invalid type. Choose: normal, nether, end, void.</red>");
            return;
        }

        // ── Validate border ────────────────────────────────────────────────
        try {
            border = Integer.parseInt(args[3]);
            if (border < 64 || border > 59_999_968) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Lang.send(sender, "world.create.invalid-border",
                    "<red>Invalid border. Must be an integer between 64 and 59999968.</red>");
            return;
        }

        // ── Validate pregenerate flag ──────────────────────────────────────
        String pregenStr = args[4].toLowerCase(Locale.ROOT);
        if (!pregenStr.equals("true") && !pregenStr.equals("false")) {
            Lang.send(sender, "world.create.invalid-pregen",
                    "<red>Invalid pregenerate value. Use true or false.</red>");
            return;
        }
        pregenerate = pregenStr.equals("true");

        // ── Check world doesn't already exist ─────────────────────────────
        if (Bukkit.getWorld(worldName) != null) {
            Lang.send(sender, "world.create.already-loaded",
                    "<red>World <white>%world%</white> is already loaded on this server.</red>",
                    Map.of("world", worldName));
            return;
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            Lang.send(sender, "world.create.folder-exists",
                    "<red>A folder named <white>%world%</white> already exists in the server directory.</red>",
                    Map.of("world", worldName));
            return;
        }

        Lang.send(sender, "world.create.creating",
                "<aqua>Creating world <white>%world%</white> (type: <yellow>%type%</yellow>, "
                + "border: <yellow>%border%</yellow> blocks)...</aqua>",
                Map.of("world", worldName, "type", typeStr, "border", String.valueOf(border)));

        final int     finalBorder = border;
        final boolean finalPregen = pregenerate;
        final String  finalType   = typeStr;

        OreScheduler.run(plugin, () -> {
            World world = buildWorld(plugin, worldName, finalType);
            if (world == null) {
                Lang.send(sender, "world.create.failed",
                        "<red>Failed to create world <white>%world%</white>. Check the server console.</red>",
                        Map.of("world", worldName));
                return;
            }

            // Apply world border
            var wb = world.getWorldBorder();
            wb.setCenter(world.getSpawnLocation());
            wb.setSize(finalBorder);

            // Persist so the world auto-loads on restart
            saveWorldEntry(worldName, finalType, finalBorder);

            Lang.send(sender, "world.create.created",
                    "<green>World <white>%world%</white> created! "
                    + "Border: <yellow>%border%</yellow> blocks.</green>",
                    Map.of("world", worldName, "border", String.valueOf(finalBorder)));

            if (finalPregen) {
                WorldPreGenerator pg = new WorldPreGenerator(plugin, world, finalBorder, sender);
                activePregens.put(worldName.toLowerCase(Locale.ROOT), pg);
                pg.start();
            }
        });
    }

    // -----------------------------------------------------------------------
    // /oeworld import <worldname> [normal|nether|end|void] [border]
    // -----------------------------------------------------------------------

    private void handleImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Lang.send(sender, "world.import.usage",
                    "<yellow>Usage: /oeworld import <worldname> [normal|nether|end|void] [border]</yellow>");
            return;
        }

        String worldName = args[1];

        // ── Validate name ──────────────────────────────────────────────────
        if (!worldName.matches("[a-zA-Z0-9_\\-]+")) {
            Lang.send(sender, "world.create.invalid-name",
                    "<red>Invalid world name. Only letters, digits, _ and - are allowed.</red>");
            return;
        }

        // ── Resolve type (optional — defaults to auto) ─────────────────────
        // "auto" = let Bukkit read the environment from level.dat
        String typeStr = "auto";
        if (args.length >= 3) {
            typeStr = args[2].toLowerCase(Locale.ROOT);
            if (!TYPES.contains(typeStr) && !typeStr.equals("auto")) {
                Lang.send(sender, "world.create.invalid-type",
                        "<red>Invalid type. Choose: normal, nether, end, void.</red>");
                return;
            }
        }

        // ── Resolve optional border ────────────────────────────────────────
        int border = 0; // 0 = keep existing / don't set
        if (args.length >= 4) {
            try {
                border = Integer.parseInt(args[3]);
                if (border < 64 || border > 59_999_968) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Lang.send(sender, "world.create.invalid-border",
                        "<red>Invalid border. Must be an integer between 64 and 59999968.</red>");
                return;
            }
        }

        // ── Check world folder exists on disk ──────────────────────────────
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists() || !worldFolder.isDirectory()) {
            Lang.send(sender, "world.import.no-folder",
                    "<red>No world folder named <white>%world%</white> found in the server directory.</red>",
                    Map.of("world", worldName));
            return;
        }

        // ── Check for level.dat (valid world folder) ───────────────────────
        if (!new File(worldFolder, "level.dat").exists()) {
            Lang.send(sender, "world.import.invalid-folder",
                    "<red><white>%world%</white> exists but doesn't look like a valid world (no level.dat).</red>",
                    Map.of("world", worldName));
            return;
        }

        // ── Already loaded? Just register in worlds.yml ────────────────────
        World already = Bukkit.getWorld(worldName);
        if (already != null) {
            String detectedType = resolveType(already, typeStr);
            double alreadySize = already.getWorldBorder().getSize();
            saveWorldEntry(worldName, detectedType, border > 0 ? border
                    : (alreadySize < 59_999_968 ? (int) alreadySize : 0));
            if (border > 0) {
                already.getWorldBorder().setCenter(already.getSpawnLocation());
                already.getWorldBorder().setSize(border);
            }
            Lang.send(sender, "world.import.already-loaded",
                    "<green>World <white>%world%</white> is already loaded — registered in worlds.yml for auto-load on restart.</green>",
                    Map.of("world", worldName));
            return;
        }

        // ── Already registered in worlds.yml? ─────────────────────────────
        if (isRegistered(worldName)) {
            Lang.send(sender, "world.import.already-registered",
                    "<yellow>World <white>%world%</white> is already registered in worlds.yml.</yellow>",
                    Map.of("world", worldName));
            return;
        }

        Lang.send(sender, "world.import.loading",
                "<aqua>Importing world <white>%world%</white>...</aqua>",
                Map.of("world", worldName));

        final String  finalType   = typeStr;
        final int     finalBorder = border;

        OreScheduler.run(plugin, () -> {
            World world = buildWorld(plugin, worldName, finalType);
            if (world == null) {
                Lang.send(sender, "world.import.failed",
                        "<red>Failed to import world <white>%world%</white>. Check the server console.</red>",
                        Map.of("world", worldName));
                return;
            }

            String detectedType = resolveType(world, finalType);

            if (finalBorder > 0) {
                world.getWorldBorder().setCenter(world.getSpawnLocation());
                world.getWorldBorder().setSize(finalBorder);
            }

            // Default Minecraft world border is 6.0E7 (above Bukkit's 59999968 cap) — save 0 in that case
            double existingSize = world.getWorldBorder().getSize();
            int savedBorder = finalBorder > 0 ? finalBorder
                    : (existingSize < 59_999_968 ? (int) existingSize : 0);
            saveWorldEntry(worldName, detectedType, savedBorder);

            Lang.send(sender, "world.import.done",
                    "<green>World <white>%world%</white> imported successfully! (type: <yellow>%type%</yellow>)</green>",
                    Map.of("world", worldName, "type", detectedType));
        });
    }

    /** Resolves the effective type string from a loaded world (used when type was "auto"). */
    private static String resolveType(World world, String hint) {
        if (!hint.equals("auto")) return hint;
        return switch (world.getEnvironment()) {
            case NETHER  -> "nether";
            case THE_END -> "end";
            default      -> "normal";
        };
    }

    /** Returns true if worldName is already tracked in worlds.yml. */
    private boolean isRegistered(String worldName) {
        if (!worldsFile.exists()) return false;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(worldsFile);
        return cfg.getMapList("worlds").stream()
                .anyMatch(e -> worldName.equalsIgnoreCase(String.valueOf(e.get("name"))));
    }

    // -----------------------------------------------------------------------
    // /oeworld gamerule <worldname>
    // -----------------------------------------------------------------------

    private void handleGamerule(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            Lang.send(sender, "world.gamerule.player-only",
                    "<red>This command can only be used by players.</red>");
            return;
        }
        if (args.length < 2) {
            Lang.send(p, "world.gamerule.usage",
                    "<yellow>Usage: /oeworld gamerule <worldname></yellow>");
            return;
        }

        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            Lang.send(p, "world.gamerule.not-found",
                    "<red>World <white>%world%</white> is not loaded.</red>",
                    Map.of("world", args[1]));
            return;
        }

        var modGui = plugin.getModGuiService();
        if (modGui == null) {
            Lang.send(p, "world.gamerule.modgui-unavailable",
                    "<red>Mod GUI is not available on this server.</red>");
            return;
        }

        SmartInventory.builder()
                .manager(plugin.getInvManager())
                .provider(new WorldGamerulesMenu(plugin, modGui, world))
                .title(Lang.color("&8Gamerules: " + world.getName()))
                .size(6, 9)
                .build()
                .open(p);
    }

    // -----------------------------------------------------------------------
    // /oeworld cancel <worldname>
    // -----------------------------------------------------------------------

    private void handleCancel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Lang.send(sender, "world.cancel.usage",
                    "<yellow>Usage: /oeworld cancel <worldname></yellow>");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        WorldPreGenerator pg = activePregens.remove(key);
        if (pg == null) {
            Lang.send(sender, "world.cancel.not-running",
                    "<red>No active pre-generation found for world <white>%world%</white>.</red>",
                    Map.of("world", args[1]));
            return;
        }
        pg.cancel();
        Lang.send(sender, "world.cancel.cancelled",
                "<yellow>Pre-generation for <white>%world%</white> has been cancelled.</yellow>",
                Map.of("world", args[1]));
    }

    // -----------------------------------------------------------------------
    // World builder (shared with static loadCustomWorlds)
    // -----------------------------------------------------------------------

    private static World buildWorld(OreoEssentials plugin, String name, String type) {
        try {
            WorldCreator creator = new WorldCreator(name);
            switch (type) {
                case "normal" -> creator.environment(World.Environment.NORMAL);
                case "nether" -> creator.environment(World.Environment.NETHER);
                case "end"    -> creator.environment(World.Environment.THE_END);
                case "void"   -> {
                    creator.environment(World.Environment.NORMAL);
                    creator.generator(new VoidChunkGenerator());
                }
            }
            World world = creator.createWorld();
            if (world != null && type.equals("void")) {
                // Place the spawn on top of the bedrock platform at (0, SPAWN_Y, 0)
                world.setSpawnLocation(0, VoidChunkGenerator.SPAWN_Y + 1, 0);
            }
            return world;
        } catch (Throwable t) {
            plugin.getLogger().severe("[OeWorld] Failed to create/load world '" + name + "': " + t.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Persistence helpers
    // -----------------------------------------------------------------------

    /** Appends a new world entry to worlds.yml. */
    private void saveWorldEntry(String name, String type, int border) {
        YamlConfiguration cfg = worldsFile.exists()
                ? YamlConfiguration.loadConfiguration(worldsFile)
                : new YamlConfiguration();

        List<Map<?, ?>> list = new ArrayList<>(cfg.getMapList("worlds"));

        // Avoid duplicates (shouldn't happen, but be safe)
        list.removeIf(e -> name.equalsIgnoreCase(String.valueOf(e.get("name"))));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name",   name);
        entry.put("type",   type);
        entry.put("border", border);
        list.add(entry);

        cfg.set("worlds", list);
        try {
            if (!worldsFile.exists()) {
                worldsFile.getParentFile().mkdirs();
            }
            cfg.save(worldsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[OeWorld] Failed to save worlds.yml: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Help
    // -----------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        Lang.sendRaw(sender,
                "<gold>--- OeWorld ---</gold>\n"
                + "<yellow>/oeworld create <name> <normal|nether|end|void> <border> <true|false>\n"
                + "  <gray>Creates and optionally pre-generates a new world.</gray>\n"
                + "<yellow>/oeworld import <worldname> [normal|nether|end|void] [border]\n"
                + "  <gray>Loads an existing world folder and registers it for auto-load on restart.</gray>\n"
                + "<yellow>/oeworld cancel <worldname>\n"
                + "  <gray>Cancels an active pre-generation.</gray>\n"
                + "<yellow>/oeworld gamerule <worldname>\n"
                + "  <gray>Opens the gamerule GUI for the specified world.</gray>");
    }

    // -----------------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------------

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission("oreo.oeworld")) return Collections.emptyList();

        if (args.length == 1) return filter(SUB_COMMANDS, args[0]);

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("import")) {
            return switch (args.length) {
                case 2 -> {
                    // Suggest unloaded world folders that exist on disk
                    List<String> folders = getUnloadedWorldFolders();
                    if (args[1].isEmpty() || folders.isEmpty()) {
                        List<String> hints = new ArrayList<>(filter(folders, args[1]));
                        if (hints.isEmpty()) hints.add("<worldname>");
                        yield hints;
                    }
                    yield filter(folders, args[1]);
                }
                case 3 -> {
                    List<String> types = filter(TYPES, args[2]);
                    if (types.isEmpty()) yield List.of("<normal|nether|end|void>");
                    yield types;
                }
                case 4 -> {
                    List<String> borders = filter(BORDER_HINTS, args[3]);
                    yield borders.isEmpty() ? List.of("<border_in_blocks>") : borders;
                }
                default -> Collections.emptyList();
            };
        }

        if (sub.equals("cancel")) {
            if (args.length == 2) {
                List<String> running = new ArrayList<>(activePregens.keySet());
                // If nothing is running, show a hint
                if (running.isEmpty()) return List.of("<worldname>");
                return filter(running, args[1]);
            }
            return Collections.emptyList();
        }

        if (sub.equals("gamerule")) {
            if (args.length == 2) {
                List<String> loaded = Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .collect(Collectors.toList());
                List<String> matches = filter(loaded, args[1]);
                return matches.isEmpty() ? List.of("<worldname>") : matches;
            }
            return Collections.emptyList();
        }

        if (sub.equals("create")) {
            return switch (args.length) {
                case 2 -> {
                    // Show placeholder hint when field is empty, otherwise let them type freely
                    if (args[1].isEmpty()) yield List.of("<worldname>");
                    yield Collections.emptyList();
                }
                case 3 -> {
                    List<String> types = filter(TYPES, args[2]);
                    if (types.isEmpty() && args[2].isEmpty()) yield List.of("<normal|nether|end|void>");
                    yield types;
                }
                case 4 -> {
                    List<String> borders = filter(BORDER_HINTS, args[3]);
                    if (borders.isEmpty() && args[3].isEmpty()) yield List.of("<border_in_blocks>");
                    yield borders.isEmpty() ? List.of("<border_in_blocks>") : borders;
                }
                case 5 -> {
                    List<String> flags = filter(List.of("true", "false"), args[4]);
                    if (flags.isEmpty() && args[4].isEmpty()) yield List.of("<true|false>");
                    yield flags;
                }
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(sender, alias, args);
    }

    /**
     * Returns names of folders in the server's world container that look like
     * valid worlds (have a level.dat) but are not currently loaded in Bukkit.
     */
    private static List<String> getUnloadedWorldFolders() {
        File container = Bukkit.getWorldContainer();
        File[] dirs = container.listFiles(f ->
                f.isDirectory()
                && new File(f, "level.dat").exists()
                && Bukkit.getWorld(f.getName()) == null);
        if (dirs == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (File d : dirs) names.add(d.getName());
        return names;
    }

    private static List<String> filter(List<String> pool, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return pool.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }
}
