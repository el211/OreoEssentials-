package fr.elias.oreoEssentials.migration.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import fr.elias.oreoEssentials.migration.ZEssentialsHomeImporter;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

public class ZEssentialsHomesImportCommand implements fr.elias.oreoEssentials.commands.OreoCommand {

    private final OreoEssentials plugin;
    private final StorageApi storage;
    private final HomeDirectory homeDirectory;

    public ZEssentialsHomesImportCommand(OreoEssentials plugin, StorageApi storage, HomeDirectory homeDirectory) {
        this.plugin = plugin;
        this.storage = storage;
        this.homeDirectory = homeDirectory;
    }

    @Override
    public String name() {
        return "importzhomes";
    }

    @Override
    public List<String> aliases() {
        return List.of("zhomesimport", "zessentialsimport");
    }

    @Override
    public String permission() {
        return "oreo.admin.import";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return false; // console allowed
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {

        if (!plugin.getConfig().getBoolean("zessentials-migration.enabled", false)) {
            sender.sendMessage("§cMigration disabled in config.yml (zessentials-migration.enabled=false)");
            return true;
        }

        String url    = plugin.getConfig().getString("zessentials-migration.jdbc-url");
        String user   = plugin.getConfig().getString("zessentials-migration.user");
        String pass   = plugin.getConfig().getString("zessentials-migration.password");
        String prefix = plugin.getConfig().getString("zessentials-migration.table-prefix", "zessentials_");
        String serverName = plugin.getConfig().getString("server.name", "server-1");

        sender.sendMessage("§eStarting zEssentials -> OreoEssentials home migration…");
        sender.sendMessage("§7Database: §f" + url);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {

                ZEssentialsHomeImporter importer = new ZEssentialsHomeImporter(
                        storage,
                        homeDirectory,
                        serverName,
                        plugin.getLogger(),
                        prefix
                );

                int imported = importer.importHomes(conn);
                sender.sendMessage("§aSuccessfully imported §e" + imported + "§a homes!");

            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage("§cMigration failed. Check console.");
            }
        });

        return true;
    }
}
