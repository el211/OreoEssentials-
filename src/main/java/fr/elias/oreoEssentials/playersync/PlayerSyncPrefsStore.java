package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public final class PlayerSyncPrefsStore {
    private final OreoEssentials plugin;
    private final boolean dInv, dXp, dHealth, dHunger;
    private final File file;
    private YamlConfiguration cfg;

    public PlayerSyncPrefsStore(OreoEssentials plugin) {
        this.plugin = plugin;
        this.dInv    = plugin.getConfig().getBoolean("playersync.inventory", true);
        this.dXp     = plugin.getConfig().getBoolean("playersync.xp", true);
        this.dHealth = plugin.getConfig().getBoolean("playersync.health", true);
        this.dHunger = plugin.getConfig().getBoolean("playersync.hunger", true);

        this.file = new File(plugin.getDataFolder(), "player-sync-prefs.yml");
        this.cfg  = YamlConfiguration.loadConfiguration(file);
    }

    public PlayerSyncPrefs get(UUID id) {
        String base = "prefs." + id + ".";
        if (!cfg.contains(base)) return PlayerSyncPrefs.defaults(dInv, dXp, dHealth, dHunger);
        PlayerSyncPrefs p = new PlayerSyncPrefs();
        p.inv    = cfg.getBoolean(base + "inv", dInv);
        p.xp     = cfg.getBoolean(base + "xp", dXp);
        p.health = cfg.getBoolean(base + "health", dHealth);
        p.hunger = cfg.getBoolean(base + "hunger", dHunger);
        return p;
    }

    public void set(UUID id, PlayerSyncPrefs p) {
        String base = "prefs." + id + ".";
        cfg.set(base + "inv",    p.inv);
        cfg.set(base + "xp",     p.xp);
        cfg.set(base + "health", p.health);
        cfg.set(base + "hunger", p.hunger);
        try { cfg.save(file); } catch (Exception ignored) {}
    }
}
