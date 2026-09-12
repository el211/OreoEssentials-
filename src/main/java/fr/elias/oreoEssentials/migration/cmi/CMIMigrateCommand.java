package fr.elias.oreoEssentials.migration.cmi;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * /cmimigrate [homes|warps|economy|all]
 *
 * Migrates data from a live CMI instance into OreoEssentials.
 * CMI must be installed and loaded on the same server.
 *
 * Runs asynchronously — safe to use on a live server, though running
 * immediately after reboot (before players join) is recommended.
 *
 * Configuration (config.yml):
 *
 * <pre>
 * cmi-migration:
 *   enabled: false
 *   on-conflict: "skip"   # skip | overwrite
 * </pre>
 */
public class CMIMigrateCommand implements OreoCommand {

    private final OreoEssentials plugin;
    private final HomeDirectory  homeDirectory;

    public CMIMigrateCommand(OreoEssentials plugin, HomeDirectory homeDirectory) {
        this.plugin        = plugin;
        this.homeDirectory = homeDirectory;
    }

    @Override public String       name()        { return "cmimigrate"; }
    @Override public List<String> aliases()     { return List.of("cmiimport", "importcmi"); }
    @Override public String       permission()  { return "oreo.admin.migrate.cmi"; }
    @Override public String       usage()       { return "/cmimigrate [homes|warps|economy|all]"; }
    @Override public boolean      playerOnly()  { return false; }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) return List.of("homes", "warps", "economy", "all");
        return List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {

        // ── Guard: feature must be enabled in config ──────────────────────
        if (!plugin.getConfig().getBoolean("cmi-migration.enabled", false)) {
            sender.sendMessage("§cCMI migration is disabled in config.yml.");
            sender.sendMessage("§7Set §fcmi-migration.enabled: true§7 to use this command.");
            return true;
        }

        // ── Guard: CMI must be loaded ──────────────────────────────────────
        Plugin cmiPlugin = plugin.getServer().getPluginManager().getPlugin("CMI");
        if (cmiPlugin == null || !cmiPlugin.isEnabled()) {
            sender.sendMessage("§c[CMI Import] CMI is not loaded on this server.");
            sender.sendMessage("§7Install and enable CMI before running the migration.");
            return true;
        }

        // ── Parse arguments ────────────────────────────────────────────────
        String target    = args.length > 0 ? args[0].toLowerCase() : "all";
        boolean doHomes  = target.equals("all") || target.equals("homes");
        boolean doWarps  = target.equals("all") || target.equals("warps");
        boolean doEcon   = target.equals("all") || target.equals("economy");

        if (!doHomes && !doWarps && !doEcon) {
            sender.sendMessage("§cUnknown target '§f" + target + "§c'. Use: homes, warps, economy, all");
            return true;
        }

        // ── Config values ──────────────────────────────────────────────────
        String  conflict    = plugin.getConfig().getString("cmi-migration.on-conflict", "skip").toLowerCase();
        boolean skipExisting = !conflict.equals("overwrite");
        String  serverName  = plugin.getConfig().getString("server.name", "server-1");

        sender.sendMessage("§e[CMI Import] Starting migration…");
        sender.sendMessage("§7CMI plugin: §f" + cmiPlugin.getName() + " v" + cmiPlugin.getDescription().getVersion());
        sender.sendMessage("§7Conflict:   §f" + conflict);

        final boolean fHomes = doHomes, fWarps = doWarps, fEcon = doEcon;
        final boolean fSkip  = skipExisting;
        final Plugin  fCmi   = cmiPlugin;

        OreScheduler.runAsync(plugin, () -> {
            int homesCount = 0, warpsCount = 0, econCount = 0;

            try {
                if (fHomes) {
                    sender.sendMessage("§7Importing homes…");
                    CMIHomeImporter homeImporter = new CMIHomeImporter(
                            plugin.getStorage(), homeDirectory, serverName,
                            plugin.getLogger(), fSkip);
                    homesCount = homeImporter.importHomes(fCmi);
                    sender.sendMessage("§a  Homes:   §e" + homesCount + " imported.");
                }

                if (fWarps) {
                    sender.sendMessage("§7Importing warps…");
                    CMIWarpImporter warpImporter = new CMIWarpImporter(
                            plugin.getStorage(), plugin.getWarpDirectory(),
                            serverName, plugin.getLogger(), fSkip);
                    warpsCount = warpImporter.importWarps(fCmi);
                    sender.sendMessage("§a  Warps:   §e" + warpsCount + " imported.");
                }

                if (fEcon) {
                    sender.sendMessage("§7Importing economy…");
                    CMIEconomyImporter econImporter = new CMIEconomyImporter(
                            plugin.getDatabase(), plugin.getLogger(), fSkip);
                    econCount = econImporter.importEconomy(fCmi);
                    sender.sendMessage("§a  Economy: §e" + econCount + " imported.");
                }

                sender.sendMessage("§a[CMI Import] Complete! "
                        + "Homes=" + homesCount
                        + " Warps=" + warpsCount
                        + " Economy=" + econCount);

            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage("§c[CMI Import] Failed — check console for details.");
                sender.sendMessage("§c" + ex.getMessage());
            }
        });

        return true;
    }
}
