package fr.elias.oreoEssentials.modules.economy.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.db.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.modules.economy.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EcoRecoverCommand implements OreoCommand {
    private final OreoEssentials plugin;

    public EcoRecoverCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "ecorecover";
    }

    @Override
    public List<String> aliases() {
        return List.of("recovereconomy", "ecofix");
    }

    @Override
    public String permission() {
        return "oreo.economy.recover";
    }

    @Override
    public String usage() {
        return "[source] - Auto-detects or specify: yaml, json, mongodb";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== Economy Recovery Tool ===");
        sender.sendMessage(ChatColor.YELLOW + "Searching for lost economy data...");

        // Get current economy type
        String currentType = plugin.getConfig().getString("economy.type", "yaml").toLowerCase();
        sender.sendMessage(ChatColor.GRAY + "Current economy type: " + ChatColor.WHITE + currentType);

        // Search for data files
        List<String> foundSources = new ArrayList<>();

        File yamlFile = new File(plugin.getDataFolder(), "balances.yml");
        File jsonFile = new File(plugin.getDataFolder(), "balances.json");
        boolean hasMongo = plugin.getDatabase() != null;

        if (yamlFile.exists()) {
            foundSources.add("yaml");
            sender.sendMessage(ChatColor.GREEN + "✓ Found balances.yml");
        }
        if (jsonFile.exists()) {
            foundSources.add("json");
            sender.sendMessage(ChatColor.GREEN + "✓ Found balances.json");
        }
        if (hasMongo) {
            foundSources.add("mongodb");
            sender.sendMessage(ChatColor.GREEN + "✓ Found MongoDB connection");
        }

        if (foundSources.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No economy data files found!");
            sender.sendMessage(ChatColor.GRAY + "Checked for: balances.yml, balances.json, MongoDB");
            return true;
        }

        // If user specified a source
        String sourceType = null;
        if (args.length > 0) {
            sourceType = args[0].toLowerCase();
            if (!foundSources.contains(sourceType)) {
                sender.sendMessage(ChatColor.RED + "Source '" + sourceType + "' not found!");
                sender.sendMessage(ChatColor.YELLOW + "Available sources: " + String.join(", ", foundSources));
                return true;
            }
        } else {
            // Auto-detect: use the first non-current type found
            for (String source : foundSources) {
                if (!source.equals(currentType)) {
                    sourceType = source;
                    break;
                }
            }

            // If all sources are the current type, use the current type
            if (sourceType == null && !foundSources.isEmpty()) {
                sourceType = foundSources.get(0);
            }
        }

        if (sourceType == null) {
            sender.sendMessage(ChatColor.RED + "Could not determine source!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Attempting to recover from: " + ChatColor.WHITE + sourceType);

        try {
            // Load source service
            EconomyService sourceService = createService(sourceType);
            if (sourceService == null) {
                sender.sendMessage(ChatColor.RED + "Failed to load source service: " + sourceType);
                return true;
            }

            // Check if there's data
            List<EconomyService.TopEntry> balances = sourceService.topBalances(Integer.MAX_VALUE);

            if (balances.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No balances found in " + sourceType);
                return true;
            }

            sender.sendMessage(ChatColor.GREEN + "Found " + balances.size() + " balances!");

            // Show preview
            sender.sendMessage(ChatColor.GRAY + "Preview (top 5):");
            int count = 0;
            for (EconomyService.TopEntry entry : balances) {
                if (count >= 5) break;
                sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + entry.name() +
                        ChatColor.GRAY + ": " + ChatColor.GOLD + entry.balance());
                count++;
            }
            if (balances.size() > 5) {
                sender.sendMessage(ChatColor.GRAY + "  ... and " + (balances.size() - 5) + " more");
            }

            // If source is same as current, just show info
            if (sourceType.equals(currentType)) {
                sender.sendMessage(ChatColor.GREEN + "Your economy is already using this data!");
                sender.sendMessage(ChatColor.GRAY + "No migration needed.");
                return true;
            }

            // Ask for confirmation
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "To migrate this data to your current economy (" + currentType + "), run:");
            sender.sendMessage(ChatColor.WHITE + "/ecomigrate " + sourceType + " " + currentType);
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Or to preview what will be migrated, run:");
            sender.sendMessage(ChatColor.WHITE + "/ecorecover " + sourceType);

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Recovery error: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private EconomyService createService(String type) {
        try {
            switch (type.toLowerCase()) {
                case "yaml":
                    return new YamlEconomyService(plugin);
                case "json":
                    return new JsonEconomyService(plugin);
                case "mongodb":
                    PlayerEconomyDatabase db = plugin.getDatabase();
                    if (db != null) {
                        return new MongoEconomyService(db);
                    }
                    return null;
                default:
                    return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy Recovery] Failed to create " + type + " service: " + e.getMessage());
            return null;
        }
    }
}