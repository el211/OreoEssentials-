package fr.elias.oreoEssentials.modules.tab;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.MiniMessageCompat;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class CustomTablistLayout {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // &#RRGGBB -> <#RRGGBB>
    private static final Pattern AMP_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // Closing color tags like </gold>, </aqua> etc. -> <reset>
    private static final Pattern CLOSING_COLOR_TAG = Pattern.compile(
            "</(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>",
            Pattern.CASE_INSENSITIVE
    );

    private static volatile Method papiSetPlaceholdersMethod;
    private static volatile boolean papiLookupAttempted;

    private final OreoEssentials plugin;
    private final TabListManager tabManager;
    private final Map<UUID, String> headerCache = new HashMap<>();
    private final Map<UUID, String> footerCache = new HashMap<>();
    private final Map<UUID, String> playerListNameCache = new HashMap<>();
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
            try {
                updateCustomTablist();
            } catch (Exception e) {
                plugin.getLogger().warning("[CustomTab] Error updating tablist: " + e.getMessage());
            }
        }, 20L, intervalTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        headerCache.clear();
        footerCache.clear();
        playerListNameCache.clear();
    }

    private void updateCustomTablist() {
        FileConfiguration cfg = tabManager.getConfig();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            headerCache.clear();
            footerCache.clear();
            playerListNameCache.clear();
            return;
        }

        advanceFrame(cfg.getInt("tab.custom-layout.change-interval", 80));

        int onlineCount = players.size();
        for (Player viewer : players) {
            try {
                updateHeaderFooter(viewer, cfg, onlineCount);
            } catch (Exception e) {
                plugin.getLogger().warning("[CustomTab] Error updating header/footer for " + viewer.getName() + ": " + e.getMessage());
            }
        }

        updatePlayerNames(players, cfg, onlineCount);
        pruneCaches(players);
    }

    private void advanceFrame(int changeInterval) {
        int safeInterval = Math.max(1, changeInterval);
        long currentTime = System.currentTimeMillis();
        long ticksPassed = (currentTime - lastFrameChange) / 50L;
        if (lastFrameChange == 0L || ticksPassed >= safeInterval) {
            currentFrame++;
            lastFrameChange = currentTime;
        }
    }

    private void updateHeaderFooter(Player viewer, FileConfiguration cfg, int onlineCount) {
        boolean headerAnimated = cfg.contains("tab.custom-layout.top-section.texts");
        boolean footerAnimated = cfg.contains("tab.custom-layout.bottom-section.texts");

        String headerText;
        if (headerAnimated) {
            headerText = getAnimatedText(cfg, "tab.custom-layout.top-section.texts", viewer, onlineCount);
        } else {
            String line1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-1", "&f&lWelcome &b%player_displayname%"), onlineCount);
            String line2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-2", "&6&lWorld: &e%player_world%"), onlineCount);
            String line3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-3", "&6&lPing: &e%player_ping%ms"), onlineCount);
            headerText = color(line1) + "\n" + color(line2) + "  " + color(line3);
        }

        String footerText;
        if (footerAnimated) {
            footerText = getAnimatedText(cfg, "tab.custom-layout.bottom-section.texts", viewer, onlineCount);
        } else {
            String fLine1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-1", "&6&lPlayers: &e%oe_network_online%"), onlineCount);
            String fLine2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-2", "&6&lBalance: &e$%vault_eco_balance_formatted%"), onlineCount);
            String fLine3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-3", "&c&lServer"), onlineCount);
            footerText = "\n" + color(fLine1) + "  " + color(fLine2) + "\n" + color(fLine3);
        }

        UUID viewerId = viewer.getUniqueId();
        if (!Objects.equals(headerCache.get(viewerId), headerText) || !Objects.equals(footerCache.get(viewerId), footerText)) {
            viewer.setPlayerListHeaderFooter(headerText, footerText);
            headerCache.put(viewerId, headerText);
            footerCache.put(viewerId, footerText);
        }
    }

    private String getAnimatedText(FileConfiguration cfg, String path, Player viewer, int onlineCount) {
        List<String> frames = cfg.getStringList(path);
        if (frames.isEmpty()) return "";

        int frameIndex = currentFrame % frames.size();
        String frame = frames.get(frameIndex);
        frame = applyPlaceholders(viewer, frame, onlineCount);
        return color(frame);
    }

    private void updatePlayerNames(List<Player> players, FileConfiguration cfg, int onlineCount) {
        if (!cfg.getBoolean("tab.custom-layout.player-section.enabled", true)) return;

        List<PlayerTabEntry> entries = new ArrayList<>(players.size());
        for (Player target : players) {
            String primaryGroup = getPrimaryGroup(target);
            int rankPriority = getRankPriority(primaryGroup, cfg);
            boolean afk = isPlayerAFK(target);
            String displayName = formatPlayerName(target, cfg, primaryGroup, afk);
            displayName = applyPlaceholders(target, displayName, onlineCount);
            entries.add(new PlayerTabEntry(target, rankPriority, displayName));
        }

        entries.sort(Comparator
                .comparingInt(PlayerTabEntry::rankPriority)
                .reversed()
                .thenComparing(entry -> entry.player().getName(), String.CASE_INSENSITIVE_ORDER));

        for (PlayerTabEntry entry : entries) {
            Player target = entry.player();
            String displayName = entry.displayName();
            UUID targetId = target.getUniqueId();
            if (Objects.equals(playerListNameCache.get(targetId), displayName)) {
                continue;
            }

            try {
                String mm = AMP_HEX.matcher(displayName).replaceAll("<#$1>");
                mm = CLOSING_COLOR_TAG.matcher(mm).replaceAll("<reset>");
                mm = mm.replace('\u00A7', '&');
                mm = MiniMessageCompat.normalizeTagAliases(convertAmpToMiniMessage(mm));
                Component comp = MM.deserialize(mm);
                target.playerListName(comp);
                playerListNameCache.put(targetId, displayName);
            } catch (Throwable e) {
                try {
                    target.setPlayerListName(color(displayName));
                    playerListNameCache.put(targetId, displayName);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[CustomTab] Failed to set player list name for " + target.getName());
                }
            }
        }
    }

    private String formatPlayerName(Player player, FileConfiguration cfg, String primaryGroup, boolean afk) {
        String format = cfg.getString("tab.custom-layout.player-section.player-format.format",
                "%player_color%%player_name%%afk_indicator%");

        String rankColor = getRankColor(primaryGroup, cfg);

        String afkIndicator = "";
        if (afk && cfg.getBoolean("tab.custom-layout.player-section.player-format.show-afk", true)) {
            afkIndicator = color(cfg.getString("tab.custom-layout.player-section.player-format.afk-format", " &7AFK"));
        }

        return format.replace("%player_color%", rankColor)
                .replace("%player_name%", player.getName())
                .replace("%afk_indicator%", afkIndicator);
    }

    private String getRankColor(String rank, FileConfiguration cfg) {
        ConfigurationSection rankColors = cfg.getConfigurationSection("tab.custom-layout.player-section.player-format.rank-colors");
        if (rankColors != null && rankColors.contains(rank)) {
            return color(rankColors.getString(rank, "&a"));
        }
        return color(rankColors != null ? rankColors.getString("default", "&a") : "&a");
    }

    private int getRankPriority(String rank, FileConfiguration cfg) {
        ConfigurationSection priorities = cfg.getConfigurationSection("tab.custom-layout.sorting.rank-priority");
        if (priorities != null && priorities.contains(rank)) {
            return priorities.getInt(rank, 10);
        }
        return 10;
    }

    private String getPrimaryGroup(Player player) {
        String result = runPapi(player, "%luckperms_primary_group%");
        return result != null && !result.isEmpty() ? result : "default";
    }

    private boolean isPlayerAFK(Player player) {
        return "yes".equalsIgnoreCase(runPapi(player, "%essentials_afk%"));
    }

    private String applyPlaceholders(Player player, String text, int onlineCount) {
        if (text == null) return "";

        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_ping%", String.valueOf(player.getPing()));
        text = text.replace("%oe_network_online%", String.valueOf(onlineCount));

        String result = runPapi(player, text);
        return result != null ? result : text;
    }

    private String runPapi(Player player, String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            Method method = papiSetPlaceholdersMethod;
            if (method == null && !papiLookupAttempted) {
                synchronized (CustomTablistLayout.class) {
                    method = papiSetPlaceholdersMethod;
                    if (method == null && !papiLookupAttempted) {
                        papiLookupAttempted = true;
                        Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                        papiSetPlaceholdersMethod = papiCls.getMethod("setPlaceholders", Player.class, String.class);
                        method = papiSetPlaceholdersMethod;
                    }
                }
            }
            if (method == null) {
                return text;
            }
            Object result = method.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Throwable ignored) {
            return text;
        }
    }

    private void pruneCaches(List<Player> players) {
        Set<UUID> onlineIds = new HashSet<>(players.size());
        for (Player player : players) {
            onlineIds.add(player.getUniqueId());
        }
        headerCache.keySet().removeIf(id -> !onlineIds.contains(id));
        footerCache.keySet().removeIf(id -> !onlineIds.contains(id));
        playerListNameCache.keySet().removeIf(id -> !onlineIds.contains(id));
    }

    /**
     * Full color pipeline using MiniMessage - properly renders gradients,
     * hex colors, named color tags, and legacy & codes.
     */
    private static String color(String s) {
        if (s == null) return "";

        s = AMP_HEX.matcher(s).replaceAll("<#$1>");
        s = CLOSING_COLOR_TAG.matcher(s).replaceAll("<reset>");
        s = s.replace('\u00A7', '&');
        s = MiniMessageCompat.normalizeTagAliases(convertAmpToMiniMessage(s));

        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                Component comp = MM.deserialize(s);
                return LEGACY.serialize(comp);
            } catch (Throwable ignored) {
            }
        }

        return ChatColor.translateAlternateColorCodes('&', s);
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

    private record PlayerTabEntry(Player player, int rankPriority, String displayName) {}
}
