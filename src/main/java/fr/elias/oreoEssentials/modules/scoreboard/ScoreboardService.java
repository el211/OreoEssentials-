package fr.elias.oreoEssentials.modules.scoreboard;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScoreboardService implements Listener {

    private static final String OBJ_NAME = "oreo_sb";
    private static final Pattern ANIM_TAG = Pattern.compile(
            "%animations_<tag(?:\\s+interval=(\\d+))?>\\s*(.*?)\\s*</tag>%",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_HEX = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    private static final Pattern PAPI_TAG = Pattern.compile("<papi:([^>]+)>");

    private final OreoEssentials plugin;
    private ScoreboardConfig cfg;
    private AnimatedText titleAnim;
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> toggles = new ConcurrentHashMap<>();
    private int taskId = -1;

    public ScoreboardService(OreoEssentials plugin, ScoreboardConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.titleAnim = new AnimatedText(cfg.titleFrames(), cfg.titleFrameTicks());
    }

    public void start() {
        if (!cfg.enabled()) return;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (shouldShow(p)) show(p);
            }

        }, 5L);

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            titleAnim.tick();

            for (UUID id : List.copyOf(shown)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    shown.remove(id);
                    toggles.remove(id);
                    continue;
                }

                if (!shouldShow(p)) {
                    hide(p);
                    continue;
                }


                refresh(p);
            }
        }, cfg.updateTicks(), cfg.updateTicks());
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        for (UUID id : List.copyOf(shown)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                clearBoard(p);
            }
        }

        shown.clear();
        HandlerList.unregisterAll(this);
    }

    public void reload() {
        Set<UUID> keepShown = new HashSet<>(shown);
        Map<UUID, Boolean> keepToggles = new HashMap<>(toggles);

        stop();

        this.cfg = ScoreboardConfig.load(plugin);
        this.titleAnim = new AnimatedText(cfg.titleFrames(), cfg.titleFrameTicks());

        start();

        toggles.clear();
        toggles.putAll(keepToggles);

        for (UUID id : keepShown) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && shouldShow(p)) {
                show(p);
            }
        }

    }

    public boolean isShown(Player p) {
        return shown.contains(p.getUniqueId());
    }

    public void toggle(Player p) {
        UUID id = p.getUniqueId();

        if (isShown(p)) {
            hide(p);
            toggles.put(id, false);
            return;
        }

        if (!isWorldAllowedByLists(p)) {
            Lang.send(p, "scoreboard.disabled-in-world",
                    "<red>Scoreboard is disabled in this world.</red>");
            return;
        }

        show(p);
        toggles.put(id, true);
    }


    public void show(Player p) {
        if (!cfg.enabled()) return;
        if (isShown(p)) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard board = mgr.getNewScoreboard();

        Objective obj = board.registerNewObjective(OBJ_NAME, "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        safeSetTitle(p, obj);


        applyLines(p, board, obj);

        p.setScoreboard(board);
        shown.add(p.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> forceNametagUpdate(p));
    }

    public void hide(Player p) {
        clearBoard(p);
        shown.remove(p.getUniqueId());
    }

    private void clearBoard(Player p) {
        try {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr != null) {
                p.setScoreboard(mgr.getNewScoreboard());
            }
        } catch (Throwable ignored) {}

        Bukkit.getScheduler().runTask(plugin, () -> forceNametagUpdate(p));
    }

    private void refresh(Player p) {
        Scoreboard board = p.getScoreboard();
        if (board == null) {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr == null) return;
            board = mgr.getNewScoreboard();
            p.setScoreboard(board);
            forceNametagUpdate(p);
        }

        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJ_NAME, "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            shown.add(p.getUniqueId());
        } else if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.numberFormat(NumberFormat.blank());
        safeSetTitle(p, obj);


        try {
            for (String entry : new HashSet<>(board.getEntries())) {
                board.resetScores(entry);
            }
        } catch (Throwable ignored) {}

        applyLines(p, board, obj);
        forceNametagUpdate(p);
    }

    private void safeSetTitle(Player p, Objective obj) {
        try {
            obj.setDisplayName(render(p, titleAnim.current()));
        } catch (Throwable ignored) {}
    }

    private void applyLines(Player p, Scoreboard board, Objective obj) {
        List<String> lines = cfg.lines();
        List<String> expanded = new ArrayList<>();

        for (String raw : lines) {
            String rendered = render(p, raw);

            if (rendered.contains("\n")) {
                for (String part : rendered.split("\n")) {
                    if (!part.trim().isEmpty()) expanded.add(part);
                }
            } else {
                expanded.add(rendered);
            }
        }

        for (int i = 0; i < 64; i++) {
            String teamName = "oe_ln_" + i;
            var t = board.getTeam(teamName);
            if (t != null) t.unregister();
        }
        for (String e : new HashSet<>(board.getEntries())) {
            board.resetScores(e);
        }

        int score = expanded.size();

        for (int i = 0; i < expanded.size(); i++) {
            String text = expanded.get(i);

            text = truncateVisible(text, 80);

            String entry = "§" + Integer.toHexString(i % 16);

            String teamName = "oe_ln_" + i;
            var team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);

            String prefix = text;
            String suffix = "";

            if (text.length() > 64) {
                prefix = text.substring(0, 64);
                suffix = text.substring(64);
                if (suffix.length() > 64) suffix = suffix.substring(0, 64);
            }

            team.addEntry(entry);
            team.prefix(Component.text(prefix));
            team.suffix(Component.text(suffix));

            obj.getScore(entry).setScore(score--);
        }
    }


    private static String ensureUnique(Scoreboard board, String base) {
        String s = base;
        int tries = 0;

        while (board.getEntries().contains(s) && tries < 16) {
            s = base + ChatColor.values()[tries % ChatColor.values().length];
            tries++;
        }

        if (board.getEntries().contains(s)) {
            s = truncateVisible(base, 30) + ChatColor.RESET + tries;
        }


        return s;
    }

    private boolean isWorldAllowedByLists(Player p) {
        String w = p.getWorld().getName();
        var wl = cfg.worldsWhitelist();
        var bl = cfg.worldsBlacklist();

        if (!wl.isEmpty() && !wl.contains(w)) return false;
        if (!bl.isEmpty() && bl.contains(w)) return false;
        return true;
    }

    private boolean shouldShow(Player p) {
        if (!isWorldAllowedByLists(p)) return false;

        Boolean pref = toggles.get(p.getUniqueId());
        return pref == null ? cfg.defaultEnabled() : pref;
    }


    private String render(Player p, String raw) {
        if (raw == null) return "";

        //1
        String s = applyTagAnimations(raw);

        //2
        s = s.replace("{player}", p.getName());

        //3
        s = convertPapiTags(s);

        //4
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);

                if (s.contains("%")) {
                    s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("PlaceholderAPI error: " + ex.getMessage());
            ex.printStackTrace();
        }

        //5
        s = AMP_HEX.matcher(s).replaceAll("<#$1>");


        //6
        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                boolean nexoEnabled = Bukkit.getPluginManager().getPlugin("Nexo") != null
                        && Bukkit.getPluginManager().getPlugin("Nexo").isEnabled();

                if (nexoEnabled) {
                    s = parseWithNexoAdventureUtils(s);
                } else {
                    Component comp = MM.deserialize(s);
                    s = LEGACY.serialize(comp);
                }
            } catch (Throwable ignored) {
            }
        }

        //7
        s = ChatColor.translateAlternateColorCodes('&', s);

        //8
        s = downsampleLegacyHexToLegacy16Safe(s);

        return s;
    }
    private String parseWithNexoAdventureUtils(String input) {
        try {
            Class<?> adv = Class.forName("com.nexomc.nexo.utils.AdventureUtils");

            try {
                return (String) adv.getMethod("parseLegacyThroughMiniMessage", String.class)
                        .invoke(null, input);
            } catch (NoSuchMethodException ignored) {}

            try {
                return (String) adv.getMethod("parseLegacy", String.class)
                        .invoke(null, input);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable ignored) {}

        try {
            Component comp = MM.deserialize(input);
            return LEGACY.serialize(comp);
        } catch (Throwable ignored) {
            return input;
        }
    }


    /**
     * Safer version of downsampleLegacyHexToLegacy16 that preserves Unicode characters
     */
    private static String downsampleLegacyHexToLegacy16Safe(String s) {
        if (s == null || s.isEmpty()) return s;

        Matcher m = LEGACY_HEX.matcher(s);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            int start = m.start();
            int end = m.end();

            boolean nearUnicode = false;
            for (int i = Math.max(0, start - 3); i < Math.min(s.length(), end + 3); i++) {
                if (s.charAt(i) > 255) {
                    nearUnicode = true;
                    break;
                }
            }

            if (nearUnicode) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            } else {
                String hex = m.group().replace("§x", "").replace("§", "");
                int rgb = Integer.parseInt(hex, 16);
                char legacy = nearestLegacyColor(rgb);
                m.appendReplacement(out, "§" + legacy);
            }
        }

        m.appendTail(out);
        return out.toString();
    }

    /**
     * Converts MiniMessage <papi:placeholder> tags to standard %placeholder% format
     * This allows both syntaxes to work with PlaceholderAPI
     */
    private String convertPapiTags(String input) {
        if (input == null || !input.contains("<papi:")) return input;

        Matcher m = PAPI_TAG.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            String placeholder = m.group(1);
            // Convert <papi:placeholder> to %placeholder%
            m.appendReplacement(out, Matcher.quoteReplacement("%" + placeholder + "%"));
        }

        m.appendTail(out);
        return out.toString();
    }



    private String applyTagAnimations(String input) {
        if (input == null || input.isEmpty()) return input;

        Matcher m = ANIM_TAG.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            int interval = 20;
            try {
                if (m.group(1) != null) interval = Math.max(1, Integer.parseInt(m.group(1)));
            } catch (Throwable ignored) {}

            String body = m.group(2) == null ? "" : m.group(2);

            List<String> frames = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            String[] lines = body.replace("\r\n", "\n").split("\n", -1);
            for (String line : lines) {
                if (line.trim().equals("|")) {
                    frames.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(line).append("\n");
                }
            }
            frames.add(current.toString());
            frames.replaceAll(s -> s.endsWith("\n") ? s.substring(0, s.length() - 1) : s);

            String replacement;
            if (frames.isEmpty()) {
                replacement = "";
            } else {
                long ticks = System.currentTimeMillis() / 50L;
                int idx = (int) ((ticks / interval) % frames.size());
                replacement = frames.get(Math.max(0, idx));
            }

            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }

        m.appendTail(out);
        return out.toString();
    }

    private void forceNametagUpdate(Player p) {
        try {
            if (plugin.getNametagManager() != null) {
                plugin.getNametagManager().forceUpdate(p);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.enabled()) return;

        Player p = e.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (shouldShow(p)) show(p);
            else forceNametagUpdate(p);

        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        shown.remove(e.getPlayer().getUniqueId());
        toggles.remove(e.getPlayer().getUniqueId());
    }
    private static String truncateVisible(String s, int maxVisible) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());

        int visible = 0;
        for (int i = 0; i < s.length() && visible < maxVisible; ) {
            char c = s.charAt(i);

            // Legacy color codes
            if (c == '§' && i + 1 < s.length()) {
                char code = s.charAt(i + 1);

                // Hex format: §x§R§R§G§G§B§B  (14 chars)
                if ((code == 'x' || code == 'X') && i + 13 < s.length()) {
                    out.append(s, i, i + 14);
                    i += 14;
                    continue;
                }

                // Normal format: §a, §l, §r, etc
                out.append(c).append(code);
                i += 2;
                continue;
            }

            // Normal visible char
            out.append(c);
            visible++;
            i++;
        }

        return out.toString();
    }
    private static String downsampleLegacyHexToLegacy16(String s) {
        if (s == null || s.isEmpty()) return s;

        Matcher m = LEGACY_HEX.matcher(s);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            // Extract RRGGBB from §x§R§R§G§G§B§B
            String hex = m.group().replace("§x", "").replace("§", "");
            int rgb = Integer.parseInt(hex, 16);

            char legacy = nearestLegacyColor(rgb);
            m.appendReplacement(out, "§" + legacy);
        }

        m.appendTail(out);
        return out.toString();
    }

    private static char nearestLegacyColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Approx Minecraft legacy palette
        // (good enough for "gold/yellow/text" themes)
        int[][] palette = {
                {0x00,0x00,0x00,'0'},
                {0x00,0x00,0xAA,'1'},
                {0x00,0xAA,0x00,'2'},
                {0x00,0xAA,0xAA,'3'},
                {0xAA,0x00,0x00,'4'},
                {0xAA,0x00,0xAA,'5'},
                {0xFF,0xAA,0x00,'6'}, // gold
                {0xAA,0xAA,0xAA,'7'},
                {0x55,0x55,0x55,'8'},
                {0x55,0x55,0xFF,'9'},
                {0x55,0xFF,0x55,'a'},
                {0x55,0xFF,0xFF,'b'},
                {0xFF,0x55,0x55,'c'},
                {0xFF,0x55,0xFF,'d'},
                {0xFF,0xFF,0x55,'e'}, // yellow
                {0xFF,0xFF,0xFF,'f'}  // white
        };

        int best = Integer.MAX_VALUE;
        char bestCode = 'f';

        for (int[] p : palette) {
            int dr = r - p[0], dg = g - p[1], db = b - p[2];
            int dist = dr*dr + dg*dg + db*db;
            if (dist < best) {
                best = dist;
                bestCode = (char) p[3];
            }
        }
        return bestCode;
    }
    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (shouldShow(p)) show(p);
        else hide(p);

    }
}