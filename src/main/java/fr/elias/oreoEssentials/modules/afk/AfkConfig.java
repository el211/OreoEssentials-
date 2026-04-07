package fr.elias.oreoEssentials.modules.afk;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Loads and exposes the dedicated AFK module configuration from
 * {@code plugins/OreoEssentials/afk/config.yml}.
 */
public class AfkConfig {

    private static final String RESOURCE_PATH = "afk/config.yml";

    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;

    public AfkConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(new File(plugin.getDataFolder(), "afk"), "config.yml");
        reload();
    }

    // -------------------------------------------------------------------------
    // Load / reload
    // -------------------------------------------------------------------------

    public void reload() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (!file.exists()) plugin.saveResource(RESOURCE_PATH, false);

        cfg = YamlConfiguration.loadConfiguration(file);
        mergeDefaults();
    }

    /** Adds any keys present in the bundled jar default but missing from the live file. */
    private void mergeDefaults() {
        try (InputStream is = plugin.getResource(RESOURCE_PATH)) {
            if (is == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue;
                if (!cfg.contains(key)) { cfg.set(key, defaults.get(key)); changed = true; }
            }
            if (changed) { try { cfg.save(file); } catch (IOException ignored) {} }
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Auto-AFK
    // -------------------------------------------------------------------------

    public boolean autoEnabled()            { return cfg.getBoolean("auto.enabled", false); }
    public int autoSeconds()                { return Math.max(0, cfg.getInt("auto.seconds", 300)); }
    public int checkIntervalSeconds()       { return Math.max(1, cfg.getInt("auto.check-interval-seconds", 5)); }
    public List<Map<?, ?>> permissionTiers(){ return cfg.getMapList("auto.permission-tiers"); }

    // -------------------------------------------------------------------------
    // Action bar / back-message
    // -------------------------------------------------------------------------

    public boolean actionBarEnabled()       { return cfg.getBoolean("actionbar.enabled", true); }
    public boolean backMessageEnabled()     { return cfg.getBoolean("back-message.enabled", true); }

    // -------------------------------------------------------------------------
    // Custom messages
    // -------------------------------------------------------------------------

    public List<Map<?, ?>> customMessages() { return cfg.getMapList("custom-messages"); }

    // -------------------------------------------------------------------------
    // Web UI
    // -------------------------------------------------------------------------

    public boolean webEnabled()             { return cfg.getBoolean("web.enabled", false); }
    public int webPort()                    { return cfg.getInt("web.port", 8765); }

    // -------------------------------------------------------------------------
    // AFK Pool
    // -------------------------------------------------------------------------

    public boolean poolEnabled()            { return cfg.getBoolean("pool.enabled", false); }
    public String poolRegionName()          { return cfg.getString("pool.region-name", "afk_pool"); }
    public String poolWorldName()           { return cfg.getString("pool.world-name", "world"); }
    public String poolServer(String def)    { String v = cfg.getString("pool.server", def); return (v == null || v.isEmpty()) ? def : v; }
    public boolean poolCrossServer()        { return cfg.getBoolean("pool.cross-server", false); }

    // -------------------------------------------------------------------------
    // Raw access
    // -------------------------------------------------------------------------

    public FileConfiguration raw() { return cfg; }
}
