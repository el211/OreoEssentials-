package fr.elias.oreoEssentials.services;


import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Storage {
    private final OreoEssentials plugin;
    private final File dataDir;
    private final File warpsFile;
    private final File spawnFile;
    private final ConcurrentMap<UUID, YamlConfiguration> playerCache = new ConcurrentHashMap<>();
    private YamlConfiguration warps;
    private YamlConfiguration spawn;

    public Storage(OreoEssentials plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data/playerdata");
        this.warpsFile = new File(plugin.getDataFolder(), "data/warps.yml");
        this.spawnFile = new File(plugin.getDataFolder(), "data/spawn.yml");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            if (!dataDir.exists()) dataDir.mkdirs();
            if (!warpsFile.getParentFile().exists()) warpsFile.getParentFile().mkdirs();
            if (!warpsFile.exists()) warpsFile.createNewFile();
            if (!spawnFile.exists()) spawnFile.createNewFile();
            this.warps = new YamlConfiguration();
            this.warps.load(warpsFile);
            this.spawn = new YamlConfiguration();
            this.spawn.load(spawnFile);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException("Failed to init storage", e);
        }
    }

    public YamlConfiguration warps() { return warps; }
    public YamlConfiguration spawn() { return spawn; }

    public YamlConfiguration player(UUID uuid) {
        return playerCache.computeIfAbsent(uuid, id -> {
            File f = new File(dataDir, id + ".yml");
            YamlConfiguration y = new YamlConfiguration();
            try {
                if (!f.exists()) f.createNewFile();
                y.load(f);
            } catch (IOException | InvalidConfigurationException e) {
                throw new RuntimeException(e);
            }
            return y;
        });
    }

    public void savePlayer(UUID uuid) {
        File f = new File(dataDir, uuid + ".yml");
        try { player(uuid).save(f); } catch (IOException e) { plugin.getLogger().warning("Failed saving " + f + ": " + e.getMessage()); }
    }

    public void saveWarps() { try { warps.save(warpsFile); } catch (IOException e) { plugin.getLogger().warning("Failed saving warps: " + e.getMessage()); } }
    public void saveSpawn() { try { spawn.save(spawnFile); } catch (IOException e) { plugin.getLogger().warning("Failed saving spawn: " + e.getMessage()); } }

    public void flush() {
        playerCache.keySet().forEach(this::savePlayer);
        saveWarps();
        saveSpawn();
    }
}
