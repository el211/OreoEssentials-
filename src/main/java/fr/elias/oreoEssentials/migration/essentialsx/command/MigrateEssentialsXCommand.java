package fr.elias.oreoEssentials.migration.essentialsx.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.migration.essentialsx.EssentialsXMigrator;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.migration.essentialsx.EssentialsXMigrator.ConflictPolicy;
import fr.elias.oreoEssentials.migration.essentialsx.EssentialsXMigrator.Result;
import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.List;

/**
 * /migrateessentials [homes|warps|economy|all]
 *
 * Imports EssentialsX player data (homes, warps, economy balances) into
 * OreoEssentials using whichever storage backend is currently active
 * (YAML, JSON, or MongoDB).
 *
 * Configuration (config.yml):
 *   essentialsx-migration:
 *     enabled: false
 *     data-path: "plugins/Essentials"
 *     import:
 *       homes: true
 *       warps: true
 *       economy: true
 *     on-conflict: "skip"   # skip | overwrite
 */
public class MigrateEssentialsXCommand implements OreoCommand {

    private final OreoEssentials plugin;
    private final HomeDirectory homeDirectory;

    public MigrateEssentialsXCommand(OreoEssentials plugin, HomeDirectory homeDirectory) {
        this.plugin = plugin;
        this.homeDirectory = homeDirectory;
    }

    @Override public String name()        { return "migrateessentials"; }
    @Override public List<String> aliases() { return List.of("importessentials", "esximport"); }
    @Override public String permission()  { return "oreo.admin.import"; }
    @Override public String usage()       { return "/migrateessentials [homes|warps|economy|all]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) return List.of("homes", "warps", "economy", "all");
        return List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("essentialsx-migration.enabled", false)) {
            sender.sendMessage("§cEssentialsX migration is disabled in config.yml.");
            sender.sendMessage("§7Set §fessentialsx-migration.enabled: true§7 to use this command.");
            return true;
        }

        String dataPathStr = plugin.getConfig().getString(
                "essentialsx-migration.data-path", "plugins/Essentials");
        File dataDir = new File(plugin.getDataFolder().getParentFile().getParentFile(), dataPathStr);

        if (!dataDir.isDirectory()) {
            // Also try relative to server root
            dataDir = new File(dataPathStr);
        }

        if (!dataDir.isDirectory()) {
            sender.sendMessage("§cEssentialsX data directory not found: §f" + dataPathStr);
            sender.sendMessage("§7Check §fessentialsx-migration.data-path§7 in config.yml.");
            return true;
        }

        String conflictStr = plugin.getConfig().getString(
                "essentialsx-migration.on-conflict", "skip").toLowerCase();
        ConflictPolicy policy = conflictStr.equals("overwrite")
                ? ConflictPolicy.OVERWRITE
                : ConflictPolicy.SKIP;

        boolean doHomes   = plugin.getConfig().getBoolean("essentialsx-migration.import.homes", true);
        boolean doWarps   = plugin.getConfig().getBoolean("essentialsx-migration.import.warps", true);
        boolean doEconomy = plugin.getConfig().getBoolean("essentialsx-migration.import.economy", true);

        // Determine what the user asked for
        String target = args.length > 0 ? args[0].toLowerCase() : "all";
        switch (target) {
            case "homes"   -> doWarps = doEconomy = false;
            case "warps"   -> doHomes = doEconomy = false;
            case "economy" -> doHomes = doWarps = false;
            case "all"     -> { /* keep config values */ }
            default -> {
                sender.sendMessage("§cUnknown target '§f" + target + "§c'. Use: homes, warps, economy, all");
                return true;
            }
        }

        sender.sendMessage("§e[EssentialsX Import] Starting migration…");
        sender.sendMessage("§7Data path: §f" + dataDir.getAbsolutePath());
        sender.sendMessage("§7Conflict policy: §f" + policy.name().toLowerCase());

        final boolean finalDoHomes   = doHomes;
        final boolean finalDoWarps   = doWarps;
        final boolean finalDoEconomy = doEconomy;
        final File    finalDataDir   = dataDir;

        OreScheduler.runAsync(plugin, () -> {
            EssentialsXMigrator migrator = new EssentialsXMigrator(
                    plugin.getStorage(),
                    plugin.getDatabase(),
                    homeDirectory,
                    plugin.getWarpDirectory(),
                    plugin.getConfig().getString("server.name", "server-1"),
                    plugin.getLogger(),
                    policy,
                    finalDataDir
            );

            Result total = new Result(0, 0, 0);

            if (finalDoHomes) {
                sender.sendMessage("§7Importing homes…");
                Result r = migrator.importHomes();
                sender.sendMessage("§a  Homes:   §eimported=§f" + r.imported()
                        + " §7skipped=§f" + r.skipped() + " §cfailed=§f" + r.failed());
                total = total.add(r);
            }

            if (finalDoWarps) {
                sender.sendMessage("§7Importing warps…");
                Result r = migrator.importWarps();
                sender.sendMessage("§a  Warps:   §eimported=§f" + r.imported()
                        + " §7skipped=§f" + r.skipped() + " §cfailed=§f" + r.failed());
                total = total.add(r);
            }

            if (finalDoEconomy) {
                sender.sendMessage("§7Importing economy…");
                Result r = migrator.importEconomy();
                sender.sendMessage("§a  Economy: §eimported=§f" + r.imported()
                        + " §7skipped=§f" + r.skipped() + " §cfailed=§f" + r.failed());
                total = total.add(r);
            }

            sender.sendMessage("§a[EssentialsX Import] Migration complete! "
                    + "§eTotal: §f" + total.imported() + " imported, "
                    + total.skipped() + " skipped, "
                    + "§c" + total.failed() + "§f failed.");

            if (total.failed() > 0) {
                sender.sendMessage("§cSome entries failed — check the console for details.");
            }
        });

        return true;
    }
}
