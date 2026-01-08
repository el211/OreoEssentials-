package fr.elias.oreoEssentials.scoreboard;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScoreboardConfig {

    private final boolean enabled;
    private final boolean defaultEnabled;
    private final long updateTicks;
    private final List<String> titleFrames;
    private final long titleFrameTicks;
    private final List<String> lines;
    private final Set<String> worldsWhitelist;
    private final Set<String> worldsBlacklist;

    private ScoreboardConfig(
            boolean enabled,
            boolean defaultEnabled,
            long updateTicks,
            List<String> titleFrames,
            long titleFrameTicks,
            List<String> lines,
            Set<String> wl,
            Set<String> bl
    ) {
        this.enabled = enabled;
        this.defaultEnabled = defaultEnabled;
        this.updateTicks = updateTicks;
        this.titleFrames = titleFrames;
        this.titleFrameTicks = titleFrameTicks;
        this.lines = lines;
        this.worldsWhitelist = wl;
        this.worldsBlacklist = bl;
    }

    public static ScoreboardConfig load(OreoEssentials plugin) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("scoreboard");
        if (root == null) {
            return new ScoreboardConfig(
                    false, true, 20L,
                    List.of("&d&lOreo&f&lEssentials"),
                    10L,
                    List.of("&7Player: &f{player}", "&7Money: &f%vault_eco_balance_formatted%"),
                    Set.of(),
                    Set.of()
            );
        }

        boolean enabled = plugin.getSettingsConfig().scoreboardEnabled();
        boolean defaultEnabled = root.getBoolean("default-enabled", true);
        long updateTicks = Math.max(1L, root.getLong("update-ticks", 20L));

        ConfigurationSection titleSec = root.getConfigurationSection("title");
        List<String> frames = new ArrayList<>();
        long frameTicks = 10L;
        if (titleSec != null) {
            frameTicks = Math.max(1L, titleSec.getLong("frame-ticks", 10L));
            List<String> cfgFrames = titleSec.getStringList("frames");
            if (cfgFrames != null && !cfgFrames.isEmpty()) frames.addAll(cfgFrames);
        }
        if (frames.isEmpty()) frames.add("&d&lOreo&f&lEssentials");

        List<String> lines = root.getStringList("lines");
        if (lines == null || lines.isEmpty()) {
            lines = List.of("&7Player: &f{player}", "&7Online: &f%server_online%");
        }

        Set<String> wl = new HashSet<>(root.getStringList("worlds.whitelist"));
        Set<String> bl = new HashSet<>(root.getStringList("worlds.blacklist"));

        return new ScoreboardConfig(
                enabled, defaultEnabled, updateTicks,
                List.copyOf(frames), frameTicks, List.copyOf(lines),
                Set.copyOf(wl), Set.copyOf(bl)
        );
    }

    public boolean enabled() { return enabled; }
    public boolean defaultEnabled() { return defaultEnabled; }
    public long updateTicks() { return updateTicks; }
    public List<String> titleFrames() { return titleFrames; }
    public long titleFrameTicks() { return titleFrameTicks; }
    public List<String> lines() { return lines; }
    public Set<String> worldsWhitelist() { return worldsWhitelist; }
    public Set<String> worldsBlacklist() { return worldsBlacklist; }
}