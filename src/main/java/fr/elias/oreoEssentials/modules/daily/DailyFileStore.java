package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class DailyFileStore {

    public static final class Record {
        public UUID uuid;
        public String name;
        public int streak;
        public long lastClaimEpochDay;
        public int totalClaims;

        public LocalDate lastClaimDate() {
            return lastClaimEpochDay <= 0 ? null : LocalDate.ofEpochDay(lastClaimEpochDay);
        }
    }

    private final OreoEssentials plugin;
    private final File dataFile;
    private YamlConfiguration data;

    private final Map<UUID, Record> cache = new ConcurrentHashMap<>();

    public DailyFileStore(OreoEssentials plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "daily_players.yml");
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[Daily] Failed to create daily_players.yml: " + e.getMessage());
                return;
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
        cache.clear();

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection playerData = players.getConfigurationSection(uuidStr);
                    if (playerData == null) continue;

                    Record r = new Record();
                    r.uuid = uuid;
                    r.name = playerData.getString("name", "Unknown");
                    r.streak = playerData.getInt("streak", 0);
                    r.lastClaimEpochDay = playerData.getLong("lastClaimEpochDay", 0L);
                    r.totalClaims = playerData.getInt("totalClaims", 0);

                    cache.put(uuid, r);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[Daily] Invalid UUID in data: " + uuidStr);
                }
            }
        }

        plugin.getLogger().info("[Daily] Loaded " + cache.size() + " player records from file storage.");
    }


    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        data.set("players", null);

        for (Map.Entry<UUID, Record> entry : cache.entrySet()) {
            String path = "players." + entry.getKey().toString();
            Record r = entry.getValue();

            data.set(path + ".name", r.name);
            data.set(path + ".streak", r.streak);
            data.set(path + ".lastClaimEpochDay", r.lastClaimEpochDay);
            data.set(path + ".totalClaims", r.totalClaims);
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Daily] Failed to save daily_players.yml: " + e.getMessage());
            if (plugin.getDataFolder() != null && plugin.getDataFolder().exists()) {
                e.printStackTrace();
            }
        }
    }


    public void startAutoSave() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::save, 6000L, 6000L);
    }


    public Record get(UUID uuid) {
        return cache.get(uuid);
    }


    public Record ensure(UUID uuid, String name) {
        Record r = cache.get(uuid);
        if (r != null) {
            if (!r.name.equals(name)) {
                r.name = name;
            }
            return r;
        }

        r = new Record();
        r.uuid = uuid;
        r.name = name;
        r.streak = 0;
        r.lastClaimEpochDay = 0L;
        r.totalClaims = 0;

        cache.put(uuid, r);
        return r;
    }


    public void updateOnClaim(UUID uuid, String name, int newStreak, LocalDate date) {
        Record r = ensure(uuid, name);
        r.name = name;
        r.streak = newStreak;
        r.lastClaimEpochDay = date.toEpochDay();
        r.totalClaims++;

        save();
    }


    public void resetStreak(UUID uuid) {
        Record r = cache.get(uuid);
        if (r != null) {
            r.streak = 0;
            r.lastClaimEpochDay = 0L;
            save();
        }
    }


    public void close() {
        save();
        cache.clear();
    }
}