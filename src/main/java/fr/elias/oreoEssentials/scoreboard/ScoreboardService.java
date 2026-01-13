package fr.elias.oreoEssentials.scoreboard;

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
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

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
                if (isWorldAllowed(p)) show(p);
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

                if (!isWorldAllowed(p)) {
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
            if (p != null && isWorldAllowed(p)) {
                show(p);
            }
        }
    }

    public boolean isShown(Player p) {
        return shown.contains(p.getUniqueId());
    }

    public void toggle(Player p) {
        if (isShown(p)) {
            hide(p);
            toggles.put(p.getUniqueId(), false);
            return;
        }

        if (!isWorldAllowed(p)) {
            Lang.send(p, "scoreboard.disabled-in-world",
                    "<red>Scoreboard is disabled in this world.</red>");
            return;
        }

        show(p);
        toggles.put(p.getUniqueId(), true);
    }

    public void show(Player p) {
        if (!cfg.enabled()) return;
        if (isShown(p)) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard board = mgr.getNewScoreboard();

        Objective obj = board.registerNewObjective(OBJ_NAME, "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
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
        int score = lines.size();

        for (String raw : lines) {
            String line = truncate(render(p, raw), 40);
            if (line.isEmpty()) line = ChatColor.RESET.toString();

            String entry = ensureUnique(board, line);
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
            s = base.substring(0, Math.min(base.length(), 30)) + ChatColor.RESET + tries;
        }

        return s;
    }

    private boolean isWorldAllowed(Player p) {
        String w = p.getWorld().getName();
        var wl = cfg.worldsWhitelist();
        var bl = cfg.worldsBlacklist();

        if (!wl.isEmpty() && !wl.contains(w)) return false;
        if (!bl.isEmpty() && bl.contains(w)) return false;

        Boolean pref = toggles.get(p.getUniqueId());
        return pref == null ? cfg.defaultEnabled() : pref;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String render(Player p, String raw) {
        if (raw == null) return "";

        String s = applyTagAnimations(raw);
        s = s.replace("{player}", p.getName());

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
            }
        } catch (Throwable ignored) {}

        try {
            Component c = MM.deserialize(s);
            s = LEGACY.serialize(c);
        } catch (Throwable ignored) {}

        return ChatColor.translateAlternateColorCodes('&', s);
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
            if (isWorldAllowed(p)) show(p);
            else forceNametagUpdate(p);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        shown.remove(e.getPlayer().getUniqueId());
        toggles.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isWorldAllowed(p)) show(p);
        else hide(p);
    }
}