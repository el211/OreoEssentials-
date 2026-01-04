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

public final class ScoreboardService implements Listener {

    private static final String OBJ_NAME = "oreo_sb";

    private final OreoEssentials plugin;

    private ScoreboardConfig cfg;       // reloadable
    private AnimatedText titleAnim;     // reloadable

    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> toggles = new ConcurrentHashMap<>();

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

        // Show for already online players (reload /plugman / etc.)
        // Delay slightly to let nametag manager initialize first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isWorldAllowed(p)) show(p);
            }
        }, 5L);

        // Update loop
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
                    hide(p); // will also re-apply nametag after scoreboard reset
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
                clearBoard(p); // also re-apply nametag
            }
        }

        shown.clear();

        // Clean unregister
        HandlerList.unregisterAll(this);
    }

    /** Re-read config and refresh all players who had it enabled. */
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

    /* ---------------- Toggling ---------------- */

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

    /* ---------------- Show/Hide ---------------- */

    public void show(Player p) {
        if (!cfg.enabled()) return;
        if (isShown(p)) return;

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        // IMPORTANT: We create a dedicated scoreboard for the sidebar.
        // This will overwrite whatever scoreboard the player had -> so we MUST re-apply nametags after.
        Scoreboard board = mgr.getNewScoreboard();

        Objective obj = board.registerNewObjective(OBJ_NAME, "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        safeSetTitle(p, obj);

        applyLines(p, board, obj);

        p.setScoreboard(board);
        shown.add(p.getUniqueId());

        // Re-apply nametag onto the NEW scoreboard
        // Delay by 1 tick to ensure scoreboard is fully set
        Bukkit.getScheduler().runTask(plugin, () -> forceNametagUpdate(p));
    }

    public void hide(Player p) {
        clearBoard(p); // clears sidebar scoreboard -> and re-applies nametag
        shown.remove(p.getUniqueId());
    }

    private void clearBoard(Player p) {
        try {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr != null) {
                p.setScoreboard(mgr.getNewScoreboard());
            }
        } catch (Throwable ignored) {}

        // After replacing scoreboard, teams are wiped -> re-apply nametag
        // Delay by 1 tick to ensure scoreboard is fully set
        Bukkit.getScheduler().runTask(plugin, () -> forceNametagUpdate(p));
    }

    /* ---------------- Refresh ---------------- */

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

        // Only clear scores for our objective (safer than resetting every entry on the board)
        try {
            for (String entry : new HashSet<>(board.getEntries())) {
                // This removes scores in ALL objectives for that entry.
                // We keep it, because this scoreboard is "ours" anyway.
                board.resetScores(entry);
            }
        } catch (Throwable ignored) {}

        applyLines(p, board, obj);

        // Optional: if you want nametag placeholders like health/ping to update smoothly
        // you can also force update here. It's cheap enough at 5s intervals.
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

        // last resort
        if (board.getEntries().contains(s)) {
            s = base.substring(0, Math.min(base.length(), 30)) + ChatColor.RESET + tries;
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
        return pref == null ? cfg.defaultEnabled() : pref;
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

    private void forceNametagUpdate(Player p) {
        try {
            if (plugin.getNametagManager() != null) {
                plugin.getNametagManager().forceUpdate(p);
            }
        } catch (Throwable ignored) {}
    }

    /* ---------------- Events ---------------- */

    /**
     * CRITICAL: Run at HIGHEST priority so we set up scoreboard BEFORE nametag manager
     * This ensures nametag manager adds teams to OUR scoreboard, not some temporary one
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.enabled()) return;

        Player p = e.getPlayer();

        // Delay slightly to let player fully load, but run before nametag manager (which uses LOWEST priority)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isWorldAllowed(p)) show(p);
            else forceNametagUpdate(p);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Clean scoreboard + avoid keeping UUIDs forever
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