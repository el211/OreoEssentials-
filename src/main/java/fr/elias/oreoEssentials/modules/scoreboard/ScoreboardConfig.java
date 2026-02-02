package fr.elias.oreoEssentials.modules.scoreboard;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;


public final class ScoreboardConfig {


    private static final boolean DEF_DEFAULT_ENABLED = true;
    private static final long DEF_UPDATE_TICKS = 20L;

    private static final long DEF_TITLE_FRAME_TICKS = 10L;
    private static final List<String> DEF_TITLE_FRAMES = List.of(
            "&d&lOreo&f&lEssentials"
    );

    private static final List<String> DEF_LINES = List.of(
            "&7Player: &f{player}",
            "&7Money: &f%vault_eco_balance_formatted%"
    );

    private static final Set<String> DEF_EMPTY_SET = Set.of();


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

        // You already control the "enabled" toggle via SettingsConfig, so always respect that:
        boolean enabled = plugin.getSettingsConfig().scoreboardEnabled();

        if (root == null) {
            return defaults(enabled);
        }

        boolean defaultEnabled = root.getBoolean("default-enabled", DEF_DEFAULT_ENABLED);
        long updateTicks = Math.max(1L, root.getLong("update-ticks", DEF_UPDATE_TICKS));

        // Title
        ConfigurationSection titleSec = root.getConfigurationSection("title");
        long frameTicks = DEF_TITLE_FRAME_TICKS;
        List<String> frames = new ArrayList<>(DEF_TITLE_FRAMES);

        if (titleSec != null) {
            frameTicks = Math.max(1L, titleSec.getLong("frame-ticks", DEF_TITLE_FRAME_TICKS));
            List<String> cfgFrames = titleSec.getStringList("frames");
            if (cfgFrames != null && !cfgFrames.isEmpty()) {
                frames = new ArrayList<>(cfgFrames);
            }
        }
        if (frames.isEmpty()) frames = new ArrayList<>(DEF_TITLE_FRAMES);

        List<String> lines = root.getStringList("lines");
        if (lines == null || lines.isEmpty()) {
            lines = new ArrayList<>(DEF_LINES);
        }

        Set<String> wl = toSet(root.getStringList("worlds.whitelist"));
        Set<String> bl = toSet(root.getStringList("worlds.blacklist"));

        return new ScoreboardConfig(
                enabled,
                defaultEnabled,
                updateTicks,
                List.copyOf(frames),
                frameTicks,
                List.copyOf(lines),
                Set.copyOf(wl),
                Set.copyOf(bl)
        );
    }

    private static ScoreboardConfig defaults(boolean enabled) {
        return new ScoreboardConfig(
                enabled,
                DEF_DEFAULT_ENABLED,
                DEF_UPDATE_TICKS,
                DEF_TITLE_FRAMES,
                DEF_TITLE_FRAME_TICKS,
                DEF_LINES,
                DEF_EMPTY_SET,
                DEF_EMPTY_SET
        );
    }

    private static Set<String> toSet(List<String> list) {
        if (list == null || list.isEmpty()) return new HashSet<>();
        return new HashSet<>(list);
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
