package fr.elias.oreoEssentials.modules.scoreboard;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.MiniMessageCompat;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
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
    private static final Pattern AMP_HEX   = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_HEX = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    private static final Pattern PAPI_TAG   = Pattern.compile("<papi:([^>]+)>");

    private final OreoEssentials plugin;
    private ScoreboardConfig cfg;
    private AnimatedText titleAnim;
    private final Set<UUID>          shown       = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> toggles     = new ConcurrentHashMap<>();
    private final Map<UUID, FoliaScoreboard> foliaBoards  = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard>      playerBoards = new ConcurrentHashMap<>();
    private final Map<UUID, RenderState>     renderStates = new ConcurrentHashMap<>();
    private final Map<String, Boolean>       sharedTemplateCache = new ConcurrentHashMap<>();
    private final Map<String, String>        sharedLegacyRenderCache = new ConcurrentHashMap<>();
    private OreTask taskId = null;
    private final File toggleFile;
    private int refreshCursor = 0;
    private int titleAnimTickAccumulator = 0;
    private double refreshBatchBudget = 0.0d;

    private static final class RenderState {
        private final String title;
        private final List<String> lines;

        private RenderState(String title, List<String> lines) {
            this.title = title;
            this.lines = List.copyOf(lines);
        }
    }

    private static final class RenderCycle {
        private final boolean placeholderApiEnabled;
        private final Map<String, String> animatedTextCache = new ConcurrentHashMap<>();

        private RenderCycle(boolean placeholderApiEnabled) {
            this.placeholderApiEnabled = placeholderApiEnabled;
        }
    }

    public ScoreboardService(OreoEssentials plugin, ScoreboardConfig cfg) {
        this.plugin      = plugin;
        this.cfg         = cfg;
        this.titleAnim   = new AnimatedText(cfg.titleFrames(), cfg.titleFrameTicks());
        this.toggleFile  = new File(new File(plugin.getDataFolder(), "players"), "sb-toggles.yml");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        if (!cfg.enabled()) return;

        loadPersistedToggles();

        plugin.getLogger().info("[Scoreboard] Starting — Folia=" + OreScheduler.isFolia()
                + " FoliaScoreboardAvailable=" + fr.elias.oreoEssentials.modules.scoreboard.FoliaScoreboard.AVAILABLE);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        OreScheduler.runLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (shouldShow(p)) show(p);
            }
        }, 5L);

        taskId = OreScheduler.runTimer(plugin, () -> {
            titleAnimTickAccumulator++;
            if (titleAnimTickAccumulator >= cfg.updateTicks()) {
                titleAnim.tick();
                titleAnimTickAccumulator = 0;
            }
            RenderCycle cycle = new RenderCycle(isPlaceholderApiEnabled());
            refreshShownPlayers(cycle);
        }, 1L, 1L);
    }

    public void stop() {
        if (taskId != null) {
            taskId.cancel();
            taskId = null;
        }
        for (UUID id : List.copyOf(shown)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearBoard(p);
        }
        shown.clear();
        playerBoards.clear();
        renderStates.clear();
        sharedLegacyRenderCache.clear();
        refreshCursor = 0;
        titleAnimTickAccumulator = 0;
        refreshBatchBudget = 0.0d;
        // Clean up Folia NMS scoreboards
        for (FoliaScoreboard fs : foliaBoards.values()) {
            try { fs.hide(); } catch (Throwable ignored) {}
        }
        foliaBoards.clear();
        HandlerList.unregisterAll(this);
    }

    public void reload() {
        Set<UUID>          keepShown    = new HashSet<>(shown);
        Map<UUID, Boolean> keepToggles  = new HashMap<>(toggles);

        stop();

        this.cfg       = ScoreboardConfig.load(plugin);
        this.titleAnim = new AnimatedText(cfg.titleFrames(), cfg.titleFrameTicks());

        start();

        toggles.clear();
        toggles.putAll(keepToggles);

        for (UUID id : keepShown) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && shouldShow(p)) show(p);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isShown(Player p) { return shown.contains(p.getUniqueId()); }

    public void toggle(Player p) {
        UUID id = p.getUniqueId();
        if (isShown(p)) {
            toggles.put(id, false); // set BEFORE hide so shouldShow() is consistent
            hide(p);
            saveToggle(id, false);
            return;
        }
        if (!isWorldAllowedByLists(p)) {
            Lang.send(p, "scoreboard.disabled-in-world",
                    "<red>Scoreboard is disabled in this world.</red>");
            return;
        }
        toggles.put(id, true); // set BEFORE show so shouldShow() is consistent
        show(p);
        saveToggle(id, true);
    }

    // -------------------------------------------------------------------------
    // Folia NMS-packet path helpers
    // -------------------------------------------------------------------------

    /** Renders the current title frame as an Adventure Component. */
    private Component getFoliaTitle(Player p, RenderCycle cycle) {
        return renderToComponent(p, titleAnim.current(), cycle);
    }

    /** Renders all config lines as Adventure Components (honoring \\n splits). */
    private List<Component> getFoliaLines(Player p, RenderCycle cycle) {
        List<String> cfgLines = cfg.lines();
        List<Component> result = new ArrayList<>();
        for (String raw : cfgLines) {
            String rendered = renderToLegacyString(p, raw, cycle);
            if (rendered.contains("\n")) {
                for (String part : rendered.split("\n")) {
                    if (!part.trim().isEmpty()) result.add(LEGACY.deserialize(truncateVisible(part, 80)));
                }
            } else {
                result.add(LEGACY.deserialize(truncateVisible(rendered, 80)));
            }
        }
        return result;
    }

    /**
     * Creates a fresh {@link Scoreboard}.
     * On Paper this delegates to {@code ScoreboardManager.getNewScoreboard()}.
     * On Folia that method throws {@code UnsupportedOperationException}
     * unconditionally (Folia blocks it at the API level regardless of calling
     * thread).  We work around this by constructing the NMS Scoreboard directly
     * via reflection — the underlying NMS class is thread-safe to instantiate;
     * only the Bukkit API wrapper throws.
     */
    private static Scoreboard newScoreboard() {
        if (!OreScheduler.isFolia()) {
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            return (mgr != null) ? mgr.getNewScoreboard() : null;
        }
        // Folia path: bypass the API guard via NMS reflection.
        // Paper remaps plugin classes to Mojang names at load time, so these
        // class names are stable across 1.20.5+ builds.
        try {
            Class<?> nmsClass   = Class.forName("net.minecraft.world.scores.Scoreboard");
            Class<?> craftClass = Class.forName("org.bukkit.craftbukkit.scoreboard.CraftScoreboard");
            Object   nmsBoard   = nmsClass.getDeclaredConstructor().newInstance();
            Constructor<?> ctor = craftClass.getDeclaredConstructor(nmsClass);
            ctor.setAccessible(true);
            return (Scoreboard) ctor.newInstance(nmsBoard);
        } catch (Throwable t) {
            // Reflection failed (e.g. future Folia changes class structure).
            // Fall back to main scoreboard so the player at least gets a board.
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            return (mgr != null) ? mgr.getMainScoreboard() : null;
        }
    }

    public void show(Player p) {
        if (!cfg.enabled()) return;
        if (isShown(p)) return;
        RenderCycle cycle = new RenderCycle(isPlaceholderApiEnabled());

        if (OreScheduler.isFolia()) {
            FoliaScoreboard fs = foliaBoards.computeIfAbsent(p.getUniqueId(), id -> new FoliaScoreboard(p));
            try { fs.show(getFoliaTitle(p, cycle), getFoliaLines(p, cycle)); } catch (Throwable ignored) {}
            renderStates.put(p.getUniqueId(), renderState(p, cycle));
            shown.add(p.getUniqueId());
            return;
        }

        try {
            Scoreboard board = newScoreboard();
            if (board == null) {
                plugin.getLogger().warning("[Scoreboard] newScoreboard() returned null for " + p.getName());
                return;
            }
            Objective  obj   = board.registerNewObjective(OBJ_NAME, "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
            RenderState state = renderState(p, cycle);
            applyRenderState(obj, board, state);

            p.setScoreboard(board);
            playerBoards.put(p.getUniqueId(), board);
            renderStates.put(p.getUniqueId(), state);
            shown.add(p.getUniqueId());
            if (plugin.getConfigService().isDebugEnabled()) {
                plugin.getLogger().info("[Scoreboard] Shown for " + p.getName());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Scoreboard] show() Paper path failed for " + p.getName() + ": " + t);
        }

    }

    public void hide(Player p) {
        if (OreScheduler.isFolia()) {
            FoliaScoreboard fs = foliaBoards.remove(p.getUniqueId());
            if (fs != null) { try { fs.hide(); } catch (Throwable ignored) {} }
            shown.remove(p.getUniqueId());
            return;
        }
        playerBoards.remove(p.getUniqueId());
        renderStates.remove(p.getUniqueId());
        clearBoard(p);
        shown.remove(p.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Internal board management
    // -------------------------------------------------------------------------

    private void clearBoard(Player p) {
        if (OreScheduler.isFolia()) {
            FoliaScoreboard fs = foliaBoards.get(p.getUniqueId());
            if (fs != null) { try { fs.hide(); } catch (Throwable ignored) {} }
            return;
        }
        try {
            Scoreboard blank = newScoreboard();
            if (blank != null) p.setScoreboard(blank);
        } catch (Throwable ignored) {}
    }

    private void refresh(Player p, RenderCycle cycle) {
        if (OreScheduler.isFolia()) {
            FoliaScoreboard fs = foliaBoards.get(p.getUniqueId());
            if (fs == null || !fs.isActive()) {
                shown.remove(p.getUniqueId()); // let show() re-add it
                show(p);
            } else {
                RenderState next = renderState(p, cycle);
                RenderState prev = renderStates.get(p.getUniqueId());
                if (prev == null || !Objects.equals(prev.title, next.title) || !Objects.equals(prev.lines, next.lines)) {
                    try { fs.show(getFoliaTitle(p, cycle), getFoliaLines(p, cycle)); } catch (Throwable ignored) {}
                    renderStates.put(p.getUniqueId(), next);
                }
            }
            return;
        }

        // Re-assert our stored board — other plugins (e.g. ViaVersion) may have called
        // p.setScoreboard() after us, replacing it. Re-setting ours every tick is cheap
        // and ensures the sidebar reappears immediately rather than waiting for the
        // next resync cycle.
        Scoreboard board = playerBoards.get(p.getUniqueId());
        if (board == null) {
            board = newScoreboard();
            if (board == null) return;
            playerBoards.put(p.getUniqueId(), board);
        }
        if (p.getScoreboard() != board) {
            p.setScoreboard(board);
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

        RenderState next = renderState(p, cycle);
        RenderState prev = renderStates.get(p.getUniqueId());
        boolean needsApply = prev == null
                || !Objects.equals(prev.title, next.title)
                || !Objects.equals(prev.lines, next.lines);
        if (!needsApply) {
            return;
        }

        try {
            applyRenderState(obj, board, next);
            renderStates.put(p.getUniqueId(), next);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Scoreboard] refresh() applyRenderState failed for " + p.getName() + ": " + t);
        }
    }

    private void refreshShownPlayers(RenderCycle cycle) {
        List<UUID> snapshot = List.copyOf(shown);
        if (snapshot.isEmpty()) {
            refreshCursor = 0;
            refreshBatchBudget = 0.0d;
            return;
        }

        refreshBatchBudget = Math.min(snapshot.size(), refreshBatchBudget + ((double) snapshot.size() / Math.max(1L, cfg.updateTicks())));
        int batchSize = (int) Math.floor(refreshBatchBudget);
        if (batchSize <= 0) {
            return;
        }
        refreshBatchBudget -= batchSize;

        for (int i = 0; i < batchSize; i++) {
            UUID id = snapshot.get(refreshCursor % snapshot.size());
            refreshCursor = (refreshCursor + 1) % Math.max(1, snapshot.size());

            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                shown.remove(id);
                continue;
            }
            if (OreScheduler.isFolia()) {
                OreScheduler.runForEntity(plugin, p, () -> {
                    if (!p.isOnline()) { shown.remove(id); return; }
                    if (!shouldShow(p)) { hide(p); return; }
                    refresh(p, cycle);
                });
            } else {
                if (!shouldShow(p)) {
                    hide(p);
                    continue;
                }
                refresh(p, cycle);
            }
        }
    }

    /**
     * FIX 1 — Title gradient support.
     *
     * The old code called obj.setDisplayName(String) which accepts a legacy-colour
     * string. MiniMessage gradient tags are Adventure-only; they are lost the
     * moment the string is serialised back to legacy format BEFORE being set.
     *
     * Using the Adventure Component overload obj.displayName(Component) lets Paper
     * handle the full colour range natively, preserving every gradient stop.
     */
    private void safeSetTitle(Player p, Objective obj) {
        try {
            Component title = renderToComponent(p, titleAnim.current(), new RenderCycle(isPlaceholderApiEnabled()));
            obj.displayName(title);   // Adventure overload — gradients work here
        } catch (Throwable ignored) {}
    }

    private RenderState renderState(Player p, RenderCycle cycle) {
        return new RenderState(renderToLegacyString(p, titleAnim.current(), cycle), renderLines(p, cycle));
    }

    private List<String> renderLines(Player p, RenderCycle cycle) {
        List<String> expanded = new ArrayList<>();
        for (String raw : cfg.lines()) {
            String rendered = renderToLegacyString(p, raw, cycle);
            if (rendered.contains("\n")) {
                for (String part : rendered.split("\n")) {
                    if (!part.trim().isEmpty()) expanded.add(truncateVisible(part, 80));
                }
            } else {
                expanded.add(truncateVisible(rendered, 80));
            }
        }
        return expanded;
    }

    private void applyRenderState(Objective obj, Scoreboard board, RenderState state) {
        try {
            obj.displayName(LEGACY.deserialize(state.title));
        } catch (Throwable ignored) {}
        applyLines(board, obj, state.lines);
    }

    private void applyLines(Scoreboard board, Objective obj, List<String> expanded) {
        // Clear old teams
        for (int i = 0; i < 64; i++) {
            var t = board.getTeam("oe_ln_" + i);
            if (t != null) t.unregister();
        }
        for (String e : new HashSet<>(board.getEntries())) {
            board.resetScores(e);
        }

        int score = expanded.size();
        for (int i = 0; i < expanded.size(); i++) {
            String text  = expanded.get(i);
            String entry = "§" + Integer.toHexString(i % 16);

            String teamName = "oe_ln_" + i;
            var team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);

            team.addEntry(entry);
            // LEGACY.deserialize re-inflates §x… hex sequences into proper Components,
            // so full-colour gradients survive all the way to the client.
            team.prefix(LEGACY.deserialize(text));
            team.suffix(Component.empty());

            obj.getScore(entry).setScore(score--);
        }
    }

    // -------------------------------------------------------------------------
    // Render pipeline
    // -------------------------------------------------------------------------

    /**
     * Returns an Adventure Component — used for the scoreboard title where the
     * Paper API accepts a Component directly.
     */
    private Component renderToComponent(Player p, String raw, RenderCycle cycle) {
        String legacy = renderToLegacyString(p, raw, cycle);
        return LEGACY.deserialize(legacy);
    }
    private static final Map<Character, String> AMP_TO_MINI = Map.ofEntries(
            // Formatting
            Map.entry('l', "<bold>"),
            Map.entry('o', "<italic>"),
            Map.entry('n', "<underlined>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('r', "<reset>"),
            // Colors
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
                    i++; // skip the code char
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
    /**
     * Full render pipeline.  Returns a §-colour-coded string with full hex
     * (§x§R§R§G§G§B§B) intact so that LEGACY.deserialize() can later turn it
     * into an Adventure Component without losing any gradient stops.
     *
     * *** We no longer call downsampleLegacyHexToLegacy16Safe here. ***
     *
     * That method crushed every intermediate gradient colour down to the nearest
     * of the 16 legacy codes.  Blue "survived" by coincidence because #5555FF
     * rounded back to §9 almost perfectly; every other hue was mangled.
     * Paper 1.16+ team#prefix(Component) and obj#displayName(Component) both
     * support full hex natively, so no downsampling is needed.
     */
    private String renderToLegacyString(Player p, String raw, RenderCycle cycle) {
        if (raw == null) return "";

        // 1. Inline animation tags  →  active frame text
        String s = resolveAnimatedText(raw, cycle);

        if (canShareRenderedText(s)) {
            return sharedLegacyRenderCache.computeIfAbsent(s, this::renderSharedLegacyString);
        }

        // 2. Simple player-name token
        s = s.replace("{player}", p.getName());

        // 3. <papi:placeholder>  →  %placeholder%
        s = convertPapiTags(s);

        // 4. PlaceholderAPI
        try {
            if (cycle.placeholderApiEnabled) {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
                // Some placeholders themselves contain placeholders
                if (s.contains("%"))
                    s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("PlaceholderAPI error: " + ex.getMessage());
        }

        // 5. &#RRGGBB  →  <#RRGGBB>
        s = AMP_HEX.matcher(s).replaceAll("<#$1>");
        s = MiniMessageCompat.normalizeTagAliases(s);
        // 5.5 &l, &o, &b, &6 etc. → MiniMessage equivalents
        // This makes & codes work correctly inside <gradient>, <rainbow> etc.
        s = convertAmpToMiniMessage(s);
        // 6. MiniMessage parsing (handles <gradient:…>, <#RRGGBB>, etc.)
        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                boolean nexoEnabled = Bukkit.getPluginManager().getPlugin("Nexo") != null
                        && Bukkit.getPluginManager().getPlugin("Nexo").isEnabled();
                s = nexoEnabled
                        ? parseWithNexoAdventureUtils(s)
                        : LEGACY.serialize(MM.deserialize(s));
            } catch (Throwable ignored) {}
        }

        // 7. Legacy &-codes  (must come AFTER MiniMessage so we don't break tags)
        s = ChatColor.translateAlternateColorCodes('&', s);

        // NOTE: step 8 (downsampleLegacyHexToLegacy16Safe) has been intentionally
        // removed.  It destroyed gradient colours.  The callers of this method
        // (team#prefix via LEGACY.deserialize, and obj#displayName via Component)
        // all handle full hex natively on Paper 1.16+.

        return s;
    }

    private String renderSharedLegacyString(String resolved) {
        String s = resolved;
        s = convertPapiTags(s);
        s = AMP_HEX.matcher(s).replaceAll("<#$1>");
        s = MiniMessageCompat.normalizeTagAliases(s);
        s = convertAmpToMiniMessage(s);
        if (s.indexOf('<') != -1 && s.indexOf('>') != -1) {
            try {
                boolean nexoEnabled = Bukkit.getPluginManager().getPlugin("Nexo") != null
                        && Bukkit.getPluginManager().getPlugin("Nexo").isEnabled();
                s = nexoEnabled
                        ? parseWithNexoAdventureUtils(s)
                        : LEGACY.serialize(MM.deserialize(s));
            } catch (Throwable ignored) {}
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String resolveAnimatedText(String raw, RenderCycle cycle) {
        return cycle.animatedTextCache.computeIfAbsent(raw, this::applyTagAnimations);
    }

    private boolean canShareRenderedText(String raw) {
        if (raw == null || raw.isEmpty()) return true;
        return sharedTemplateCache.computeIfAbsent(raw, key -> {
            if (key.contains("{player}") || key.contains("<papi:")) {
                return false;
            }
            return !key.contains("%");
        });
    }

    private boolean isPlaceholderApiEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
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
            return LEGACY.serialize(MM.deserialize(input));
        } catch (Throwable ignored) {
            return input;
        }
    }

    // -------------------------------------------------------------------------
    // Animation tag parser
    // -------------------------------------------------------------------------

    /**
     * Resolves  %animations_<tag interval=N> frame1 | frame2 | … </tag>%
     * into the currently-active frame.
     *
     * Fix applied here: trailing blank strings that YAML's block-scalar (|-)
     * can produce are stripped from the frames list before indexing.
     */
    private String applyTagAnimations(String input) {
        if (input == null || input.isEmpty()) return input;

        Matcher      m   = ANIM_TAG.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            int interval = 20;
            try {
                if (m.group(1) != null) interval = Math.max(1, Integer.parseInt(m.group(1)));
            } catch (Throwable ignored) {}

            String body = m.group(2) == null ? "" : m.group(2);

            List<String> frames  = new ArrayList<>();
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
            // Add the last frame
            String lastFrame = current.toString();
            if (lastFrame.endsWith("\n"))
                lastFrame = lastFrame.substring(0, lastFrame.length() - 1);
            frames.add(lastFrame);

            // FIX: strip frames that are blank (artifact of YAML "|-" block scalar)
            frames.removeIf(f -> f.trim().isEmpty());

            // Trim trailing newline from each remaining frame
            frames.replaceAll(f -> f.endsWith("\n") ? f.substring(0, f.length() - 1) : f);

            String replacement;
            if (frames.isEmpty()) {
                replacement = "";
            } else {
                long ticks = System.currentTimeMillis() / 50L;
                int  idx   = (int) ((ticks / interval) % frames.size());
                replacement = frames.get(Math.max(0, idx));
            }

            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }

        m.appendTail(out);
        return out.toString();
    }

    // -------------------------------------------------------------------------
    // Colour helpers
    // -------------------------------------------------------------------------

    /**
     * Converts <papi:placeholder> tags to %placeholder% for PlaceholderAPI.
     */
    private String convertPapiTags(String input) {
        if (input == null || !input.contains("<papi:")) return input;

        Matcher      m   = PAPI_TAG.matcher(input);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("%" + m.group(1) + "%"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Truncates a legacy-colour-coded string to at most {@code maxVisible}
     * visible characters, leaving colour codes intact.
     */
    private static String truncateVisible(String s, int maxVisible) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out     = new StringBuilder(s.length());
        int           visible = 0;

        for (int i = 0; i < s.length() && visible < maxVisible; ) {
            char c = s.charAt(i);

            if (c == '§' && i + 1 < s.length()) {
                char code = s.charAt(i + 1);
                // Full hex:  §x§R§R§G§G§B§B  (14 chars)
                if ((code == 'x' || code == 'X') && i + 13 < s.length()) {
                    out.append(s, i, i + 14);
                    i += 14;
                    continue;
                }
                // Normal code: §a, §l, §r, …
                out.append(c).append(code);
                i += 2;
                continue;
            }

            out.append(c);
            visible++;
            i++;
        }
        return out.toString();
    }

    /**
     * Maps an RGB value to its nearest Minecraft legacy colour code.
     * Kept for any future use where a true legacy-only output is required,
     * but is NOT called during the normal render pipeline any more.
     */
    @SuppressWarnings("unused")
    private static char nearestLegacyColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;

        int[][] palette = {
                {0x00,0x00,0x00,'0'}, {0x00,0x00,0xAA,'1'},
                {0x00,0xAA,0x00,'2'}, {0x00,0xAA,0xAA,'3'},
                {0xAA,0x00,0x00,'4'}, {0xAA,0x00,0xAA,'5'},
                {0xFF,0xAA,0x00,'6'}, {0xAA,0xAA,0xAA,'7'},
                {0x55,0x55,0x55,'8'}, {0x55,0x55,0xFF,'9'},
                {0x55,0xFF,0x55,'a'}, {0x55,0xFF,0xFF,'b'},
                {0xFF,0x55,0x55,'c'}, {0xFF,0x55,0xFF,'d'},
                {0xFF,0xFF,0x55,'e'}, {0xFF,0xFF,0xFF,'f'}
        };

        int  best     = Integer.MAX_VALUE;
        char bestCode = 'f';
        for (int[] p : palette) {
            int dr = r - p[0], dg = g - p[1], db = b - p[2];
            int dist = dr*dr + dg*dg + db*db;
            if (dist < best) { best = dist; bestCode = (char) p[3]; }
        }
        return bestCode;
    }

    // -------------------------------------------------------------------------
    // Nametag helper
    // -------------------------------------------------------------------------

    private void forceNametagUpdate(Player p) {
        try {
            if (plugin.getNametagManager() != null)
                plugin.getNametagManager().forceUpdate(p);
        } catch (Throwable ignored) {}
    }

    // -------------------------------------------------------------------------
    // Toggle persistence
    // -------------------------------------------------------------------------

    /** Loads all saved toggle preferences from disk into the in-memory map. Called once at startup. */
    private void loadPersistedToggles() {
        if (!toggleFile.exists()) return;
        try {
            org.bukkit.configuration.file.YamlConfiguration y =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(toggleFile);
            for (String key : y.getKeys(false)) {
                try { toggles.put(UUID.fromString(key), y.getBoolean(key)); }
                catch (IllegalArgumentException ignored) {}
            }
            plugin.getLogger().info("[Scoreboard] Loaded " + toggles.size() + " saved toggle preference(s).");
        } catch (Exception e) {
            plugin.getLogger().warning("[Scoreboard] Could not load sb-toggles.yml: " + e.getMessage());
        }
    }

    /** Persists a single player's toggle preference to disk asynchronously. */
    private void saveToggle(UUID id, boolean value) {
        OreScheduler.runAsync(plugin, () -> {
            try {
                File dir = toggleFile.getParentFile();
                if (!dir.exists()) dir.mkdirs();
                org.bukkit.configuration.file.YamlConfiguration y = toggleFile.exists()
                        ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(toggleFile)
                        : new org.bukkit.configuration.file.YamlConfiguration();
                y.set(id.toString(), value);
                y.save(toggleFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[Scoreboard] Could not save toggle for " + id + ": " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.enabled()) return;
        Player p = e.getPlayer();

        if (OreScheduler.isFolia()) {
            // On Folia: use entity scheduler so the task runs on the player's region
            // thread. This ensures PAPI placeholders can access player data safely and
            // that the scoreboard packets are sent after the client has finished its
            // login sequence (20 ticks = 1 s delay).
            OreScheduler.runLaterForEntity(plugin, p, () -> {
                if (p.isOnline() && shouldShow(p)) show(p);
                else if (p.isOnline()) forceNametagUpdate(p);
            }, plugin.getJoinUiDelayTicks(p, 20L));
        } else {
            // Paper: global scheduler on main thread, 2-tick delay is fine.
            OreScheduler.runLater(plugin, () -> {
                if (shouldShow(p)) show(p);
                else forceNametagUpdate(p);
            }, plugin.getJoinUiDelayTicks(p, 2L));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        shown.remove(id);
        playerBoards.remove(id);
        // Must remove Folia scoreboard on quit so the next login creates a fresh
        // instance with the new Player reference (not the stale old one).
        FoliaScoreboard fs = foliaBoards.remove(id);
        if (fs != null) { try { fs.hide(); } catch (Throwable ignored) {} }
        // toggles intentionally kept in memory — preference is re-applied on next login
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (shouldShow(p)) show(p);
        else hide(p);
    }

    // -------------------------------------------------------------------------
    // World / toggle helpers
    // -------------------------------------------------------------------------

    private boolean isWorldAllowedByLists(Player p) {
        String w  = p.getWorld().getName();
        var    wl = cfg.worldsWhitelist();
        var    bl = cfg.worldsBlacklist();
        if (!wl.isEmpty() && !wl.contains(w)) return false;
        if (!bl.isEmpty() &&  bl.contains(w)) return false;
        return true;
    }

    private boolean shouldShow(Player p) {
        if (!isWorldAllowedByLists(p)) {
            return false;
        }
        Boolean pref = toggles.get(p.getUniqueId());
        return pref == null ? cfg.defaultEnabled() : pref;
    }
}
