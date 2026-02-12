package fr.elias.oreoEssentials.services.yaml;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class YamlPlayerWarpStorage implements PlayerWarpStorage {

    private final OreoEssentials plugin;
    private final File file;
    private final FileConfiguration cfg;

    // in-memory cache of warps
    private final Map<String, PlayerWarp> cache = new HashMap<>();

    public YamlPlayerWarpStorage(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerwarps.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[PlayerWarps] Could not create playerwarps.yml: " + e.getMessage());
            }
        }

        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAllFromFile();
    }



    @Override
    public synchronized void save(PlayerWarp warp) {
        cache.put(warp.getId(), warp);
        saveAllToFile();
    }

    @Override
    public synchronized PlayerWarp getById(String id) {
        return cache.get(id);
    }

    @Override
    public synchronized PlayerWarp getByOwnerAndName(UUID owner, String nameLower) {
        if (owner == null || nameLower == null) return null;
        String lower = nameLower.trim().toLowerCase(Locale.ROOT);
        for (PlayerWarp warp : cache.values()) {
            if (owner.equals(warp.getOwner())
                    && lower.equals(warp.getName().trim().toLowerCase(Locale.ROOT))) {
                return warp;
            }
        }
        return null;
    }

    @Override
    public synchronized boolean delete(String id) {
        if (id == null || id.isEmpty()) return false;
        PlayerWarp removed = cache.remove(id);
        if (removed != null) {
            saveAllToFile();
            return true;
        }
        return false;
    }

    @Override
    public synchronized List<PlayerWarp> listByOwner(UUID owner) {
        if (owner == null) return Collections.emptyList();
        return cache.values().stream()
                .filter(w -> owner.equals(w.getOwner()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<PlayerWarp> listAll() {
        return new ArrayList<>(cache.values());
    }



    private void loadAllFromFile() {
        cache.clear();

        ConfigurationSection root = cfg.getConfigurationSection("warps");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            PlayerWarp warp = fromSection(id, sec);
            if (warp != null) {
                cache.put(id, warp);
            }
        }
        plugin.getLogger().info("[PlayerWarps] Loaded " + cache.size() + " warps from playerwarps.yml");
    }

    private void saveAllToFile() {
        cfg.set("warps", null);
        ConfigurationSection root = cfg.createSection("warps");

        for (PlayerWarp warp : cache.values()) {
            ConfigurationSection sec = root.createSection(warp.getId());
            toSection(warp, sec);
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[PlayerWarps] Failed to save playerwarps.yml: " + e.getMessage());
        }
    }

    private void toSection(PlayerWarp warp, ConfigurationSection sec) {
        Location loc = warp.getLocation();

        sec.set("owner", warp.getOwner().toString());
        sec.set("name", warp.getName() == null
                ? ""
                : warp.getName().trim().toLowerCase(Locale.ROOT));

        if (loc != null && loc.getWorld() != null) {
            sec.set("world", loc.getWorld().getName());
            sec.set("x", loc.getX());
            sec.set("y", loc.getY());
            sec.set("z", loc.getZ());
            sec.set("yaw", loc.getYaw());
            sec.set("pitch", loc.getPitch());
        }

        sec.set("description", warp.getDescription());
        sec.set("category", warp.getCategory());
        sec.set("locked", warp.isLocked());
        sec.set("cost", warp.getCost());

        // whitelist
        sec.set("whitelist_enabled", warp.isWhitelistEnabled());
        Set<UUID> wl = warp.getWhitelist();
        List<String> wlList = (wl == null ? Collections.emptyList()
                : wl.stream().map(UUID::toString).collect(Collectors.toList()));
        sec.set("whitelist_players", wlList);


        if (warp.getIcon() != null) {
            sec.set("icon", warp.getIcon());
        } else {
            sec.set("icon", null);
        }
    }

    private PlayerWarp fromSection(String id, ConfigurationSection sec) {
        String ownerStr = sec.getString("owner");
        if (ownerStr == null) return null;

        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String worldName = sec.getString("world");
        World world = (worldName == null ? null : Bukkit.getWorld(worldName));
        if (world == null) {
            // world not loaded -> skip
            return null;
        }

        double x = sec.getDouble("x");
        double y = sec.getDouble("y");
        double z = sec.getDouble("z");
        float yaw = (float) sec.getDouble("yaw");
        float pitch = (float) sec.getDouble("pitch");
        Location loc = new Location(world, x, y, z, yaw, pitch);

        String name = sec.getString("name");
        if (name == null) name = "";

        boolean wlEnabled = sec.getBoolean("whitelist_enabled", false);
        Set<UUID> wl = new HashSet<>();
        for (String s : sec.getStringList("whitelist_players")) {
            try {
                wl.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        PlayerWarp warp = new PlayerWarp(id, owner, name, loc, wlEnabled, wl);

        warp.setDescription(sec.getString("description"));
        warp.setCategory(sec.getString("category"));
        warp.setLocked(sec.getBoolean("locked", false));
        warp.setCost(sec.getDouble("cost", 0.0));

        ItemStack icon = sec.getItemStack("icon");
        if (icon != null) {
            warp.setIcon(icon);
        }

        return warp;
    }
}
