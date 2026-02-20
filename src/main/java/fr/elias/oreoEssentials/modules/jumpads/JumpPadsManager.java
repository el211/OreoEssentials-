package fr.elias.oreoEssentials.modules.jumpads;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class JumpPadsManager {

    public static class JumpPad {
        public final String name;
        public final World world;
        public final int x, y, z;   // block coords
        public double power;
        public double upward;
        public boolean useLookDir;

        public JumpPad(String name, World world, int x, int y, int z, double power, double upward, boolean useLookDir) {
            this.name = name;
            this.world = world;
            this.x = x; this.y = y; this.z = z;
            this.power = power;
            this.upward = upward;
            this.useLookDir = useLookDir;
        }

        public boolean isAt(Location l) {
            return l != null && l.getWorld() == world && l.getBlockX()==x && l.getBlockY()==y && l.getBlockZ()==z;
        }

        public Vector launchVector(Player p) {
            Vector base;
            if (useLookDir) {
                base = p.getLocation().getDirection().setY(0).normalize();
                if (base.lengthSquared() == 0) base = new Vector(0,0,1);
            } else {
                // default: straight up with small push forward (facing)
                base = p.getLocation().getDirection().setY(0).normalize();
                if (base.lengthSquared() == 0) base = new Vector(0,0,1);
            }
            base.multiply(power);
            base.setY(upward);
            return base;
        }

        public String key() { return world.getName()+":"+x+":"+y+":"+z; }
    }

    private final OreoEssentials plugin;
    private final File file;
    private final FileConfiguration cfg;

    private final Map<String, JumpPad> byName = new ConcurrentHashMap<>();
    private final Map<String, JumpPad> byBlock = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    // Config-backed options
    private final boolean enabled;
    private final long cooldownMs;
    private final String soundName;
    private final String particleName;
    private final int particleCount;

    // Defaults exposed so the command can use them when args are omitted
    public final double defaultPower;
    public final double defaultUpward;
    public final boolean defaultUseLookDir;

    public JumpPadsManager(OreoEssentials plugin) {
        this.plugin = plugin;

        // Read config (with sane defaults)
        FileConfiguration c = plugin.getConfig();
        this.enabled = plugin.getSettings().jumpPadsEnabled();
        this.cooldownMs = c.getLong("jumpads.cooldown_ms", 800L);
        this.soundName = c.getString("jumpads.sound", "ENTITY_FIREWORK_ROCKET_LAUNCH");
        this.particleName = c.getString("jumpads.particle", "CLOUD");
        this.particleCount = c.getInt("jumpads.particle_count", 12);
        this.defaultPower = c.getDouble("jumpads.default_power", 1.2);
        this.defaultUpward = c.getDouble("jumpads.default_upward", 1.0);
        this.defaultUseLookDir = c.getBoolean("jumpads.default_useLookDir", true);

        // Data file
        this.file = new File(plugin.getDataFolder(), "jumpads.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create jumpads.yml: " + e.getMessage());
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    public Set<String> listNames() {
        return new TreeSet<>(byName.keySet());
    }

    public JumpPad getByName(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean create(String name, Location blockLoc, double power, double upward, boolean useLookDir) {
        if (blockLoc == null || blockLoc.getWorld() == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        JumpPad jp = new JumpPad(name, blockLoc.getWorld(),
                blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(),
                power, upward, useLookDir);
        byName.put(key, jp);
        byBlock.put(jp.key(), jp);
        saveJumpPad(jp);
        saveFile();
        return true;
    }

    public boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        JumpPad jp = byName.remove(key);
        if (jp == null) return false;
        byBlock.remove(jp.key());
        cfg.set("jumpads." + key, null);
        saveFile();
        return true;
    }

    public void loadAll() {
        byName.clear();
        byBlock.clear();
        ConfigurationSection root = cfg.getConfigurationSection("jumpads");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                String world = s.getString("world");
                World w = Bukkit.getWorld(world);
                if (w == null) continue;
                int x = s.getInt("x"), y = s.getInt("y"), z = s.getInt("z");
                double power = s.getDouble("power", 1.0);
                double upward = s.getDouble("upward", 1.0);
                boolean look = s.getBoolean("useLookDir", true);
                JumpPad jp = new JumpPad(s.getString("name", key), w, x, y, z, power, upward, look);
                byName.put(key.toLowerCase(Locale.ROOT), jp);
                byBlock.put(jp.key(), jp);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load jumpad " + key + ": " + t.getMessage());
            }
        }
    }

    private void saveJumpPad(JumpPad jp) {
        String key = jp.name.toLowerCase(Locale.ROOT);
        String base = "jumpads." + key + ".";
        cfg.set(base + "name", jp.name);
        cfg.set(base + "world", jp.world.getName());
        cfg.set(base + "x", jp.x);
        cfg.set(base + "y", jp.y);
        cfg.set(base + "z", jp.z);
        cfg.set(base + "power", jp.power);
        cfg.set(base + "upward", jp.upward);
        cfg.set(base + "useLookDir", jp.useLookDir);
    }

    private void saveFile() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save jumpads.yml: " + e.getMessage());
        }
    }

    /**
     * Called from listener when player moves.
     * Checks if player is standing on a jump pad and launches them.
     */
    public void tryLaunch(Player p) {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        Long cd = cooldown.get(p.getUniqueId());
        if (cd != null && now < cd) return;

        Location under = p.getLocation().clone().subtract(0, 1, 0);
        if (under.getWorld() == null) return;
        String key = under.getWorld().getName()+":"+under.getBlockX()+":"+under.getBlockY()+":"+under.getBlockZ();
        JumpPad jp = byBlock.get(key);
        if (jp == null) return;

        // Launch player
        p.setVelocity(jp.launchVector(p));

        // Effects from config (using modern Registry API)
        try {
            if (soundName != null && !soundName.isEmpty()) {
                // Convert common formats: "ENTITY_FIREWORK_ROCKET_LAUNCH" -> "entity_firework_rocket_launch"
                String soundKey = soundName.toLowerCase(Locale.ROOT).replace("_", "_");

                // Try with minecraft namespace
                Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));

                // If not found, try parsing as namespaced key (e.g., "minecraft:entity_firework_rocket_launch")
                if (sound == null && soundName.contains(":")) {
                    try {
                        NamespacedKey nsKey = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
                        if (nsKey != null) {
                            sound = Registry.SOUNDS.get(nsKey);
                        }
                    } catch (Exception ignored) {}
                }

                if (sound != null) {
                    p.getWorld().playSound(p.getLocation(), sound, 1f, 1.2f);
                }
            }

            if (particleName != null && !particleName.isEmpty() && particleCount > 0) {
                // Convert common formats: "CLOUD" -> "cloud"
                String particleKey = particleName.toLowerCase(Locale.ROOT).replace("_", "_");

                // Try with minecraft namespace
                Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key));

                // If not found, try parsing as namespaced key
                if (particle == null && particleName.contains(":")) {
                    try {
                        NamespacedKey nsKey = NamespacedKey.fromString(particleName.toLowerCase(Locale.ROOT));
                        if (nsKey != null) {
                            particle = Registry.PARTICLE_TYPE.get(nsKey);
                        }
                    } catch (Exception ignored) {}
                }

                if (particle != null) {
                    p.getWorld().spawnParticle(
                            particle,
                            p.getLocation(), particleCount,
                            0.2, 0.1, 0.2, 0.02
                    );
                }
            }
        } catch (Exception ignored) {
            // Any errors with effects, just skip them
        }

        cooldown.put(p.getUniqueId(), now + Math.max(0, cooldownMs));
    }
}