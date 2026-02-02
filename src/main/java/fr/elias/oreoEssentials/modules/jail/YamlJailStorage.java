package fr.elias.oreoEssentials.modules.jail;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public final class YamlJailStorage implements JailStorage {
    private final Plugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public YamlJailStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jails.yml");
    }

    private void ensureLoaded() {
        if (cfg != null) return;
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (Exception ignored) {}
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public Map<String, JailModels.Jail> loadJails() {
        ensureLoaded();
        Map<String, JailModels.Jail> out = new HashMap<>();
        ConfigurationSection root = cfg.getConfigurationSection("jails");
        if (root == null) return out;
        for (String name : root.getKeys(false)) {
            ConfigurationSection j = root.getConfigurationSection(name);
            if (j == null) continue;

            JailModels.Jail jail = new JailModels.Jail();
            jail.name = name;
            jail.world = j.getString("world", "");
            ConfigurationSection r = j.getConfigurationSection("region");
            if (r != null) {
                JailModels.Cuboid c = new JailModels.Cuboid();
                c.x1 = r.getDouble("x1"); c.y1 = r.getDouble("y1"); c.z1 = r.getDouble("z1");
                c.x2 = r.getDouble("x2"); c.y2 = r.getDouble("y2"); c.z2 = r.getDouble("z2");
                jail.region = c;
            }
            // cells
            ConfigurationSection cs = j.getConfigurationSection("cells");
            if (cs != null) {
                for (String id : cs.getKeys(false)) {
                    List<Double> v = cs.getDoubleList(id);
                    if (v.size() >= 5) {
                        Location loc = JailModels.loc(
                                jail.world,
                                v.get(0), v.get(1), v.get(2),
                                v.get(3).floatValue(), v.get(4).floatValue());
                        if (loc != null) jail.cells.put(id, loc);
                    }
                }
            }
            if (jail.isValid()) out.put(jail.name.toLowerCase(Locale.ROOT), jail);
        }
        return out;
    }

    @Override
    public void saveJails(Map<String, JailModels.Jail> all) {
        ensureLoaded();
        cfg.set("jails", null);
        for (JailModels.Jail j : all.values()) {
            String base = "jails." + j.name;
            cfg.set(base + ".world", j.world);
            if (j.region != null) {
                cfg.set(base + ".region.x1", j.region.x1);
                cfg.set(base + ".region.y1", j.region.y1);
                cfg.set(base + ".region.z1", j.region.z1);
                cfg.set(base + ".region.x2", j.region.x2);
                cfg.set(base + ".region.y2", j.region.y2);
                cfg.set(base + ".region.z2", j.region.z2);
            }
            for (Map.Entry<String, Location> e : j.cells.entrySet()) {
                Location l = e.getValue();
                List<Double> arr = Arrays.asList(l.getX(), l.getY(), l.getZ(), (double) l.getYaw(), (double) l.getPitch());
                cfg.set(base + ".cells." + e.getKey(), arr);
            }
        }
        try { cfg.save(file); } catch (Exception ex) {
            plugin.getLogger().warning("[Jails] Failed to save jails.yml: " + ex.getMessage());
        }
    }

    @Override
    public Map<UUID, JailModels.Sentence> loadSentences() {
        ensureLoaded();
        Map<UUID, JailModels.Sentence> out = new HashMap<>();
        ConfigurationSection root = cfg.getConfigurationSection("sentences");
        if (root == null) return out;
        for (String k : root.getKeys(false)) {
            try {
                UUID u = UUID.fromString(k);
                ConfigurationSection s = root.getConfigurationSection(k);
                JailModels.Sentence sn = new JailModels.Sentence();
                sn.player = u;
                sn.jailName = s.getString("jail");
                sn.cellId = s.getString("cell");
                sn.endEpochMs = s.getLong("end", 0L);
                sn.reason = s.getString("reason", "");
                sn.by = s.getString("by", "console");
                out.put(u, sn);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    @Override
    public void saveSentences(Map<UUID, JailModels.Sentence> sentences) {
        ensureLoaded();
        cfg.set("sentences", null);
        for (JailModels.Sentence s : sentences.values()) {
            String base = "sentences." + s.player;
            cfg.set(base + ".jail", s.jailName);
            cfg.set(base + ".cell", s.cellId);
            cfg.set(base + ".end", s.endEpochMs);
            cfg.set(base + ".reason", s.reason);
            cfg.set(base + ".by", s.by);
        }
        try { cfg.save(file); } catch (Exception ex) {
            plugin.getLogger().warning("[Jails] Failed to save sentences: " + ex.getMessage());
        }
    }

    @Override public void close() { /* noop */ }
}
