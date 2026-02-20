package fr.elias.oreoEssentials.modules.ic;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ICManager {
    private final File file;
    private final Map<String, ICEntry> map = new LinkedHashMap<>();

    public ICManager(File dataFolder) {
        this.file = new File(dataFolder, "interactive-commands.yml");
        if (!file.exists()) {
            try {
                YamlConfiguration y = new YamlConfiguration();
                ConfigurationSection ic = y.createSection("ic");
                ConfigurationSection demo = ic.createSection("healer");
                demo.set("public", false);
                demo.set("cmds", List.of(
                        "asConsole! delay! 1 effect give [playerName] regeneration 5 1",
                        "asConsole! title [playerName] actionbar {\"text\":\"Healed!\",\"color\":\"green\"}"
                ));
                demo.set("blocks", List.of("world;100;64;200"));
                demo.set("entities", List.of());
                y.save(file);
                Bukkit.getLogger().info("[OreoEssentials-IC] Wrote interactive-commands.yml skeleton.");
            } catch (IOException ioe) {
                Bukkit.getLogger().warning("[OreoEssentials-IC] Failed to write skeleton: " + ioe.getMessage());
            }
        }
        reload();
    }

    public void reload() {
        map.clear();
        if (!file.exists()) {
            Bukkit.getLogger().warning("[OreoEssentials-IC] interactive-commands.yml not found.");
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cs = y.getConfigurationSection("ic");
        if (cs == null) {
            Bukkit.getLogger().warning("[OreoEssentials-IC] Missing root 'ic:' section.");
            return;
        }
        int loaded = 0;
        for (String name : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(name);
            if (c == null) continue;
            ICEntry e = new ICEntry(name);
            e.isPublic = c.getBoolean("public", false);
            for (String s : c.getStringList("cmds")) e.commands.add(s);
            for (String s : c.getStringList("blocks")) {
                try {
                    String[] p = s.split(";", 4);
                    if (p.length != 4) {
                        Bukkit.getLogger().warning("[OreoEssentials-IC] Skipping bad block entry '" + s + "' for " + name);
                        continue;
                    }
                    e.blocks.add(new ICPos(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                } catch (Exception ex) {
                    Bukkit.getLogger().warning("[OreoEssentials-IC] Skipping block '" + s + "' for " + name + ": " + ex.getMessage());
                }
            }
            for (String s : c.getStringList("entities")) {
                try { e.entities.add(UUID.fromString(s)); }
                catch (Exception ex) {
                    Bukkit.getLogger().warning("[OreoEssentials-IC] Skipping entity UUID '" + s + "' for " + name);
                }
            }
            map.put(name.toLowerCase(Locale.ROOT), e);
            loaded++;
        }
        Bukkit.getLogger().info("[OreoEssentials-IC] Loaded " + loaded + " interactive command(s).");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("ic");
        for (ICEntry e : map.values()) {
            ConfigurationSection c = root.createSection(e.name);
            c.set("public", e.isPublic);
            c.set("cmds", e.commands);
            List<String> bl = new ArrayList<>();
            for (ICPos p : e.blocks) bl.add(p.world + ";" + p.x + ";" + p.y + ";" + p.z);
            c.set("blocks", bl);
            List<String> el = new ArrayList<>();
            for (UUID u : e.entities) el.add(u.toString());
            c.set("entities", el);
        }
        try {
            y.save(file);
            Bukkit.getLogger().info("[OreoEssentials-IC] Saved interactive-commands.yml (" + map.size() + " entries).");
        } catch (IOException ex) {
            Bukkit.getLogger().warning("[OreoEssentials-IC] Failed to save: " + ex.getMessage());
        }
    }

    public ICEntry get(String name) { return map.get(name.toLowerCase(Locale.ROOT)); }

    public ICEntry create(String name) {
        ICEntry e = new ICEntry(name);
        map.put(name.toLowerCase(Locale.ROOT), e);
        save();
        return e;
    }

    public Collection<ICEntry> all() { return map.values(); }
}