package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public final class YamlPlayerSyncStorage implements PlayerSyncStorage {
    private final OreoEssentials plugin;
    private final File file;
    private YamlConfiguration cfg;

    public YamlPlayerSyncStorage(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-sync.yml");
        this.cfg  = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void save(UUID uuid, PlayerSyncSnapshot snap) throws Exception {
        cfg.set("players." + uuid + ".blob", PlayerSyncSnapshot.toBase64(snap));
        cfg.save(file);
    }

    @Override
    public PlayerSyncSnapshot load(UUID uuid) throws Exception {
        String b64 = cfg.getString("players." + uuid + ".blob", null);
        if (b64 == null) return null;
        return PlayerSyncSnapshot.fromBase64(b64);
    }
}
