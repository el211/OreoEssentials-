package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.util.LocUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class YamlStorage implements StorageApi {

    private final Plugin plugin;
    private final File dataDir;
    private final File warpsFile;
    private final File spawnFile;
    private YamlConfiguration warps;
    private YamlConfiguration spawn;

    private final ConcurrentMap<UUID, YamlConfiguration> playerCache = new ConcurrentHashMap<>();

    public YamlStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data/playerdata");
        this.warpsFile = new File(plugin.getDataFolder(), "data/warps.yml");
        this.spawnFile = new File(plugin.getDataFolder(), "data/spawn.yml");
        init();
    }

    private void init() {
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
            throw new RuntimeException("Failed to init YAML storage", e);
        }
    }

    private YamlConfiguration player(UUID uuid) {
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



    @Override
    public void setBackData(UUID uuid, Map<String, Object> data) {
        YamlConfiguration y = player(uuid);

        if (data == null) {
            // remove old lastBack section
            y.set("back", null);
        } else {
            y.createSection("back", data);
        }
        savePlayer(uuid);
    }

    @Override
    public Map<String, Object> getBackData(UUID uuid) {
        YamlConfiguration y = player(uuid);
        ConfigurationSection sec = y.getConfigurationSection("back");
        return (sec != null ? sec.getValues(false) : null);
    }



    @Override
    public void setSpawn(String server, Location loc) {
        String key = (server == null ? "" : server.trim().toLowerCase(Locale.ROOT));

        ConfigurationSection sec = spawn.getConfigurationSection("spawns." + key);
        if (sec == null) sec = spawn.createSection("spawns." + key);

        LocUtil.write(sec, loc);
        saveSpawn();
    }

    @Override
    public Location getSpawn(String server) {
        String key = (server == null ? "" : server.trim().toLowerCase(Locale.ROOT));

        Location loc = LocUtil.read(spawn.getConfigurationSection("spawns." + key));
        if (loc != null) return loc;

        return LocUtil.read(spawn.getConfigurationSection("spawn"));
    }


    @Override
    public void setWarp(String name, Location loc) {
        String key = name.toLowerCase();
        var sec = warps.getConfigurationSection(key);
        if (sec == null) sec = warps.createSection(key);
        LocUtil.write(sec, loc);
        saveWarps();
    }

    @Override
    public boolean delWarp(String name) {
        String key = name.toLowerCase();
        if (warps.contains(key)) {
            warps.set(key, null);
            saveWarps();
            return true;
        }
        return false;
    }

    @Override
    public Location getWarp(String name) {
        return LocUtil.read(warps.getConfigurationSection(name.toLowerCase()));
    }

    @Override
    public Set<String> listWarps() {
        return warps.getKeys(false).stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    @Override
    public boolean setHome(UUID uuid, String name, Location loc) {
        String key = "homes." + name.toLowerCase();
        YamlConfiguration y = player(uuid);

        var sec = y.getConfigurationSection(key);
        if (sec == null) sec = y.createSection(key);

        LocUtil.write(sec, loc);
        savePlayer(uuid);
        return true;
    }

    @Override
    public boolean delHome(UUID uuid, String name) {
        String key = "homes." + name.toLowerCase();
        YamlConfiguration y = player(uuid);

        if (y.contains(key)) {
            y.set(key, null);
            savePlayer(uuid);
            return true;
        }
        return false;
    }

    @Override
    public Location getHome(UUID uuid, String name) {
        return LocUtil.read(player(uuid).getConfigurationSection("homes." + name.toLowerCase()));
    }

    @Override
    public Set<String> homes(UUID uuid) {
        YamlConfiguration y = player(uuid);
        ConfigurationSection sec = y.getConfigurationSection("homes");
        if (sec == null) return Set.of();
        return sec.getKeys(false).stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Map<String, HomeService.StoredHome> listHomes(UUID owner) {
        Map<String, HomeService.StoredHome> out = new LinkedHashMap<>();
        YamlConfiguration yml = player(owner);

        ConfigurationSection homesSec = yml.getConfigurationSection("homes");
        if (homesSec == null) return out;

        String server = org.bukkit.Bukkit.getServer().getName();

        for (String name : homesSec.getKeys(false)) {
            Location loc = LocUtil.read(homesSec.getConfigurationSection(name));
            if (loc == null) continue;

            String world = (loc.getWorld() != null ? loc.getWorld().getName() : "world");

            out.put(
                    name.toLowerCase(Locale.ROOT),
                    new HomeService.StoredHome(world, loc.getX(), loc.getY(), loc.getZ(), server)
            );
        }

        return out;
    }


    @Override
    public void setLast(UUID uuid, Location loc) {
        // FIRST: new global system (BackLocation)
        StorageApi.super.setLast(uuid, loc);

        // OLD local system
        YamlConfiguration y = player(uuid);
        var sec = y.getConfigurationSection("lastLocation");
        if (sec == null) sec = y.createSection("lastLocation");

        LocUtil.write(sec, loc);
        savePlayer(uuid);
    }

    @Override
    public Location getLast(UUID uuid) {
        // Prefer new global /back system
        Location fromBack = StorageApi.super.getLast(uuid);
        if (fromBack != null) return fromBack;

        // Fallback to old "lastLocation"
        return LocUtil.read(player(uuid).getConfigurationSection("lastLocation"));
    }



    private void savePlayer(UUID uuid) {
        File f = new File(dataDir, uuid + ".yml");
        try {
            player(uuid).save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed saving " + f + ": " + e.getMessage());
        }
    }

    private void saveWarps() {
        try { warps.save(warpsFile); }
        catch (IOException e) { plugin.getLogger().warning("Failed saving warps: " + e.getMessage()); }
    }

    private void saveSpawn() {
        try { spawn.save(spawnFile); }
        catch (IOException e) { plugin.getLogger().warning("Failed saving spawn: " + e.getMessage()); }
    }

    @Override
    public void flush() {
        playerCache.keySet().forEach(this::savePlayer);
        saveWarps();
        saveSpawn();
    }

    @Override
    public void close() { /* no-op */ }
}
