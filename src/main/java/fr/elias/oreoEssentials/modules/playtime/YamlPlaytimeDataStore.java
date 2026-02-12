package fr.elias.oreoEssentials.modules.playtime;


import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public final class YamlPlaytimeDataStore implements PlaytimeDataStore {
    private final OreoEssentials plugin;
    private final File file;
    private FileConfiguration cfg;


    public YamlPlaytimeDataStore(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "prewards_data.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }


    private String p(UUID u, String path){ return u.toString() + "." + path; }


    @Override public Set<String> getClaimedOnce(UUID uuid) {
        return new HashSet<>(cfg.getStringList(p(uuid, "claimedOnce")));
    }


    @Override public Map<String, Integer> getPaidCounts(UUID uuid) {
        Map<String, Integer> out = new HashMap<>();
        if (cfg.isConfigurationSection(p(uuid, "paidCounts"))) {
            for (String k : cfg.getConfigurationSection(p(uuid, "paidCounts")).getKeys(false)) {
                out.put(k, cfg.getInt(p(uuid, "paidCounts." + k), 0));
            }
        }
        return out;
    }


    @Override public void setClaimedOnce(UUID uuid, Set<String> ids) {
        cfg.set(p(uuid, "claimedOnce"), ids.stream().sorted().collect(Collectors.toList()));
    }


    @Override public void setPaidCounts(UUID uuid, Map<String, Integer> counts) {
        for (String k : counts.keySet()) cfg.set(p(uuid, "paidCounts." + k), counts.get(k));
    }


    @Override public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { cfg.save(file); } catch (IOException e) { plugin.getLogger().warning("Failed to save prewards_data.yml: " + e.getMessage()); }
        });
    }
}