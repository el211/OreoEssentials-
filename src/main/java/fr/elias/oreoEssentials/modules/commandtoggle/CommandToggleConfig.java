package fr.elias.oreoEssentials.modules.commandtoggle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class CommandToggleConfig {
    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final File configFile;
    private String disabledMessage;
    private final Map<String, CommandToggleEntry> commands = new HashMap<>();

    public CommandToggleConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "commands-toggle.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("commands-toggle.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.disabledMessage = config.getString("disabled-command-message", "&cThis command is currently disabled.");

        commands.clear();

        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection == null) {
            plugin.getLogger().warning("[CommandToggle] No 'commands' section found in commands-toggle.yml");
            return;
        }

        for (String commandName : commandsSection.getKeys(false)) {
            ConfigurationSection cmdSection = commandsSection.getConfigurationSection(commandName);
            if (cmdSection == null) continue;

            boolean enabled = cmdSection.getBoolean("enabled", true);
            List<String> aliases = cmdSection.getStringList("aliases");

            CommandToggleEntry entry = new CommandToggleEntry(commandName, enabled, aliases);
            commands.put(commandName.toLowerCase(Locale.ROOT), entry);
        }

        plugin.getLogger().info("[CommandToggle] Loaded " + commands.size() + " command toggles from commands-toggle.yml");
    }

    public void reload() {
        load();
    }

    public boolean isCommandEnabled(String commandName) {
        CommandToggleEntry entry = commands.get(commandName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            // If command is not in config, it's enabled by default
            return true;
        }
        return entry.isEnabled();
    }

    public String getDisabledMessage() {
        return disabledMessage;
    }

    public Map<String, CommandToggleEntry> getAllCommands() {
        return new HashMap<>(commands);
    }

    public void setCommandEnabled(String commandName, boolean enabled) {
        CommandToggleEntry entry = commands.get(commandName.toLowerCase(Locale.ROOT));
        if (entry != null) {
            entry.setEnabled(enabled);
            save();
        }
    }

    private void save() {
        try {
            ConfigurationSection commandsSection = config.getConfigurationSection("commands");
            if (commandsSection == null) return;

            for (Map.Entry<String, CommandToggleEntry> entry : commands.entrySet()) {
                String cmdName = entry.getKey();
                CommandToggleEntry toggleEntry = entry.getValue();

                ConfigurationSection cmdSection = commandsSection.getConfigurationSection(cmdName);
                if (cmdSection != null) {
                    cmdSection.set("enabled", toggleEntry.isEnabled());
                }
            }

            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("[CommandToggle] Failed to save commands-toggle.yml: " + e.getMessage());
        }
    }

    public static class CommandToggleEntry {
        private final String name;
        private boolean enabled;
        private final List<String> aliases;

        public CommandToggleEntry(String name, boolean enabled, List<String> aliases) {
            this.name = name;
            this.enabled = enabled;
            this.aliases = aliases != null ? new ArrayList<>(aliases) : new ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAliases() {
            return new ArrayList<>(aliases);
        }
    }
}