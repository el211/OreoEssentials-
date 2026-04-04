package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads and reloads plugins/OreoEssentials/custom-nameplates/config.yml.
 * On first run the default file is copied from the jar's resources.
 */
public final class CustomNameplatesConfig {

    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;

    private static final String RESOURCE_PATH = "custom-nameplates/config.yml";

    public CustomNameplatesConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File folder = new File(plugin.getDataFolder(), "custom-nameplates");
        if (!folder.exists()) folder.mkdirs();

        file = new File(folder, "config.yml");
        if (!file.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }

        cfg = YamlConfiguration.loadConfiguration(file);
    }

    /** Returns the raw FileConfiguration for this config file. */
    public FileConfiguration raw() {
        return cfg;
    }
}
