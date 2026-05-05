package fr.elias.oreoEssentials.modules.ignore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoreService {

    private final Plugin            plugin;
    private final File              file;
    private       FileConfiguration cfg;
    /** playerUuid -> set of ignored UUIDs */
    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();

    public IgnoreService(Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "ignore.yml");
        if (!file.exists()) {
            try { plugin.getDataFolder().mkdirs(); file.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[Ignore] Cannot create ignore.yml: " + e.getMessage()); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    public boolean isIgnoring(UUID player, UUID target) {
        Set<UUID> set = ignored.get(player);
        return set != null && set.contains(target);
    }

    /** @return true if added, false if already ignored */
    public boolean ignore(UUID player, UUID target) {
        if (player.equals(target)) return false;
        boolean added = ignored.computeIfAbsent(player, k -> new HashSet<>()).add(target);
        if (added) save(player);
        return added;
    }

    /** @return true if removed, false if wasn't ignored */
    public boolean unignore(UUID player, UUID target) {
        Set<UUID> set = ignored.get(player);
        if (set == null || !set.remove(target)) return false;
        save(player);
        return true;
    }

    public Set<UUID> getIgnoredBy(UUID player) {
        return Collections.unmodifiableSet(ignored.getOrDefault(player, Collections.emptySet()));
    }

    public void clearIgnoreList(UUID player) {
        ignored.remove(player);
        cfg.set("ignore." + player, null);
        saveFile();
    }

    private void loadAll() {
        var root = cfg.getConfigurationSection("ignore");
        if (root == null) return;
        for (String uuidStr : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> list = cfg.getStringList("ignore." + uuidStr);
                Set<UUID> set = new HashSet<>();
                for (String s : list) {
                    try { set.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                if (!set.isEmpty()) ignored.put(uuid, set);
            } catch (Exception ignored) {}
        }
    }

    private void save(UUID player) {
        Set<UUID> set = ignored.getOrDefault(player, Collections.emptySet());
        if (set.isEmpty()) cfg.set("ignore." + player, null);
        else cfg.set("ignore." + player, set.stream().map(UUID::toString).toList());
        saveFile();
    }

    private void saveFile() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().severe("[Ignore] Failed to save ignore.yml: " + e.getMessage()); }
    }
}
