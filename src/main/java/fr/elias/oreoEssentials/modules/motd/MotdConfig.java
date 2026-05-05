package fr.elias.oreoEssentials.modules.motd;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MotdConfig {

    public record MotdGroup(String permission, List<String> lines) {}

    private final Plugin plugin;
    private final File   file;

    private boolean       enabled;
    private long          delayTicks;
    private List<String>  defaultLines;
    private boolean       firstJoinEnabled;
    private List<String>  firstJoinLines;
    private List<MotdGroup> groups;

    public MotdConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "server/motd.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("server/motd.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled         = cfg.getBoolean("motd.enabled", true);
        delayTicks      = Math.max(0, cfg.getLong("motd.delay-ticks", 20));
        defaultLines    = cfg.getStringList("motd.default");
        firstJoinEnabled= cfg.getBoolean("motd.first-join.enabled", true);
        firstJoinLines  = cfg.getStringList("motd.first-join.lines");

        groups = new ArrayList<>();
        List<?> rawGroups = cfg.getList("motd.groups", Collections.emptyList());
        for (Object obj : rawGroups) {
            if (obj instanceof ConfigurationSection sec) {
                String perm = sec.getString("permission", "");
                List<String> lines = sec.getStringList("lines");
                if (!perm.isEmpty()) groups.add(new MotdGroup(perm, lines));
            } else if (obj instanceof java.util.Map<?,?> map) {
                String perm = str(map, "permission");
                @SuppressWarnings("unchecked")
                List<String> lines = map.get("lines") instanceof List<?> l
                        ? (List<String>) l : Collections.emptyList();
                if (!perm.isEmpty()) groups.add(new MotdGroup(perm, lines));
            }
        }
    }

    public boolean isEnabled()             { return enabled; }
    public long    delayTicks()            { return delayTicks; }
    public List<String> defaultLines()     { return defaultLines; }
    public boolean isFirstJoinEnabled()    { return firstJoinEnabled; }
    public List<String> firstJoinLines()   { return firstJoinLines; }
    public List<MotdGroup> groups()        { return groups; }

    private static String str(java.util.Map<?,?> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : "";
    }
}
