// File: src/main/java/fr/elias/oreoEssentials/enderchest/EnderChestConfig.java
package fr.elias.oreoEssentials.modules.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class EnderChestConfig {
    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;

    public EnderChestConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        try {
            if (file == null) file = new File(plugin.getDataFolder(), "enderchest.yml");
            if (!file.exists()) plugin.saveResource("enderchest.yml", false);
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[EC] Failed to load enderchest.yml: " + e.getMessage());
            cfg = new YamlConfiguration();
        }
    }

    public int getDefaultSlots() {
        return cap(cfg.getInt("default", 3));
    }

    public Map<String, Integer> getRankSlots() {
        Map<String, Integer> out = new LinkedHashMap<>();
        Set<String> keys = cfg.getKeys(false);
        for (String k : keys) {
            if ("default".equalsIgnoreCase(k)) continue;
            int v = cap(cfg.getInt(k, -1));
            if (v > 0) out.put(k, v);
        }
        return out;
    }

    private int cap(int v) {
        if (v < 1) v = 1;
        if (v > 54) v = 54;
        return v;
    }
}
