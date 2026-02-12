package fr.elias.oreoEssentials.modules.chat.channels;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class ChannelPersistence {

    private final OreoEssentials plugin;
    private final File dataFile;

    public ChannelPersistence(OreoEssentials plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "channel-data.yml");

        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[Channels] Failed to create channel data file: " + e.getMessage());
            }
        }
    }

    public Map<UUID, String> loadAll() {
        Map<UUID, String> data = new HashMap<>();

        if (!dataFile.exists()) {
            return data;
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

            for (String key : cfg.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String channel = cfg.getString(key);
                    if (channel != null) {
                        data.put(uuid, channel);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Channels] Failed to load channel data: " + e.getMessage());
        }

        return data;
    }

    public void saveAll(Map<UUID, String> data) {
        try {
            FileConfiguration cfg = new YamlConfiguration();

            for (Map.Entry<UUID, String> entry : data.entrySet()) {
                cfg.set(entry.getKey().toString(), entry.getValue());
            }

            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Channels] Failed to save channel data: " + e.getMessage());
        }
    }

    public void save(UUID uuid, String channelId) {
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            cfg.set(uuid.toString(), channelId);
            cfg.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[Channels] Failed to save channel for " + uuid + ": " + e.getMessage());
        }
    }

    public void remove(UUID uuid) {
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            cfg.set(uuid.toString(), null);
            cfg.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[Channels] Failed to remove channel for " + uuid + ": " + e.getMessage());
        }
    }
}