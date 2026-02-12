package fr.elias.oreoEssentials.modules.portals;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortalsManager {

    public static class Portal {
        public final String name;
        public final World world;
        public final BoundingBox box;
        public final Location destination;
        public final boolean keepYawPitch;
        public final String permission;

        public Portal(String name, World world, BoundingBox box, Location destination, boolean keepYawPitch, String permission) {
            this.name = name;
            this.world = world;
            this.box = box;
            this.destination = destination;
            this.keepYawPitch = keepYawPitch;
            this.permission = permission;
        }

        public boolean contains(Location loc) {
            return loc != null
                    && loc.getWorld() != null
                    && loc.getWorld().equals(world)
                    && box.contains(loc.toVector());
        }

        public boolean hasPermission(Player p) {
            return permission == null || permission.isEmpty() || p.hasPermission(permission);
        }
    }

    private final OreoEssentials plugin;
    private final File file;
    private final FileConfiguration cfg;

    private final Map<String, Portal> portals = new ConcurrentHashMap<>(64);
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPortalDenied = new ConcurrentHashMap<>(); // prevent spam

    private final boolean enabled;
    private final long cooldownMs;
    private final String soundName;
    private final String particleName;
    private final int particleCount;
    private final boolean allowKeepYawPitch;
    private final boolean teleportAsync;
    private final int maxPortalVolume;

    public PortalsManager(OreoEssentials plugin) {
        this.plugin = plugin;

        FileConfiguration c = plugin.getConfig();
        this.enabled = plugin.getSettings().portalsEnabled();
        this.cooldownMs = c.getLong("portals.cooldown_ms", 1000L);
        this.soundName = c.getString("portals.sound", "ENTITY_ENDERMAN_TELEPORT");
        this.particleName = c.getString("portals.particle", "PORTAL");
        this.particleCount = c.getInt("portals.particle_count", 20);
        this.allowKeepYawPitch = c.getBoolean("portals.allow_keep_yaw_pitch", true);
        this.teleportAsync = c.getBoolean("portals.teleport_async", false);
        this.maxPortalVolume = c.getInt("portals.max_portal_volume", 100000);

        this.file = new File(plugin.getDataFolder(), "portals.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create portals.yml: " + e.getMessage());
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    public Set<String> listNames() {
        return new TreeSet<>(portals.keySet());
    }

    public void setPos1(Player p, Location l) {
        pos1.put(p.getUniqueId(), l.clone());
    }

    public void setPos2(Player p, Location l) {
        pos2.put(p.getUniqueId(), l.clone());
    }

    public Location getPos1(Player p) {
        return pos1.get(p.getUniqueId());
    }

    public Location getPos2(Player p) {
        return pos2.get(p.getUniqueId());
    }

    public Portal get(String name) {
        return portals.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Create a portal with permission support.
     * @return null on success, error message on failure
     */
    public String create(String name, Player creator, Location dest, boolean keepYawPitch, String permission) {
        Location a = pos1.get(creator.getUniqueId());
        Location b = pos2.get(creator.getUniqueId());

        if (a == null || b == null) {
            return "Both pos1 and pos2 must be set";
        }

        if (a.getWorld() == null || b.getWorld() == null) {
            return "Invalid world for pos1 or pos2";
        }

        if (!a.getWorld().equals(b.getWorld())) {
            return "pos1 and pos2 must be in the same world";
        }

        World w = a.getWorld();
        BoundingBox box = BoundingBox.of(a, b);

        double volume = box.getVolume();
        if (volume > maxPortalVolume) {
            return "Portal too large (max volume: " + maxPortalVolume + " blocks)";
        }

        if (volume < 1) {
            return "Portal too small (minimum 1 block)";
        }

        String key = name.toLowerCase(Locale.ROOT);

        if (portals.containsKey(key)) {
            return "Portal with that name already exists";
        }

        Portal portal = new Portal(name, w, box, dest.clone(), keepYawPitch, permission);
        portals.put(key, portal);

        savePortal(portal);
        saveFile();

        pos1.remove(creator.getUniqueId());
        pos2.remove(creator.getUniqueId());

        return null;
    }

    public boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!portals.containsKey(key)) return false;
        portals.remove(key);
        cfg.set("portals." + key, null);
        saveFile();
        return true;
    }

    public void loadAll() {
        portals.clear();
        ConfigurationSection root = cfg.getConfigurationSection("portals");
        if (root == null) return;

        int loaded = 0;
        int failed = 0;

        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) {
                    failed++;
                    continue;
                }

                String worldName = s.getString("world");
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    plugin.getLogger().warning("Portal '" + key + "' world not loaded: " + worldName);
                    failed++;
                    continue;
                }

                double x1 = s.getDouble("box.x1");
                double y1 = s.getDouble("box.y1");
                double z1 = s.getDouble("box.z1");
                double x2 = s.getDouble("box.x2");
                double y2 = s.getDouble("box.y2");
                double z2 = s.getDouble("box.z2");
                BoundingBox box = BoundingBox.of(new Location(w, x1, y1, z1), new Location(w, x2, y2, z2));

                String dw = s.getString("dest.world");
                World dworld = Bukkit.getWorld(dw);
                if (dworld == null) {
                    plugin.getLogger().warning("Portal '" + key + "' destination world not loaded: " + dw);
                    failed++;
                    continue;
                }

                double dx = s.getDouble("dest.x");
                double dy = s.getDouble("dest.y");
                double dz = s.getDouble("dest.z");
                float yaw = (float) s.getDouble("dest.yaw", 0f);
                float pitch = (float) s.getDouble("dest.pitch", 0f);
                boolean keep = s.getBoolean("keepYawPitch", false);
                String perm = s.getString("permission", null);

                Location dest = new Location(dworld, dx, dy, dz, yaw, pitch);

                portals.put(key.toLowerCase(Locale.ROOT),
                        new Portal(s.getString("name", key), w, box, dest, keep, perm));
                loaded++;
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load portal " + key + ": " + t.getMessage());
                failed++;
            }
        }

        plugin.getLogger().info("[Portals] Loaded " + loaded + " portal(s)" +
                (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    private void savePortal(Portal p) {
        String key = p.name.toLowerCase(Locale.ROOT);
        String base = "portals." + key + ".";
        cfg.set(base + "name", p.name);
        cfg.set(base + "world", p.world.getName());
        cfg.set(base + "box.x1", p.box.getMinX());
        cfg.set(base + "box.y1", p.box.getMinY());
        cfg.set(base + "box.z1", p.box.getMinZ());
        cfg.set(base + "box.x2", p.box.getMaxX());
        cfg.set(base + "box.y2", p.box.getMaxY());
        cfg.set(base + "box.z2", p.box.getMaxZ());
        cfg.set(base + "dest.world", p.destination.getWorld().getName());
        cfg.set(base + "dest.x", p.destination.getX());
        cfg.set(base + "dest.y", p.destination.getY());
        cfg.set(base + "dest.z", p.destination.getZ());
        cfg.set(base + "dest.yaw", p.destination.getYaw());
        cfg.set(base + "dest.pitch", p.destination.getPitch());
        cfg.set(base + "keepYawPitch", p.keepYawPitch);
        if (p.permission != null && !p.permission.isEmpty()) {
            cfg.set(base + "permission", p.permission);
        }
    }

    private void saveFile() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save portals.yml: " + e.getMessage());
        }
    }

    /** Teleport if inside any portal (optimized O(n) scan). */
    public void tickMove(Player p, Location to) {
        if (!enabled || to == null || p == null) return;

        long now = System.currentTimeMillis();
        UUID pid = p.getUniqueId();

        Long cd = cooldown.get(pid);
        if (cd != null && now < cd) return;

        for (Portal portal : portals.values()) {
            if (portal.contains(to)) {
                if (!portal.hasPermission(p)) {
                    String lastDenied = lastPortalDenied.get(pid);
                    if (!portal.name.equals(lastDenied)) {
                        Lang.send(p, "portals.no-portal-permission",
                                "<red>You don't have permission to use this portal.</red>");
                        lastPortalDenied.put(pid, portal.name);

                        // Clear denial cache after 5 seconds
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> lastPortalDenied.remove(pid, portal.name),
                                100L);
                    }
                    return;
                }

                Location dest = portal.destination.clone();
                if (allowKeepYawPitch && portal.keepYawPitch) {
                    dest.setYaw(p.getLocation().getYaw());
                    dest.setPitch(p.getLocation().getPitch());
                }

                playEffects(p, to);

                cooldown.put(pid, now + Math.max(0, cooldownMs));

                lastPortalDenied.remove(pid);

                if (teleportAsync) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.teleport(dest));
                } else {
                    p.teleport(dest);
                }

                return;
            }
        }
    }

    private void playEffects(Player p, Location loc) {
        try {
            // Sound
            if (soundName != null && !soundName.isEmpty()) {
                try {
                    Sound sound = Sound.valueOf(soundName);
                    p.getWorld().playSound(loc, sound, 1f, 1f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound name in config: " + soundName);
                }
            }

            if (particleName != null && !particleName.isEmpty() && particleCount > 0) {
                try {
                    Particle particle = Particle.valueOf(particleName);
                    p.getWorld().spawnParticle(
                            particle,
                            loc, particleCount,
                            0.25, 0.5, 0.25, 0.01
                    );
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid particle name in config: " + particleName);
                }
            }
        } catch (Throwable t) {
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}