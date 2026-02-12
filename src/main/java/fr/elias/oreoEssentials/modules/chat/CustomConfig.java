package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class CustomConfig {
    private final OreoEssentials plugin;
    private final File file;
    private FileConfiguration config;
    private final String fileName;

    public CustomConfig(OreoEssentials plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);

        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                if (plugin.getResource(fileName) != null) {
                    plugin.saveResource(fileName, false);
                } else {
                    file.createNewFile();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error creating " + fileName + ": " + e.getMessage());
        }

        // Load the existing config FIRST
        config = YamlConfiguration.loadConfiguration(file);

        // Only merge defaults if needed
        mergeDefaults();
    }

    private void mergeDefaults() {
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream == null) {
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);

            boolean modified = false;
            modified |= mergeSection(defaultConfig, config, "");

            if (modified) {
                saveCustomConfig();
                plugin.getLogger().info("[" + fileName + "] Added missing configuration fields from defaults.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to merge defaults for " + fileName + ": " + e.getMessage());
        }
    }

    private boolean mergeSection(ConfigurationSection defaults, ConfigurationSection current, String path) {
        boolean modified = false;
        Set<String> defaultKeys = defaults.getKeys(false);

        for (String key : defaultKeys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;

            // ONLY add if the key doesn't exist
            if (!current.contains(key)) {
                Object defaultValue = defaults.get(key);
                current.set(key, defaultValue);
                modified = true;
                plugin.getLogger().info("[" + fileName + "] Added missing field: " + fullPath);
            }
            // If both are sections, recursively merge
            else if (defaults.isConfigurationSection(key) && current.isConfigurationSection(key)) {
                ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                ConfigurationSection currentSection = current.getConfigurationSection(key);
                if (defaultSection != null && currentSection != null) {
                    modified |= mergeSection(defaultSection, currentSection, fullPath);
                }
            }
            // IMPORTANT: If key exists but is NOT a section, DO NOT OVERWRITE IT
        }

        return modified;
    }

    public FileConfiguration getCustomConfig() {
        return config;
    }

    public void saveCustomConfig() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + file.getName() + ": " + e.getMessage());
        }
    }

    public void reloadCustomConfig() {
        // Load from file FIRST (this preserves your custom values)
        config = YamlConfiguration.loadConfiguration(file);

        // Then merge any NEW defaults that might have been added
        mergeDefaults();
    }
}