package fr.elias.oreoEssentials.modules.commandtoggle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Loads commandsmodule/commands-toggle.yml and resolves configured aliases.
 *
 * Important precedence rule:
 *   1) A real command name always wins over an alias with the same label.
 *   2) If two configured aliases use the same label, the first command in the
 *      YAML file wins. A warning is logged for the duplicate.
 */
public class CommandToggleConfig {
    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final File configFile;
    private String disabledMessage;

    // LinkedHashMap is intentional: YAML order is used to resolve duplicate aliases deterministically.
    private final Map<String, CommandToggleEntry> commands = new LinkedHashMap<>();
    private final Map<String, String> aliasOwners = new LinkedHashMap<>();

    private final Map<String, Runnable> moduleCallbacks = new HashMap<>();

    public CommandToggleConfig(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configFile = new File(plugin.getDataFolder(), "commandsmodule/commands-toggle.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            File parent = configFile.getParentFile();
            if (parent != null) parent.mkdirs();
            plugin.saveResource("commandsmodule/commands-toggle.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.disabledMessage = config.getString(
                "disabled-command-message",
                "&cThis command is currently disabled."
        );

        commands.clear();
        aliasOwners.clear();

        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection == null) {
            plugin.getLogger().warning("[CommandToggle] No 'commands' section found in commands-toggle.yml");
            return;
        }

        // Pass 1: load all primary command names first.
        // This lets us guarantee that a primary command label always beats an alias.
        for (String rawCommandName : commandsSection.getKeys(false)) {
            ConfigurationSection cmdSection = commandsSection.getConfigurationSection(rawCommandName);
            if (cmdSection == null) continue;

            String commandName = normalizeLabel(rawCommandName);
            if (commandName.isEmpty()) {
                plugin.getLogger().warning("[CommandToggle] Ignoring an empty command name in commands-toggle.yml");
                continue;
            }

            boolean enabled = cmdSection.getBoolean("enabled", true);
            List<String> aliases = normalizeAliases(cmdSection.getStringList("aliases"), commandName);

            commands.put(commandName, new CommandToggleEntry(commandName, enabled, aliases));
        }

        // Pass 2: build a deterministic alias -> primary-command index.
        Set<String> primaryNames = commands.keySet();
        for (CommandToggleEntry entry : commands.values()) {
            for (String alias : entry.getAliases()) {
                if (primaryNames.contains(alias)) {
                    if (!alias.equals(entry.getName())) {
                        plugin.getLogger().warning(
                                "[CommandToggle] Alias '/" + alias + "' for /" + entry.getName()
                                        + " conflicts with the primary command /" + alias
                                        + "; the primary command wins."
                        );
                    }
                    continue;
                }

                String previousOwner = aliasOwners.putIfAbsent(alias, entry.getName());
                if (previousOwner != null && !previousOwner.equals(entry.getName())) {
                    plugin.getLogger().warning(
                            "[CommandToggle] Duplicate alias '/" + alias + "' declared for /"
                                    + previousOwner + " and /" + entry.getName()
                                    + "; keeping /" + previousOwner + "."
                    );
                }
            }
        }

        plugin.getLogger().info(
                "[CommandToggle] Loaded " + commands.size() + " command toggles and "
                        + aliasOwners.size() + " resolved alias(es) from commands-toggle.yml"
        );
    }

    public void reload() {
        load();
    }

    /**
     * Checks a PRIMARY command name. Unknown commands are enabled by default.
     * If an alias is supplied, it is resolved to its primary command first.
     */
    public boolean isCommandEnabled(String commandNameOrAlias) {
        String resolved = resolveCommandName(commandNameOrAlias);
        if (resolved == null) return true;

        CommandToggleEntry entry = commands.get(resolved);
        return entry == null || entry.isEnabled();
    }

    /**
     * Resolves a command label to the primary command configured in commands-toggle.yml.
     * Primary names take precedence over aliases.
     * Returns null when the label is not managed by this config.
     */
    public String resolveCommandName(String label) {
        String normalized = normalizeLabel(label);
        if (normalized.isEmpty()) return null;

        if (commands.containsKey(normalized)) return normalized;
        return aliasOwners.get(normalized);
    }

    public boolean containsCommand(String name) {
        String normalized = normalizeLabel(name);
        return commands.containsKey(normalized);
    }

    public CommandToggleEntry getCommand(String nameOrAlias) {
        String resolved = resolveCommandName(nameOrAlias);
        return resolved == null ? null : commands.get(resolved);
    }

    public String getDisabledMessage() {
        return disabledMessage;
    }

    /** Returns a copy preserving YAML order. */
    public Map<String, CommandToggleEntry> getAllCommands() {
        return new LinkedHashMap<>(commands);
    }

    /** Returns the resolved alias -> primary-command mapping. */
    public Map<String, String> getAliasOwners() {
        return new LinkedHashMap<>(aliasOwners);
    }

    public void registerModuleCallback(String commandName, Runnable onToggle) {
        if (onToggle == null) return;
        String normalized = normalizeLabel(commandName);
        if (!normalized.isEmpty()) {
            moduleCallbacks.put(normalized, onToggle);
        }
    }

    public void fireAllCallbacks() {
        for (Map.Entry<String, Runnable> entry : moduleCallbacks.entrySet()) {
            try {
                entry.getValue().run();
            } catch (Throwable t) {
                plugin.getLogger().warning(
                        "[CommandToggle] Module callback failed for '" + entry.getKey() + "': " + t.getMessage()
                );
            }
        }
    }

    public void setCommandEnabled(String commandNameOrAlias, boolean enabled) {
        String resolved = resolveCommandName(commandNameOrAlias);
        if (resolved == null) return;

        CommandToggleEntry entry = commands.get(resolved);
        if (entry == null) return;

        entry.setEnabled(enabled);
        save();

        Runnable callback = moduleCallbacks.get(resolved);
        if (callback != null) {
            try {
                callback.run();
            } catch (Throwable t) {
                plugin.getLogger().warning(
                        "[CommandToggle] Module callback failed for '" + resolved + "': " + t.getMessage()
                );
            }
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

    private List<String> normalizeAliases(List<String> rawAliases, String owner) {
        if (rawAliases == null || rawAliases.isEmpty()) return List.of();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : rawAliases) {
            String alias = normalizeLabel(raw);
            if (alias.isEmpty() || alias.equals(owner)) continue;
            out.add(alias);
        }
        return new ArrayList<>(out);
    }

    /** Normalizes '/foo', 'plugin:foo' and mixed-case labels to 'foo'. */
    public static String normalizeLabel(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("/")) s = s.substring(1);
        int colon = s.indexOf(':');
        if (colon >= 0 && colon + 1 < s.length()) s = s.substring(colon + 1);
        return s.trim();
    }

    public static class CommandToggleEntry {
        private final String name;
        private boolean enabled;
        private final List<String> aliases;

        public CommandToggleEntry(String name, boolean enabled, List<String> aliases) {
            this.name = normalizeLabel(name);
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
