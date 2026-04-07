// File: src/main/java/fr/elias/oreoEssentials/enderchest/EnderChestConfig.java
package fr.elias.oreoEssentials.modules.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
            if (file == null) {
                File folder = new File(plugin.getDataFolder(), "enderchests");
                if (!folder.exists()) folder.mkdirs();
                file = new File(folder, "enderchest.yml");
            }
            if (!file.exists()) plugin.saveResource("enderchests/enderchest.yml", false);
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[EC] Failed to load enderchests/enderchest.yml: " + e.getMessage());
            cfg = new YamlConfiguration();
        }
    }

    // ── Permission-based ──────────────────────────────────────────────────────

    public boolean isPermissionSlotsEnabled() {
        return cfg.getBoolean("permission-slots.enabled", true);
    }

    /** Fallback slot count for permission-based mode. */
    public int getDefaultSlots() {
        return cap(cfg.getInt("permission-slots.default", 27));
    }

    /** Returns permission-key → slot count (excludes "enabled" and "default"). */
    public Map<String, Integer> getRankSlots() {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("permission-slots");
        if (section == null) return out;
        for (String k : section.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(k) || "default".equalsIgnoreCase(k)) continue;
            if (section.isInt(k)) out.put(k, cap(section.getInt(k)));
        }
        return out;
    }

    // ── LuckPerms group-based ─────────────────────────────────────────────────

    public boolean isRankSlotsEnabled() {
        return cfg.getBoolean("rank-slots.enabled", false);
    }

    /** Returns LuckPerms group name → slot count (excludes "enabled" and "default"). */
    public Map<String, Integer> getRankGroupSlots() {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("rank-slots");
        if (section == null) return out;
        for (String k : section.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(k) || "default".equalsIgnoreCase(k)) continue;
            if (section.isInt(k)) out.put(k.toLowerCase(Locale.ROOT), cap(section.getInt(k)));
        }
        return out;
    }

    /** Fallback slot count for rank-slots mode. */
    public int getRankSlotsDefault() {
        return cap(cfg.getInt("rank-slots.default", 27));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int cap(int v) {
        if (v < 1) v = 1;
        if (v > 54) v = 54;
        return v;
    }
}
