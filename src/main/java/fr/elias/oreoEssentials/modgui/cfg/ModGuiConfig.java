package fr.elias.oreoEssentials.modgui.cfg;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ModGuiConfig {
    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;

    public ModGuiConfig(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            file = new File(plugin.getDataFolder(), "modgui.yml");
            if (!file.exists()) {
                plugin.saveResource("modgui.yml", false);
            }
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Throwable t) {
            plugin.getLogger().warning("[ModGUI] Failed to load modgui.yml: " + t.getMessage());
            cfg = new YamlConfiguration();
        }
        if (!cfg.isConfigurationSection("worlds")) {
            cfg.createSection("worlds");
        }
        if (!cfg.isConfigurationSection("world-tweaks")) {
            cfg.createSection("world-tweaks");
        }
        saveSilently();
    }

    public void save() throws Exception { cfg.save(file); }
    private void saveSilently() { try { save(); } catch (Exception ignored) {} }

    private String wKey(World w) { return "worlds." + w.getName(); }

    public boolean worldWhitelistEnabled(World w) {
        return cfg.getBoolean(wKey(w) + ".whitelist.enabled", false);
    }
    public void setWorldWhitelistEnabled(World w, boolean v) {
        cfg.set(wKey(w) + ".whitelist.enabled", v);
        saveSilently();
    }
    public Set<UUID> worldWhitelist(World w) {
        List<String> list = cfg.getStringList(wKey(w) + ".whitelist.players");
        Set<UUID> out = new HashSet<>();
        for (String s : list) {
            try { out.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        return out;
    }
    public void addWorldWhitelist(World w, UUID id) {
        Set<UUID> cur = worldWhitelist(w);
        cur.add(id);
        writeWorldWhitelist(w, cur);
    }
    public void removeWorldWhitelist(World w, UUID id) {
        Set<UUID> cur = worldWhitelist(w);
        cur.remove(id);
        writeWorldWhitelist(w, cur);
    }
    private void writeWorldWhitelist(World w, Set<UUID> set) {
        List<String> ls = set.stream().map(UUID::toString).toList();
        cfg.set(wKey(w) + ".whitelist.players", ls);
        saveSilently();
    }

    public Set<String> bannedMobs(World w) {
        return new HashSet<>(cfg.getStringList(wKey(w) + ".banned-mobs"));
    }
    public void toggleMobBan(World w, String mobKey) {
        Set<String> s = bannedMobs(w);
        if (s.contains(mobKey)) s.remove(mobKey); else s.add(mobKey);
        cfg.set(wKey(w) + ".banned-mobs", new ArrayList<>(s));
        saveSilently();
    }
    public boolean isMobBanned(World w, String mobKey) {
        return bannedMobs(w).contains(mobKey);
    }

    public String gamerule(World w, String key, String def) {
        return cfg.getString(wKey(w) + ".gamerules." + key, def);
    }
    public void setGamerule(World w, String key, String value) {
        cfg.set(wKey(w) + ".gamerules." + key, value);
        saveSilently();
    }

    public boolean tweak(World world, String key, boolean def) {
        String path = "world-tweaks." + world.getName() + "." + key;
        return cfg.getBoolean(path, def);
    }

    public void setTweak(World world, String key, boolean value) {
        String path = "world-tweaks." + world.getName() + "." + key;
        cfg.set(path, value);
        saveSilently();
    }


    public boolean netherAllowWater(World w) {
        return tweak(w, "allow-water", false);
    }
    public void setNetherAllowWater(World w, boolean v) {
        setTweak(w, "allow-water", v);
    }

    public boolean netherAllowBeds(World w) {
        return tweak(w, "allow-beds", false);
    }
    public void setNetherAllowBeds(World w, boolean v) {
        setTweak(w, "allow-beds", v);
    }

    public boolean netherNoFireDamage(World w) {
        return tweak(w, "no-fire-damage", false);
    }
    public void setNetherNoFireDamage(World w, boolean v) {
        setTweak(w, "no-fire-damage", v);
    }

    public boolean netherNoGhastGrief(World w) {
        return tweak(w, "no-ghast-grief", false);
    }
    public void setNetherNoGhastGrief(World w, boolean v) {
        setTweak(w, "no-ghast-grief", v);
    }

    public boolean endVoidTeleport(World w) {
        return tweak(w, "void-teleport", false);
    }
    public void setEndVoidTeleport(World w, boolean v) {
        setTweak(w, "void-teleport", v);
    }

    public boolean endNoEndermanGrief(World w) {
        return tweak(w, "no-enderman-grief", false);
    }
    public void setEndNoEndermanGrief(World w, boolean v) {
        setTweak(w, "no-enderman-grief", v);
    }

    public boolean endNoDragonGrief(World w) {
        return tweak(w, "no-dragon-grief", false);
    }
    public void setEndNoDragonGrief(World w, boolean v) {
        setTweak(w, "no-dragon-grief", v);
    }
    // generic world flag
    public boolean worldFlag(World w, String key, boolean def) {
        return cfg.getBoolean(wKey(w) + ".flags." + key, def);
    }
    public void setWorldFlag(World w, String key, boolean value) {
        cfg.set(wKey(w) + ".flags." + key, value);
        saveSilently();
    }

    public boolean disableElytra(World w) { return worldFlag(w, "disable-elytra", false); }
    public void setDisableElytra(World w, boolean v) { setWorldFlag(w, "disable-elytra", v); }

    public boolean disableTrident(World w) { return worldFlag(w, "disable-trident", false); }
    public void setDisableTrident(World w, boolean v) { setWorldFlag(w, "disable-trident", v); }

    public boolean pvpEnabled(World w) { return !worldFlag(w, "disable-pvp", false); }
    public void setPvpEnabled(World w, boolean enabled) { setWorldFlag(w, "disable-pvp", !enabled); }

    public boolean disableProjectilePvp(World w) { return worldFlag(w, "disable-projectile-pvp", false); }
    public void setDisableProjectilePvp(World w, boolean v) { setWorldFlag(w, "disable-projectile-pvp", v); }

    public String worldTheme(World w) {
        return cfg.getString(wKey(w) + ".theme", "DEFAULT");
    }
    public void setWorldTheme(World w, String theme) {
        cfg.set(wKey(w) + ".theme", theme.toUpperCase(Locale.ROOT));
        saveSilently();
    }

}
