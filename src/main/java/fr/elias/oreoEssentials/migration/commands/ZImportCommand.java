package fr.elias.oreoEssentials.migration.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.migration.ZEssentialsEconomyImporter;
import fr.elias.oreoEssentials.migration.ZEssentialsHomeImporter;
import fr.elias.oreoEssentials.migration.ZEssentialsWarpImporter;
import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * /zimport [homes|warps|economy|all]
 *
 * Unified command for importing all supported data types from zEssentials
 * into OreoEssentials. Runs asynchronously.
 *
 * Configuration (config.yml) — same section as the original zessentials-migration:
 *
 *   zessentials-migration:
 *     enabled: true
 *     jdbc-url:      "jdbc:sqlite:plugins/zEssentials/data.db"
 *     user:          ""
 *     password:      ""
 *     table-prefix:  "zessentials_"
 *     economy-name:  "money"         # which economy_name to import
 *     on-conflict:   "skip"          # skip | overwrite
 *     warp-json-path: "plugins/zEssentials/config_storage.json"
 */
public class ZImportCommand implements OreoCommand {

    private final OreoEssentials plugin;
    private final HomeDirectory  homeDirectory;

    public ZImportCommand(OreoEssentials plugin, HomeDirectory homeDirectory) {
        this.plugin        = plugin;
        this.homeDirectory = homeDirectory;
    }

    @Override public String name()          { return "zimport"; }
    @Override public List<String> aliases() { return List.of("zessimport", "importzessentials"); }
    @Override public String permission()    { return "oreo.admin.import"; }
    @Override public String usage()         { return "/zimport [homes|warps|economy|all]"; }
    @Override public boolean playerOnly()   { return false; }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) return List.of("homes", "warps", "economy", "all");
        return List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("zessentials-migration.enabled", false)) {
            sender.sendMessage("§czEssentials migration is disabled in config.yml.");
            sender.sendMessage("§7Set §fzessentials-migration.enabled: true§7 to use this command.");
            return true;
        }

        String url    = plugin.getConfig().getString("zessentials-migration.jdbc-url");
        String user   = plugin.getConfig().getString("zessentials-migration.user",   "");
        String pass   = plugin.getConfig().getString("zessentials-migration.password","");
        String prefix = plugin.getConfig().getString("zessentials-migration.table-prefix","zessentials_");
        String econName = plugin.getConfig().getString("zessentials-migration.economy-name","money");
        String conflict = plugin.getConfig().getString("zessentials-migration.on-conflict","skip").toLowerCase();
        String warpJsonPath = plugin.getConfig().getString(
                "zessentials-migration.warp-json-path",
                "plugins/zEssentials/config_storage.json");

        if (url == null || url.isBlank()) {
            sender.sendMessage("§czessentials-migration.jdbc-url is not set in config.yml.");
            return true;
        }

        boolean skipExisting = !conflict.equals("overwrite");

        // What the user asked for
        String target = args.length > 0 ? args[0].toLowerCase() : "all";
        boolean doHomes   = target.equals("all") || target.equals("homes");
        boolean doWarps   = target.equals("all") || target.equals("warps");
        boolean doEconomy = target.equals("all") || target.equals("economy");

        if (!doHomes && !doWarps && !doEconomy) {
            sender.sendMessage("§cUnknown target '§f" + target + "§c'. Use: homes, warps, economy, all");
            return true;
        }

        final File warpJson = resolveFile(warpJsonPath);
        sender.sendMessage("§e[zEssentials Import] Starting migration…");
        sender.sendMessage("§7Database: §f" + url);
        sender.sendMessage("§7Conflict: §f" + conflict);

        final boolean fHomes = doHomes, fWarps = doWarps, fEconomy = doEconomy;
        final String  fPrefix = prefix, fEconName = econName;
        final boolean fSkip = skipExisting;
        final String  serverName = plugin.getConfig().getString("server.name", "server-1");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = DriverManager.getConnection(url, user.isEmpty() ? null : user,
                                                                    pass.isEmpty() ? null : pass)) {
                int homesCount = 0, warpsCount = 0, econCount = 0;

                if (fHomes) {
                    sender.sendMessage("§7Importing homes…");
                    ZEssentialsHomeImporter h = new ZEssentialsHomeImporter(
                            plugin.getStorage(), homeDirectory, serverName,
                            plugin.getLogger(), fPrefix);
                    homesCount = h.importHomes(conn);
                    sender.sendMessage("§a  Homes:   §e" + homesCount + " imported.");
                }

                if (fWarps) {
                    sender.sendMessage("§7Importing warps…");
                    ZEssentialsWarpImporter w = new ZEssentialsWarpImporter(
                            plugin.getStorage(), plugin.getWarpDirectory(),
                            serverName, plugin.getLogger(),
                            fPrefix, warpJson, fSkip);
                    warpsCount = w.importWarps(conn);
                    sender.sendMessage("§a  Warps:   §e" + warpsCount + " imported.");
                }

                if (fEconomy) {
                    sender.sendMessage("§7Importing economy…");
                    ZEssentialsEconomyImporter e = new ZEssentialsEconomyImporter(
                            plugin.getDatabase(), plugin.getLogger(),
                            fPrefix, fEconName, fSkip);
                    econCount = e.importEconomy(conn);
                    sender.sendMessage("§a  Economy: §e" + econCount + " imported.");
                }

                sender.sendMessage("§a[zEssentials Import] Complete! "
                        + "Homes=" + homesCount
                        + " Warps=" + warpsCount
                        + " Economy=" + econCount);

            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage("§c[zEssentials Import] Failed — check console for details.");
                sender.sendMessage("§c" + ex.getMessage());
            }
        });

        return true;
    }

    /** Resolves a path relative to the server root (two levels up from plugin data folder). */
    private File resolveFile(String path) {
        File f = new File(path);
        if (f.isAbsolute()) return f;
        // server root = plugins/../  (getDataFolder parent = plugins, its parent = server root)
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        return new File(serverRoot, path);
    }
}
