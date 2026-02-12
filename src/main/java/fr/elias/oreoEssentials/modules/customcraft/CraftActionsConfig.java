package fr.elias.oreoEssentials.modules.customcraft;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class CraftActionsConfig {
    private final Plugin plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<Material, CraftAction> vanillaActions = new HashMap<>();
    private final Map<String, CraftAction> customRecipeActions = new HashMap<>();

    public CraftActionsConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "craft-actions.yml");
        if (!file.exists()) {
            createDefault();
        }
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        vanillaActions.clear();
        customRecipeActions.clear();
        loadActions();
        plugin.getLogger().info("[CraftActions] Loaded " +
                (vanillaActions.size() + customRecipeActions.size()) + " craft actions");
    }

    private void loadActions() {
        ConfigurationSection actionsSection = config.getConfigurationSection("actions");
        if (actionsSection == null) return;
        for (String key : actionsSection.getKeys(false)) {
            ConfigurationSection section = actionsSection.getConfigurationSection(key);
            if (section == null) continue;
            List<String> commands = section.getStringList("commands");
            String message = section.getString("message", null);
            CraftAction action = new CraftAction(commands, message);
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                vanillaActions.put(mat, action);
            } catch (IllegalArgumentException e) {
                customRecipeActions.put(key.toLowerCase(), action);
            }
        }
    }


    public CraftAction getAction(Material material) {
        return vanillaActions.get(material);
    }


    public CraftAction getActionForRecipe(String recipeName) {
        return customRecipeActions.get(recipeName.toLowerCase());
    }

    private void createDefault() {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();

            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            cfg.set("actions.DIAMOND_PICKAXE.commands", List.of(
                    "eco give %player% 50",
                    "broadcast &e%player% &acrafted a &bDiamond Pickaxe&a!"
            ));
            cfg.set("actions.DIAMOND_PICKAXE.message",
                    "<green>You earned <gold>$50</gold> for crafting this pickaxe!</green>");

            cfg.set("actions.DIAMOND_SWORD.commands", List.of(
                    "effect give %player% strength 60 1"
            ));
            cfg.set("actions.DIAMOND_SWORD.message",
                    "<aqua>You feel powerful wielding this sword!</aqua>");

            // Example for custom recipe
            cfg.set("actions.example_custom_recipe.commands", List.of(
                    "give %player% diamond 1"
            ));
            cfg.set("actions.example_custom_recipe.message",
                    "<yellow>You crafted a special custom item!</yellow>");

            cfg.options().setHeader(List.of(
                    "Craft Actions Configuration",
                    "",
                    "Define commands and messages to execute when items are crafted.",
                    "",
                    "For VANILLA items, use the Material name (e.g., DIAMOND_PICKAXE)",
                    "For CUSTOM recipes, use the recipe name (e.g., my_custom_sword)",
                    "",
                    "Available placeholders:",
                    "  %player% - Player's name",
                    "  %player_uuid% - Player's UUID",
                    "  %world% - World name",
                    "",
                    "Commands are executed as CONSOLE.",
                    "Messages support MiniMessage format.",
                    "",
                    "Example:",
                    "actions:",
                    "  DIAMOND_PICKAXE:",
                    "    commands:",
                    "      - 'eco give %player% 100'",
                    "      - 'broadcast %player% crafted something special!'",
                    "    message: '<green>You earned $100!</green>'",
                    ""
            ));

            cfg.save(file);
            plugin.getLogger().info("[CraftActions] Created default craft-actions.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("[CraftActions] Failed to create default config: " + e.getMessage());
        }
    }


    public static class CraftAction {
        private final List<String> commands;
        private final String message;

        public CraftAction(List<String> commands, String message) {
            this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
            this.message = message;
        }

        public List<String> getCommands() {
            return new ArrayList<>(commands);
        }

        public String getMessage() {
            return message;
        }

        public boolean hasCommands() {
            return !commands.isEmpty();
        }

        public boolean hasMessage() {
            return message != null && !message.isBlank();
        }
    }
}