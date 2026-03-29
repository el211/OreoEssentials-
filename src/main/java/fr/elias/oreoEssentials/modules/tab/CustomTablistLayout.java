package fr.elias.oreoEssentials.modules.tab;

import fr.elias.oreoEssentials.OreoEssentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomTablistLayout {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // &#RRGGBB → <#RRGGBB>
    private static final Pattern AMP_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // Closing colour tags like </gold>, </aqua> etc. → <reset>
    private static final Pattern CLOSING_COLOR_TAG = Pattern.compile(
            "</(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>",
            Pattern.CASE_INSENSITIVE
    );

    private final OreoEssentials plugin;
    private final TabListManager tabManager;
    private OreTask updateTask;

    private int currentFrame = 0;
    private long lastFrameChange = 0;

    public CustomTablistLayout(OreoEssentials plugin, TabListManager tabManager) {
        this.plugin = plugin;
        this.tabManager = tabManager;
    }

    public void start(int intervalTicks) {
        stop();
        updateTask = OreScheduler.runTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    updateCustomTablist(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("[CustomTab] Error updating tablist for " + player.getName() + ": " + e.getMessage());
                }
            }
        }, 20L, intervalTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void updateCustomTablist(Player viewer) {
        try {
            FileConfiguration cfg = tabManager.getConfig();

            boolean headerAnimated = cfg.contains("tab.custom-layout.top-section.texts");
            boolean footerAnimated = cfg.contains("tab.custom-layout.bottom-section.texts");

            int changeInterval = cfg.getInt("tab.custom-layout.change-interval", 80);
            long currentTime = System.currentTimeMillis();
            long ticksPassed = (currentTime - lastFrameChange) / 50;

            if (ticksPassed >= changeInterval) {
                currentFrame++;
                lastFrameChange = currentTime;
            }

            String headerText;
            if (headerAnimated) {
                headerText = getAnimatedText(cfg, "tab.custom-layout.top-section.texts", viewer);
            } else {
                String line1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-1", "&f&lWelcome &b%player_displayname%"));
                String line2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-2", "&6&lWorld: &e%player_world%"));
                String line3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-3", "&6&lPing: &e%player_ping%ms"));
                headerText = color(line1) + "\n" + color(line2) + "  " + color(line3);
            }

            String footerText;
            if (footerAnimated) {
                footerText = getAnimatedText(cfg, "tab.custom-layout.bottom-section.texts", viewer);
            } else {
                String fLine1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-1", "&6&lPlayers: &e%oe_network_online%"));
                String fLine2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-2", "&6&lBalance: &e$%vault_eco_balance_formatted%"));
                String fLine3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-3", "&c&lServer"));
                footerText = "\n" + color(fLine1) + "  " + color(fLine2) + "\n" + color(fLine3);
            }

            viewer.setPlayerListHeaderFooter(headerText, footerText);
            updatePlayerNames(viewer, cfg);

        } catch (Exception e) {
            plugin.getLogger().warning("[CustomTab] Error updating tablist: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getAnimatedText(FileConfiguration cfg, String path, Player viewer) {
        List<String> frames = cfg.getStringList(path);
        if (frames.isEmpty()) return "";

        int frameIndex = currentFrame % frames.size();
        String frame = frames.get(frameIndex);
        frame = applyPlaceholders(viewer, frame);
        return color(frame);
    }

    private void updatePlayerNames(Player viewer, FileConfiguration cfg) {
        if (!cfg.getBoolean("tab.custom-layout.player-section.enabled", true)) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort((p1, p2) -> Integer.compare(getRankPriority(p2, cfg), getRankPriority(p1, cfg)));

        for (Player target : players) {
            String displayName = formatPlayerName(target, cfg);
            displayName = applyPlaceholders(target, displayName);

            // Normalise the full pipeline into a Component so MiniMessage tags
            // (e.g. from LuckPerms PAPI) render correctly in the tab list.
            try {
                String mm = AMP_HEX.matcher(displayName).replaceAll("<#$1>");
                mm = CLOSING_COLOR_TAG.matcher(mm).replaceAll("<reset>");
                mm = mm.replace('§', '&');
                mm = convertAmpToMiniMessage(mm);
                Component comp = MM.deserialize(mm);
                target.playerListName(comp);
            } catch (Throwable e) {
                // Fallback: legacy string path
                try {
                    target.setPlayerListName(color(displayName));
                } catch (Exception ex) {
                    plugin.getLogger().warning("[CustomTab] Failed to set player list name for " + target.getName());
                }
            }
        }
    }

    private String formatPlayerName(Player player, FileConfiguration cfg) {
        String format = cfg.getString("tab.custom-layout.player-section.player-format.format",
                "%player_color%%player_name%%afk_indicator%");

        String rankColor = getRankColor(player, cfg);

        String afkIndicator = "";
        if (cfg.getBoolean("tab.custom-layout.player-section.player-format.show-afk", true)) {
            if (isPlayerAFK(player)) {
                afkIndicator = color(cfg.getString("tab.custom-layout.player-section.player-format.afk-format", " &7AFK"));
            }
        }

        return format.replace("%player_color%", rankColor)
                .replace("%player_name%", player.getName())
                .replace("%afk_indicator%", afkIndicator);
    }

    private String getRankColor(Player player, FileConfiguration cfg) {
        String rank = getPrimaryGroup(player);
        ConfigurationSection rankColors = cfg.getConfigurationSection("tab.custom-layout.player-section.player-format.rank-colors");
        if (rankColors != null && rankColors.contains(rank)) {
            return color(rankColors.getString(rank, "&a"));
        }
        return color(rankColors != null ? rankColors.getString("default", "&a") : "&a");
    }

    private int getRankPriority(Player player, FileConfiguration cfg) {
        String rank = getPrimaryGroup(player);
        ConfigurationSection priorities = cfg.getConfigurationSection("tab.custom-layout.sorting.rank-priority");
        if (priorities != null && priorities.contains(rank)) {
            return priorities.getInt(rank, 10);
        }
        return 10;
    }

    private String getPrimaryGroup(Player player) {
        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            String result = (String) m.invoke(null, player, "%luckperms_primary_group%");
            return result != null ? result : "default";
        } catch (Throwable e) {
            return "default";
        }
    }

    private boolean isPlayerAFK(Player player) {
        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            String result = (String) m.invoke(null, player, "%essentials_afk%");
            return "yes".equalsIgnoreCase(result);
        } catch (Throwable e) {
            return false;
        }
    }

    private String applyPlaceholders(Player player, String text) {
        if (text == null) return "";

        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_ping%", String.valueOf(player.getPing()));
        text = text.replace("%oe_network_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            text = (String) m.invoke(null, player, text);
        } catch (Throwable ignored) {}

        return text;
    }

    /**
     * Full colour pipeline using MiniMessage — properly renders gradients,
     * hex colours, named colour tags, and legacy & codes.
     *
     * Pipeline:
     * 1. &#RRGGBB  →  <#RRGGBB>  (normalise amp-hex to MiniMessage)
     * 2. </gold>, </aqua> etc.  →  <reset>  (fix unsupported closing colour tags)
     * 3. &l, &o, &b etc.  →  MiniMessage equivalents  (so & codes work inside gradient tags)
     * 4. MiniMessage parse  →  legacy §-string with full hex preserved
     * 5. Any remaining & codes translated (fallback for PAPI output etc.)
     */
    private static String color(String s) {
        if (s == null) return "";

        // 1. &#RRGGBB → <#RRGGBB>
        s = AMP_HEX.matcher(s).replaceAll("<#$1>");

        // 2. </gold> etc. → <reset>
        s = CLOSING_COLOR_TAG.matcher(s).replaceAll("<reset>");

        // 3. §-codes (e.g. from %player_displayname% or %simplenick_nickname% PAPI output)
        //    → & so they survive MM.deserialize when mixed with MiniMessage prefix tags
        s = s.replace('§', '&');

        // 4. & codes → MiniMessage equivalents so they work inside <gradient> tags
        s = convertAmpToMiniMessage(s);

        // 5. MiniMessage parse — handles <gradient>, <rainbow>, <#RRGGBB>, named tags etc.
        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                Component comp = MM.deserialize(s);
                s = LEGACY.serialize(comp);
                return s;
            } catch (Throwable ignored) {}
        }

        // 6. Fallback & translation for anything PAPI returned that didn't go through MM
        s = ChatColor.translateAlternateColorCodes('&', s);

        return s;
    }

    private static final Map<Character, String> AMP_TO_MINI = Map.ofEntries(
            Map.entry('l', "<bold>"),
            Map.entry('o', "<italic>"),
            Map.entry('n', "<underlined>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('r', "<reset>"),
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>")
    );

    private static String convertAmpToMiniMessage(String s) {
        if (s == null || s.indexOf('&') == -1) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String mini = AMP_TO_MINI.get(code);
                if (mini != null) {
                    out.append(mini);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}