// File: src/main/java/fr/elias/oreoEssentials/scoreboard/ScoreboardService.java
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

public final class ScoreboardService implements Listener {
    private final OreoEssentials plugin;
    private ScoreboardConfig cfg;              // not final
    private AnimatedText titleAnim;            // not final

    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> toggles = new HashMap<>();

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private int taskId = -1;

    public ScoreboardService(OreoEssentials plugin, ScoreboardConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.titleAnim = new AnimatedText(cfg.titleFrames(), cfg.titleFrameTicks());
    }

    /* ---------------- Lifecycle ---------------- */

    public void start() {
        if (!cfg.enabled()) return;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isWorldAllowed(p)) show(p);
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            titleAnim.tick();
            for (UUID id : List.copyOf(shown)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    shown.remove(id);
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
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        for (UUID id : List.copyOf(shown)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearBoard(p);
        }
        shown.clear();
        PlayerJoinEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
        PlayerChangedWorldEvent.getHandlerList().unregister(this);
    }

    /** Re-read config and refresh all players who have it enabled. */
    public void reload() {
        // Remember who had it shown (and their toggle preference)
        Set<UUID> keep = new HashSet<>(shown);
        Map<UUID, Boolean> keepToggles = new HashMap<>(toggles);

        stop(); // clears shown + listeners + task

        // Re-read config
        this.cfg = ScoreboardConfig.load(plugin);
        this.titleAnim = new AnimatedText(cfg.titleFrames(), cfg.titleFrameTicks());

        start(); // re-register + restart timer

        // Restore visibility for players that had it on (and are allowed in current world)
        this.toggles.clear();
        this.toggles.putAll(keepToggles);
        for (UUID id : keep) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && isWorldAllowed(p)) show(p);
        }
    }

    /* ---------------- Toggling ---------------- */

    public boolean isShown(Player p) {
        return shown.contains(p.getUniqueId());
    }

    public void toggle(Player p) {
        if (isShown(p)) {
            hide(p);
            toggles.put(p.getUniqueId(), false);
        } else {
            if (!isWorldAllowed(p)) {
                Lang.send(p, "scoreboard.disabled-in-world",
                        "<red>Scoreboard is disabled in this world.</red>");
                return;
            }
            show(p);
            toggles.put(p.getUniqueId(), true);
        }
    }

    /* ---------------- Show/Hide ---------------- */

    public void show(Player p) {
        if (!cfg.enabled()) return;
        if (isShown(p)) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard board = mgr.getNewScoreboard();

        Objective obj = board.registerNewObjective("oreo_sb", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(render(p, titleAnim.current()));

        applyLines(p, board, obj);

        p.setScoreboard(board);
        shown.add(p.getUniqueId());
    }

    public void hide(Player p) {
        clearBoard(p);
        shown.remove(p.getUniqueId());
    }

    private void clearBoard(Player p) {
        try {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr != null) p.setScoreboard(mgr.getNewScoreboard());
        } catch (Throwable ignored) {}
    }

    /* ---------------- Refresh ---------------- */

    private void refresh(Player p) {
        Scoreboard board = p.getScoreboard();

        // If player has no scoreboard, create one once
        if (board == null) {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr == null) return;
            board = mgr.getNewScoreboard();
            p.setScoreboard(board);
        }

        // Ensure the objective exists
        Objective obj = board.getObjective("oreo_sb");
        if (obj == null) {
            obj = board.registerNewObjective("oreo_sb", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            shown.add(p.getUniqueId());
        } else if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Update title
        try {
            obj.setDisplayName(render(p, titleAnim.current()));
        } catch (Throwable ignored) {}

        // Reset + reapply lines
        for (String e : new ArrayList<>(board.getEntries())) {
            board.resetScores(e);
        }
        applyLines(p, board, obj);
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
        while (board.getEntries().contains(s) && tries < 10) {
            s = base + ChatColor.values()[tries % ChatColor.values().length];
            tries++;
        }
        return s;
    }

    /* ---------------- Helpers ---------------- */

    private boolean isWorldAllowed(Player p) {
        String w = p.getWorld().getName();
        var wl = cfg.worldsWhitelist();
        var bl = cfg.worldsBlacklist();
        if (!wl.isEmpty() && !wl.contains(w)) return false;
        if (!bl.isEmpty() && bl.contains(w)) return false;
        Boolean pref = toggles.get(p.getUniqueId());
        if (pref == null) return cfg.defaultEnabled();
        return pref;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Render pipeline: {player} -> PAPI -> MiniMessage -> legacy (§x) -> & codes. */
    private String render(Player p, String raw) {
        if (raw == null) return "";
        String s = raw.replace("{player}", p.getName());
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

    /* ---------------- Events ---------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.enabled()) return;
        Player p = e.getPlayer();
        if (isWorldAllowed(p)) show(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isWorldAllowed(p)) show(p);
        else hide(p);
    }
}