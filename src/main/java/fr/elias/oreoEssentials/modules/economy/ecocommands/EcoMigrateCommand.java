package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.modules.economy.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EcoMigrateCommand implements OreoCommand {
    private final OreoEssentials plugin;

    public EcoMigrateCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "ecomigrate";
    }

    @Override
    public List<String> aliases() {
        return List.of("economymigrate", "migrateeco");
    }

    @Override
    public String permission() {
        return "oreo.economy.migrate";
    }

    @Override
    public String usage() {
        return "<from> <to> - Valid types: yaml, json, mongodb";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + usage());
            sender.sendMessage(ChatColor.GRAY + "Example: /" + label + " yaml json");
            sender.sendMessage(ChatColor.GRAY + "This will migrate all balances from YAML to JSON");
            return true;
        }

        String fromType = args[0].toLowerCase();
        String toType = args[1].toLowerCase();

        if (fromType.equals(toType)) {
            sender.sendMessage(ChatColor.RED + "Source and destination cannot be the same!");
            return true;
        }

        // Create services
        EconomyService fromService = createService(fromType);
        EconomyService toService = createService(toType);

        if (fromService == null) {
            sender.sendMessage(ChatColor.RED + "Invalid source type: " + fromType);
            sender.sendMessage(ChatColor.GRAY + "Valid types: yaml, json, mongodb");
            return true;
        }

        if (toService == null) {
            sender.sendMessage(ChatColor.RED + "Invalid destination type: " + toType);
            sender.sendMessage(ChatColor.GRAY + "Valid types: yaml, json, mongodb");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting economy migration...");
        sender.sendMessage(ChatColor.GRAY + "From: " + ChatColor.WHITE + fromType);
        sender.sendMessage(ChatColor.GRAY + "To: " + ChatColor.WHITE + toType);

        try {
            EconomyMigration migration = new EconomyMigration(plugin);

            // Create backup
            sender.sendMessage(ChatColor.YELLOW + "Creating backup...");
            Map<UUID, Double> backup = migration.backup(fromService);

            if (backup.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No balances found in source!");
                sender.sendMessage(ChatColor.GRAY + "Nothing to migrate.");
                return true;
            }

            sender.sendMessage(ChatColor.GREEN + "Backup created: " + backup.size() + " balances");

            // Migrate
            sender.sendMessage(ChatColor.YELLOW + "Migrating balances...");
            int migrated = migration.migrate(fromService, toService);

            if (migrated > 0) {
                sender.sendMessage(ChatColor.GREEN + "✓ Successfully migrated " + migrated + " balances!");
                sender.sendMessage(ChatColor.GRAY + "Don't forget to update your config.yml:");
                sender.sendMessage(ChatColor.WHITE + "economy:");
                sender.sendMessage(ChatColor.WHITE + "  type: \"" + toType + "\"");
            } else {
                sender.sendMessage(ChatColor.RED + "Migration failed! No balances were transferred.");
                sender.sendMessage(ChatColor.YELLOW + "Check console for errors.");
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Migration error: " + e.getMessage());
            sender.sendMessage(ChatColor.YELLOW + "Check console for full error details.");
            e.printStackTrace();
        }

        return true;
    }

    private EconomyService createService(String type) {
        switch (type.toLowerCase()) {
            case "yaml":
                return new YamlEconomyService(plugin);
            case "json":
                return new JsonEconomyService(plugin);
            case "mongodb":
                PlayerEconomyDatabase db = plugin.getDatabase();
                if (db != null) {
                    return new MongoEconomyService(db);
                } else {
                    return null;
                }
            default:
                return null;
        }
    }
}