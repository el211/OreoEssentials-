package fr.elias.oreoEssentials.modules.playerwarp;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple YAML-backed implementation of PlayerWarpStorage.
 *
 * (playerwarps.yml):
 *
 * playerwarps:
 *   <id>:
 *     owner: "uuid"
 *     name: "warpname"
 *     world: "world"
 *     x: 0.0
 *     y: 64.0
 *     z: 0.0
 *     yaw: 0.0
 *     pitch: 0.0
 *     description: "..."
 *     category: "..."
 *     locked: false
 *     cost: 0.0
 *     icon: <ItemStack>
 *     whitelist:
 *       enabled: true
 *       players:
 *         - "uuid1"
 *         - "uuid2"
 *     managers:
 *       - "uuid1"
 *       - "uuid2"
 *     password: "changeme"
 */
public class YamlPlayerWarpStorage implements PlayerWarpStorage {

    private static final String ROOT = "playerwarps";

    private final OreoEssentials plugin;
    private final File file;
    private FileConfiguration config;

    // Simple in-memory cache
    private final Map<String, PlayerWarp> byId = new HashMap<>();

    public YamlPlayerWarpStorage(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerwarps.yml");
        loadFile();
        loadAllFromConfig();
    }



    private void loadFile() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[PlayerWarps] Failed to create playerwarps.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[PlayerWarps] Failed to save playerwarps.yml: " + e.getMessage());
        }
    }

    private void loadAllFromConfig() {
        byId.clear();

        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            try {
                PlayerWarp warp = fromSection(id, sec);
                if (warp != null) {
                    byId.put(id, warp);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[PlayerWarps] Failed to load warp '" + id + "': " + ex.getMessage());
            }
        }
    }

    private PlayerWarp fromSection(String warpId, ConfigurationSection sec) {
        String ownerStr = sec.getString("owner");
        String name = sec.getString("name");
        String world = sec.getString("world");

        if (ownerStr == null || name == null || world == null) {
            plugin.getLogger().warning("[PlayerWarps] Missing required fields for warp " + warpId + " (owner/name/world). Skipping.");
            return null;
        }

        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[PlayerWarps] Invalid owner UUID for warp " + warpId + ": " + ownerStr);
            return null;
        }

        double x = sec.getDouble("x");
        double y = sec.getDouble("y");
        double z = sec.getDouble("z");
        float yaw = (float) sec.getDouble("yaw");
        float pitch = (float) sec.getDouble("pitch");

        Location loc = PlayerWarp.fromData(world, x, y, z, yaw, pitch);
        if (loc == null) {
            plugin.getLogger().warning("[PlayerWarps] World '" + world + "' not found for warp " + warpId + ". Skipping.");
            return null;
        }

        boolean whitelistEnabled = sec.getBoolean("whitelist.enabled", false);
        List<String> wlRaw = sec.getStringList("whitelist.players");
        Set<UUID> wl = new HashSet<>();
        for (String s : wlRaw) {
            try {
                wl.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }

        PlayerWarp warp = new PlayerWarp(warpId, owner, name, loc, whitelistEnabled, wl);

        warp.setDescription(sec.getString("description", ""));
        warp.setCategory(sec.getString("category", ""));
        warp.setLocked(sec.getBoolean("locked", false));
        warp.setCost(sec.getDouble("cost", 0.0D));

        Object rawIcon = sec.get("icon");
        if (rawIcon instanceof ItemStack icon) {
            warp.setIcon(icon);
        }

        List<String> managersRaw = sec.getStringList("managers");
        Set<UUID> managers = new HashSet<>();
        for (String s : managersRaw) {
            try {
                managers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        warp.setManagers(managers);

        String pwd = sec.getString("password", null);
        warp.setPassword(pwd); // PlayerWarp normalizes null/empty

        return warp;
    }

    private void toSection(String id, PlayerWarp warp, ConfigurationSection root) {
        ConfigurationSection sec = root.getConfigurationSection(id);
        if (sec == null) {
            sec = root.createSection(id);
        }

        Location loc = warp.getLocation();
        String worldName = (loc.getWorld() != null ? loc.getWorld().getName() : "world");

        sec.set("owner", warp.getOwner().toString());
        sec.set("name", warp.getName());
        sec.set("world", worldName);
        sec.set("x", loc.getX());
        sec.set("y", loc.getY());
        sec.set("z", loc.getZ());
        sec.set("yaw", loc.getYaw());
        sec.set("pitch", loc.getPitch());

        sec.set("description", warp.getDescription());
        sec.set("category", warp.getCategory());
        sec.set("locked", warp.isLocked());
        sec.set("cost", warp.getCost());

        sec.set("icon", warp.getIcon());

        sec.set("whitelist.enabled", warp.isWhitelistEnabled());
        List<String> wl = warp.getWhitelist().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        sec.set("whitelist.players", wl);

        List<String> managers = warp.getManagers().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        sec.set("managers", managers);

        sec.set("password", warp.getPassword());
    }


    @Override
    public synchronized void save(PlayerWarp warp) {
        byId.put(warp.getId(), warp);

        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) {
            root = config.createSection(ROOT);
        }

        toSection(warp.getId(), warp, root);
        saveFile();
    }

    @Override
    public synchronized PlayerWarp getById(String id) {
        return byId.get(id);
    }

    @Override
    public synchronized PlayerWarp getByOwnerAndName(UUID owner, String nameLower) {
        if (owner == null || nameLower == null) return null;
        String search = nameLower.toLowerCase(Locale.ROOT);

        for (PlayerWarp warp : byId.values()) {
            if (warp.getOwner().equals(owner)
                    && warp.getName().equalsIgnoreCase(search)) {
                return warp;
            }
        }
        return null;
    }

    @Override
    public synchronized boolean delete(String id) {
        boolean existed = (byId.remove(id) != null);

        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root != null && root.getConfigurationSection(id) != null) {
            root.set(id, null);
            saveFile();
        }
        return existed;
    }

    @Override
    public synchronized List<PlayerWarp> listByOwner(UUID owner) {
        if (owner == null) return Collections.emptyList();
        return byId.values().stream()
                .filter(w -> w.getOwner().equals(owner))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<PlayerWarp> listAll() {
        return new ArrayList<>(byId.values());
    }
}
