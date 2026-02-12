package fr.elias.oreoEssentials.modules.tempfly;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TempFlyConfig {

    private final File configFile;
    private FileConfiguration config;

    private boolean usePermissionGroups = true;
    private boolean useLuckPermsGroups = true;
    private final Map<String, Integer> permissionGroups = new HashMap<>();
    private final Map<String, Integer> luckPermsGroups = new HashMap<>();

    public TempFlyConfig(File dataFolder) {
        this.configFile = new File(dataFolder, "tempfly.yml");
        load();
    }

    public void load() {
        if (!configFile.exists()) {
            createDefault();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        permissionGroups.clear();
        luckPermsGroups.clear();

        usePermissionGroups = config.getBoolean("use-permission-groups", true);
        useLuckPermsGroups = config.getBoolean("use-luckperms-groups", true);

        ConfigurationSection permSection = config.getConfigurationSection("permission-groups");
        if (permSection != null) {
            for (String key : permSection.getKeys(false)) {
                int duration = permSection.getInt(key, 0);
                if (duration > 0) {
                    permissionGroups.put(key, duration);
                }
            }
        }

        ConfigurationSection lpSection = config.getConfigurationSection("luckperms-groups");
        if (lpSection != null) {
            for (String key : lpSection.getKeys(false)) {
                int duration = lpSection.getInt(key, 0);
                if (duration > 0) {
                    luckPermsGroups.put(key, duration);
                }
            }
        }
    }

    private void createDefault() {
        config = new YamlConfiguration();

        config.set("use-permission-groups", true);
        config.set("use-luckperms-groups", true);

        config.set("permission-groups.vip", 300);
        config.set("permission-groups.premium", 600);
        config.set("permission-groups.elite", 1800);

        config.set("luckperms-groups.vip", 300);
        config.set("luckperms-groups.premium", 600);
        config.set("luckperms-groups.elite", 1800);

        try {
            config.save(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean usePermissionGroups() {
        return usePermissionGroups;
    }

    public boolean useLuckPermsGroups() {
        return useLuckPermsGroups;
    }

    public Map<String, Integer> getPermissionGroups() {
        return permissionGroups;
    }

    public Map<String, Integer> getLuckPermsGroups() {
        return luckPermsGroups;
    }
}