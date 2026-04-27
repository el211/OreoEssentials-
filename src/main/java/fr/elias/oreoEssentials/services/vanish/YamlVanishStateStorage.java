package fr.elias.oreoEssentials.services.vanish;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class YamlVanishStateStorage implements VanishStateStorage {
    private final File file;
    private final YamlConfiguration cfg;

    public YamlVanishStateStorage(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "vanish-state.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized boolean isVanished(UUID playerId) {
        if (playerId == null) return false;
        return cfg.getBoolean("players." + playerId, false);
    }

    @Override
    public synchronized void setVanished(UUID playerId, boolean vanished) throws IOException {
        if (playerId == null) return;
        String path = "players." + playerId;
        if (vanished) {
            cfg.set(path, true);
        } else {
            cfg.set(path, null);
        }
        cfg.save(file);
    }
}
