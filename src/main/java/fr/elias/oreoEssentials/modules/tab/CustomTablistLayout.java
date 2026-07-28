package fr.elias.oreoEssentials.modules.tab;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomTablistLayout {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00A7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static final Pattern AMP_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern CLOSING_COLOR_TAG = Pattern.compile(
            "</(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>",
            Pattern.CASE_INSENSITIVE);

    // ── <center> support ──────────────────────────────────────────────────────
    /** Matches <center>…</center> blocks (case-insensitive, may span lines). */
    private static final Pattern CENTER_TAG = Pattern.compile(
            "(?i)<center>(.*?)</center>", Pattern.DOTALL);
    /** Strip all formatting tags/codes to measure plain-text pixel width. */
    private static final Pattern STRIP_FORMAT_FOR_MEASURE = Pattern.compile(
            "<[^>]+>|§.|&[0-9a-fk-orA-FK-OR]");
    /** Centering target in pixels — single-column or full-width header. */
    private static final int TAB_COL_PX  = 320;
    /** Centering target in pixels — each column of the 2-column layout. */
    private static final int TAB_COL2_PX = 180;
    /** Average Minecraft default-font character width in pixels. */
    private static final int AVG_CHAR_PX = 6;
    /** Space character width in pixels. */
    private static final int SPACE_PX    = 4;

    /** True when PacketEvents is on the classpath — enables fake-entry tab mode. */
    private static final boolean PACKET_EVENTS_AVAILABLE;
    static {
        boolean ok;
        try { Class.forName("com.github.retrooper.packetevents.PacketEvents"); ok = true; }
        catch (ClassNotFoundException e) { ok = false; }
        PACKET_EVENTS_AVAILABLE = ok;
    }

    private static volatile Method papiSetPlaceholdersMethod;
    private static volatile boolean papiLookupAttempted;

    // ── Fields ────────────────────────────────────────────────────────────────
    private final OreoEssentials plugin;
    private final TabListManager tabManager;

    /** Packet-based tab list manager (non-null only when PacketEvents is available). */
    private PacketTablistManager packetTablistManager;

    // Legacy (Bukkit API) caches — only used when PacketEvents is not available
    private final Map<UUID, String> headerCache         = new HashMap<>();
    private final Map<UUID, String> footerCache         = new HashMap<>();
    private final Map<UUID, String> playerListNameCache = new HashMap<>();
    private final Map<String, String> sharedColorCache  = new HashMap<>();

    private OreTask updateTask;
    private int currentFrame = 0;
    private long lastFrameChange = 0;
    private int updateCadenceTicks = 20;
    private int viewerRefreshCursor = 0;
    private int playerNameRefreshTicksRemaining = 0;
    private double headerFooterBatchBudget = 0.0d;
    private List<PlayerTabEntry> cachedSortedEntries = new ArrayList<>();
    private int cachedFrameAtLastSend = -1;
    private int contentRefreshCounter = 0;
    private volatile boolean entriesDirty = true;
    private int delistResyncCounter = 0;
    private static final int DELIST_RESYNC_INTERVAL = 100; // every 5 seconds
    // Viewer batching — spreads per-viewer slot builds across multiple ticks
    private int pendingViewerUpdates = 0;
    private int viewerBatchCursor = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CustomTablistLayout(OreoEssentials plugin, TabListManager tabManager) {
        this.plugin = plugin;
        this.tabManager = tabManager;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start(int intervalTicks) {
        stop();
        updateCadenceTicks = Math.max(1, intervalTicks);
        playerNameRefreshTicksRemaining = 0;

        if (PACKET_EVENTS_AVAILABLE) {
            packetTablistManager = new PacketTablistManager();
            registerJoinQuitListener();
            plugin.getLogger().info("[CustomTab] Packet-based tab list active (PacketEvents mode).");
        }

        updateTask = OreScheduler.runTimer(plugin, () -> {
            try { updateCustomTablist(); }
            catch (Exception e) {
                plugin.getLogger().warning("[CustomTab] Error: " + e.getMessage());
            }
        }, 20L, 1L);
    }

    public void stop() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        headerCache.clear();
        footerCache.clear();
        playerListNameCache.clear();
        sharedColorCache.clear();
        viewerRefreshCursor = 0;
        playerNameRefreshTicksRemaining = 0;
        headerFooterBatchBudget = 0.0d;
        cachedSortedEntries = new ArrayList<>();
        cachedFrameAtLastSend = -1;
        contentRefreshCounter = 0;
        entriesDirty = true;
        delistResyncCounter = 0;
        pendingViewerUpdates = 0;
        viewerBatchCursor = 0;
        if (packetTablistManager != null) {
            packetTablistManager.cleanupAll();
            packetTablistManager = null;
        }
    }

    private void registerJoinQuitListener() {
        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.MONITOR)
            public void onJoin(PlayerJoinEvent e) {
                entriesDirty = true;
                if (packetTablistManager == null) return;
                Player viewer = e.getPlayer();
                // Delay until the client has fully loaded the tab list
                OreScheduler.runLater(plugin, () -> {
                    if (!viewer.isOnline() || packetTablistManager == null) return;
                    // Force a fresh init for this viewer on next render cycle
                    packetTablistManager.cleanupViewer(viewer.getUniqueId());
                    // De-list real players from vanilla tab for this viewer
                    packetTablistManager.delistRealPlayers(viewer);
                    // De-list the new joiner from all other viewers' vanilla tabs
                    packetTablistManager.delistPlayerForAll(viewer);
                }, plugin.getJoinUiDelayTicks(viewer, 5L));
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onQuit(PlayerQuitEvent e) {
                entriesDirty = true;
                if (packetTablistManager == null) return;
                packetTablistManager.cleanupViewer(e.getPlayer().getUniqueId());
                packetTablistManager.removePlayerFakeEntry(e.getPlayer());
            }

        }, plugin);
    }

    // ── Main update loop ──────────────────────────────────────────────────────

    private void updateCustomTablist() {
        FileConfiguration cfg = tabManager.getConfig();
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            headerCache.clear();
            footerCache.clear();
            playerListNameCache.clear();
            viewerRefreshCursor = 0;
            headerFooterBatchBudget = 0.0d;
            return;
        }

        List<Player> players = new ArrayList<>(onlinePlayers.size());
        for (Player player : onlinePlayers) {
            if (plugin.isJoinUiReady(player)) players.add(player);
        }
        if (players.isEmpty()) {
            pruneCaches(onlinePlayers);
            return;
        }

        advanceFrame(cfg.getInt("tab.custom-layout.change-interval", 80));
        int onlineCount = onlinePlayers.size();

        if (PACKET_EVENTS_AVAILABLE && packetTablistManager != null) {
            updatePacketTablist(players, cfg, onlineCount);
        } else {
            updateClassicTablist(players, cfg, onlineCount);
        }

        pruneCaches(onlinePlayers);
    }

    // ── Packet-based tab list ─────────────────────────────────────────────────

    private void updatePacketTablist(List<Player> players, FileConfiguration cfg, int onlineCount) {
        // ── 1. Rebuild sorted entries only when dirty or on schedule ──────────
        contentRefreshCounter++;
        boolean contentDue = contentRefreshCounter >= updateCadenceTicks;
        if (contentDue || entriesDirty || cachedSortedEntries.isEmpty()) {
            if (contentDue) contentRefreshCounter = 0;
            entriesDirty = false;
            cachedSortedEntries = buildSortedPlayerEntries(players, cfg, onlineCount);
        }

        // ── 2. When content or frame changes, queue all viewers for re-send ───
        boolean frameChanged = (currentFrame != cachedFrameAtLastSend);
        if (contentDue || frameChanged) {
            cachedFrameAtLastSend = currentFrame;
            // Queue all viewers — the batch loop below drains them over N ticks
            pendingViewerUpdates = players.size();
            viewerBatchCursor = 0;
        }

        // ── 3. Nothing pending — nothing to do ───────────────────────────────
        if (pendingViewerUpdates <= 0) return;

        // ── 4. Periodic de-list resync (every 100 ticks) ─────────────────────
        delistResyncCounter++;
        if (delistResyncCounter >= DELIST_RESYNC_INTERVAL) {
            delistResyncCounter = 0;
            for (Player viewer : players) {
                packetTablistManager.delistRealPlayers(viewer);
            }
        }

        boolean twoCol = cfg.getBoolean("tab.two-column-layout.enabled", false);
        int numCols = twoCol ? Math.max(1, Math.min(3, cfg.getInt("tab.two-column-layout.columns", 2))) : 0;

        // Build header/footer once per tick (shared across all viewers in this batch)
        Component tabHeader = Component.empty();
        Component tabFooter = Component.empty();
        if (twoCol && !players.isEmpty()) {
            Player sample = players.get(0);

            String borderStr = cfg.getString("tab.two-column-layout.border", "");
            Component border = borderStr.isBlank() ? Component.empty()
                    : parseToComponent(applyPlaceholders(sample, borderStr, onlineCount));

            List<String> titleTexts = cfg.getStringList("tab.two-column-layout.header-title-texts");
            if (!titleTexts.isEmpty()) {
                String titleStr = titleTexts.get(currentFrame % titleTexts.size());
                Component title = parseToComponent(applyPlaceholders(sample, titleStr, onlineCount));
                tabHeader = border.equals(Component.empty())
                        ? title
                        : border.append(Component.newline()).append(title);
            } else {
                String headerStr = cfg.getString("tab.two-column-layout.header", "");
                if (!headerStr.isBlank())
                    tabHeader = parseToComponent(applyPlaceholders(sample, headerStr, onlineCount));
            }

            String footerStr = cfg.getString("tab.two-column-layout.footer", "");
            if (!footerStr.isBlank())
                tabFooter = parseToComponent(applyPlaceholders(sample, footerStr, onlineCount));
        }

        // ── 5. Process a batch of viewers this tick ───────────────────────────
        // Spread all pending viewers evenly across updateCadenceTicks ticks.
        // e.g. 400 viewers / 200 ticks = 2 viewers per tick → zero burst.
        int total = players.size();
        int batchSize = Math.max(1, (int) Math.ceil((double) total / updateCadenceTicks));

        for (int i = 0; i < batchSize && pendingViewerUpdates > 0; i++) {
            int idx = viewerBatchCursor % total;
            viewerBatchCursor++;
            pendingViewerUpdates--;

            Player viewer = players.get(idx);
            List<PacketTablistManager.TabSlot> slots = twoCol
                    ? buildMultiColumnTabSlots(viewer, cfg, cachedSortedEntries, onlineCount, numCols)
                    : buildTabSlots(viewer, cfg, cachedSortedEntries, onlineCount);
            packetTablistManager.updateViewer(viewer, slots);
            if (twoCol) {
                viewer.sendPlayerListHeaderAndFooter(tabHeader, tabFooter);
            }
        }

        playerNameRefreshTicksRemaining--;
        if (playerNameRefreshTicksRemaining <= 0) {
            playerNameRefreshTicksRemaining = updateCadenceTicks;
        }
    }

    /**
     * Build the complete list of tab slots for a viewer.
     *
     * Slot layout:
     *   [0..N-1]    Top section — one slot per line of the current animated frame
     *   [N..N+P-1]  Player section — one slot per online player (with their skin)
     *   [N+P..end]  Bottom section — one slot per line of the current animated frame
     */
    private List<PacketTablistManager.TabSlot> buildTabSlots(
            Player viewer, FileConfiguration cfg,
            List<PlayerTabEntry> sortedEntries, int onlineCount) {

        List<PacketTablistManager.TabSlot> slots = new ArrayList<>();

        // ── Top section ──────────────────────────────────────────────────────
        List<String> topFrames = cfg.getStringList("tab.custom-layout.top-section.texts");
        if (!topFrames.isEmpty()) {
            String topText = topFrames.get(currentFrame % topFrames.size());
            topText = applyPlaceholders(viewer, topText, onlineCount);
            String[] topLines = topText.split("\n", -1);
            for (int i = 0; i < topLines.length; i++) {
                Component display = topLines[i].isBlank()
                        ? Component.empty()
                        : parseToComponent(topLines[i]);
                slots.add(new PacketTablistManager.TabSlot(
                        PacketTablistManager.topSlotUuid(i),
                        "\u0001_" + String.format("%02d", i),
                        display,
                        PacketTablistManager.NO_SKIN,
                        1   // green bar (decorative)
                ));
            }
        } else {
            // single-line header config
            String line1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-1", ""), onlineCount);
            String line2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-2", ""), onlineCount);
            String line3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-3", ""), onlineCount);
            String[] lines = {line1, line2, line3};
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isBlank()) continue;
                slots.add(new PacketTablistManager.TabSlot(
                        PacketTablistManager.topSlotUuid(i),
                        "\u0001_" + String.format("%02d", i),
                        parseToComponent(lines[i]),
                        PacketTablistManager.NO_SKIN, 1
                ));
            }
        }

        // ── Player section ───────────────────────────────────────────────────
        boolean playerSectionEnabled = cfg.getBoolean("tab.custom-layout.player-section.enabled", true);
        if (playerSectionEnabled) {
            for (int i = 0; i < sortedEntries.size(); i++) {
                PlayerTabEntry entry = sortedEntries.get(i);
                Player target = entry.player();

                // Get skin textures via PacketEvents
                List<TextureProperty> skin = packetTablistManager.getPlayerSkin(target);
                if (skin == null) skin = PacketTablistManager.NO_SKIN;
                boolean skinChanged = packetTablistManager.checkAndUpdateSkinVersion(target.getUniqueId(), skin);

                Component display = parseToComponent(entry.displayName());

                slots.add(new PacketTablistManager.TabSlot(
                        PacketTablistManager.playerFakeUuid(target.getUniqueId()),
                        "\u0002_" + target.getName(),
                        display,
                        skin,
                        target.getPing(),
                        skinChanged
                ));
            }
        }

        // ── Bottom section ───────────────────────────────────────────────────
        List<String> bottomFrames = cfg.getStringList("tab.custom-layout.bottom-section.texts");
        if (!bottomFrames.isEmpty()) {
            String bottomText = bottomFrames.get(currentFrame % bottomFrames.size());
            bottomText = applyPlaceholders(viewer, bottomText, onlineCount);
            String[] bottomLines = bottomText.split("\n", -1);
            for (int i = 0; i < bottomLines.length; i++) {
                Component display = bottomLines[i].isBlank()
                        ? Component.empty()
                        : parseToComponent(bottomLines[i]);
                slots.add(new PacketTablistManager.TabSlot(
                        PacketTablistManager.bottomSlotUuid(i),
                        "\u0003_" + String.format("%02d", i),
                        display,
                        PacketTablistManager.NO_SKIN,
                        1
                ));
            }
        } else {
            String fLine1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-1", ""), onlineCount);
            String fLine2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-2", ""), onlineCount);
            String fLine3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-3", ""), onlineCount);
            String[] lines = {fLine1, fLine2, fLine3};
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isBlank()) continue;
                slots.add(new PacketTablistManager.TabSlot(
                        PacketTablistManager.bottomSlotUuid(i),
                        "\u0003_" + String.format("%02d", i),
                        parseToComponent(lines[i]),
                        PacketTablistManager.NO_SKIN, 1
                ));
            }
        }

        return slots;
    }

    // ── Classic (Bukkit API) tab list ─────────────────────────────────────────

    private void updateClassicTablist(List<Player> players, FileConfiguration cfg, int onlineCount) {
        SharedHeaderFooter sharedHF = resolveSharedHeaderFooter(cfg);
        headerFooterBatchBudget = Math.min(players.size(),
                headerFooterBatchBudget + ((double) players.size() / Math.max(1, updateCadenceTicks)));
        int viewerBatchSize = (int) Math.floor(headerFooterBatchBudget);
        headerFooterBatchBudget -= viewerBatchSize;

        for (int i = 0; i < viewerBatchSize; i++) {
            Player viewer = players.get(viewerRefreshCursor % players.size());
            viewerRefreshCursor = (viewerRefreshCursor + 1) % Math.max(1, players.size());
            try { updateHeaderFooter(viewer, cfg, onlineCount, sharedHF); }
            catch (Exception e) {
                plugin.getLogger().warning("[CustomTab] Header/footer error for " + viewer.getName() + ": " + e.getMessage());
            }
        }

        playerNameRefreshTicksRemaining--;
        if (playerNameRefreshTicksRemaining <= 0) {
            updatePlayerNames(players, cfg, onlineCount);
            playerNameRefreshTicksRemaining = updateCadenceTicks;
        }
    }

    private void updateHeaderFooter(Player viewer, FileConfiguration cfg, int onlineCount, SharedHeaderFooter sharedHF) {
        boolean headerAnimated = cfg.contains("tab.custom-layout.top-section.texts");
        boolean footerAnimated = cfg.contains("tab.custom-layout.bottom-section.texts");

        String headerText;
        if (sharedHF.headerText() != null) {
            headerText = sharedHF.headerText();
        } else if (headerAnimated) {
            headerText = getAnimatedText(cfg, "tab.custom-layout.top-section.texts", viewer, onlineCount);
        } else {
            String l1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-1", "&f"), onlineCount);
            String l2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-2", ""), onlineCount);
            String l3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.top-section.line-3", ""), onlineCount);
            headerText = colorMaybeCached(l1) + "\n" + colorMaybeCached(l2) + "  " + colorMaybeCached(l3);
        }

        String footerText;
        if (sharedHF.footerText() != null) {
            footerText = sharedHF.footerText();
        } else if (footerAnimated) {
            footerText = getAnimatedText(cfg, "tab.custom-layout.bottom-section.texts", viewer, onlineCount);
        } else {
            String f1 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-1", ""), onlineCount);
            String f2 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-2", ""), onlineCount);
            String f3 = applyPlaceholders(viewer, cfg.getString("tab.custom-layout.bottom-section.line-3", ""), onlineCount);
            footerText = "\n" + colorMaybeCached(f1) + "  " + colorMaybeCached(f2) + "\n" + colorMaybeCached(f3);
        }

        UUID viewerId = viewer.getUniqueId();
        if (!Objects.equals(headerCache.get(viewerId), headerText)
                || !Objects.equals(footerCache.get(viewerId), footerText)) {
            viewer.setPlayerListHeaderFooter(headerText, footerText);
            headerCache.put(viewerId, headerText);
            footerCache.put(viewerId, footerText);
        }
    }

    private void updatePlayerNames(List<Player> players, FileConfiguration cfg, int onlineCount) {
        if (!cfg.getBoolean("tab.custom-layout.player-section.enabled", true)) return;

        for (PlayerTabEntry entry : buildSortedPlayerEntries(players, cfg, onlineCount)) {
            Player target = entry.player();
            String displayName = entry.displayName();
            UUID targetId = target.getUniqueId();
            if (Objects.equals(playerListNameCache.get(targetId), displayName)) continue;
            try {
                String mm = AMP_HEX.matcher(displayName).replaceAll("<#$1>");
                mm = CLOSING_COLOR_TAG.matcher(mm).replaceAll("<reset>");
                mm = mm.replace('\u00A7', '&');
                mm = MiniMessageCompat.normalizeTagAliases(convertAmpToMiniMessage(mm));
                target.playerListName(MM.deserialize(mm));
                playerListNameCache.put(targetId, displayName);
            } catch (Throwable e) {
                try {
                    target.setPlayerListName(color(displayName));
                    playerListNameCache.put(targetId, displayName);
                } catch (Exception ignored) {}
            }
        }
    }

    // ── Multi-column layout (N × 20 slots, forces Minecraft N-column display) ───

    /** Rows per column — always 20 (Minecraft's column height). */
    private static final int COL_ROWS = 20;

    /**
     * Sort-key prefixes per column. Minecraft orders tab entries lexicographically
     * by profile name, so \u0001A_ < \u0001B_ < \u0001C_ → col 1, 2, 3.
     */
    private static final String[] COL_PREFIXES = {"\u0001A_", "\u0001B_", "\u0001C_"};

    /**
     * Config key suffixes for top/bottom decoration sections, indexed by column
     * position for 1-col, 2-col, and 3-col layouts respectively.
     *
     * For 1 column  → [left]
     * For 2 columns → [left, right]
     * For 3 columns → [left, center, right]
     */
    private static final String[][] TOP_KEYS = {
            {"top-left"},
            {"top-left", "top-right"},
            {"top-left", "top-center", "top-right"}
    };
    private static final String[][] BOT_KEYS = {
            {"bottom-left"},
            {"bottom-left", "bottom-right"},
            {"bottom-left", "bottom-center", "bottom-right"}
    };

    /**
     * Build exactly {@code numCols × 20} tab slots.
     *
     * Minecraft renders sorted entries as columns of 20:
     *   numCols=1 →  20 slots → 1 column
     *   numCols=2 →  40 slots → 2 columns
     *   numCols=3 →  60 slots → 3 columns
     *
     * Row structure per column:
     *   [0 .. topRows-1]        top decoration (stats / separators)
     *   [topRows .. botStart-1] player rows  (skins + names)
     *   [botStart .. 19]        bottom decoration (links / URL)
     *
     * The border/title are sent as tab header/footer and span the full width.
     */
    @SuppressWarnings("unchecked")
    private List<PacketTablistManager.TabSlot> buildMultiColumnTabSlots(
            Player viewer, FileConfiguration cfg,
            List<PlayerTabEntry> sortedEntries, int onlineCount, int numCols) {

        numCols = Math.max(1, Math.min(3, numCols));
        PacketTablistManager.TabSlot[][] cols =
                (PacketTablistManager.TabSlot[][]) new PacketTablistManager.TabSlot[numCols][COL_ROWS];

        String[] topKeys = TOP_KEYS[numCols - 1];
        String[] botKeys = BOT_KEYS[numCols - 1];

        // ── Resolve decoration lines for each column ───────────────────────
        String[][] topLines = new String[numCols][];
        String[][] botLines = new String[numCols][];
        int maxTopRows = 0, maxBotRows = 0;

        for (int c = 0; c < numCols; c++) {
            topLines[c] = getTwoColSectionLines(cfg,
                    "tab.two-column-layout." + topKeys[c] + ".texts", viewer, onlineCount);
            botLines[c] = getTwoColSectionLines(cfg,
                    "tab.two-column-layout." + botKeys[c] + ".texts", viewer, onlineCount);
            maxTopRows = Math.max(maxTopRows, topLines[c].length);
            maxBotRows = Math.max(maxBotRows, botLines[c].length);
        }

        int topRows  = Math.min(maxTopRows, COL_ROWS - 2);
        int botRows  = Math.min(maxBotRows, COL_ROWS - topRows);
        int botStart = COL_ROWS - botRows;

        // ── Fill top and bottom decoration rows ────────────────────────────
        for (int c = 0; c < numCols; c++) {
            String prefix = COL_PREFIXES[c];
            for (int i = 0; i < topRows; i++) {
                String text = i < topLines[c].length ? topLines[c][i] : "";
                cols[c][i] = makeDecorSlot(PacketTablistManager.colSlotUuid(c, i),
                        prefix + fmt2(i), text, TAB_COL2_PX);
            }
            for (int i = 0; i < botRows; i++) {
                int row = botStart + i;
                String text = i < botLines[c].length ? botLines[c][i] : "";
                cols[c][row] = makeDecorSlot(PacketTablistManager.colSlotUuid(c, row),
                        prefix + fmt2(row), text, TAB_COL2_PX);
            }
        }

        // ── Fill player rows ───────────────────────────────────────────────
        // Players are distributed evenly across columns:
        //   numCols=1 → all players in col 0
        //   numCols=2 → first ⌈N/2⌉ in col 0, rest in col 1
        //   numCols=3 → first ⌈N/3⌉ in col 0, next ⌈N/3⌉ in col 1, rest in col 2
        boolean playerSectionEnabled = cfg.getBoolean("tab.custom-layout.player-section.enabled", true);
        if (playerSectionEnabled) {
            int total     = sortedEntries.size();
            int availRows = botStart - topRows;        // player rows available per column
            int chunkSize = (total + numCols - 1) / numCols; // ⌈total/numCols⌉

            for (int c = 0; c < numCols; c++) {
                String prefix   = COL_PREFIXES[c];
                int startIdx    = c * chunkSize;
                for (int slot = 0; slot < availRows; slot++) {
                    int pIdx = startIdx + slot;
                    if (pIdx >= total) break;
                    int row = topRows + slot;
                    PlayerTabEntry e = sortedEntries.get(pIdx);
                    Player target = e.player();
                    List<TextureProperty> skin = packetTablistManager.getPlayerSkin(target);
                    if (skin == null) skin = PacketTablistManager.NO_SKIN;
                    boolean skinChanged = packetTablistManager.checkAndUpdateSkinVersion(target.getUniqueId(), skin);
                    cols[c][row] = new PacketTablistManager.TabSlot(
                            PacketTablistManager.colSlotUuid(c, row), prefix + fmt2(row),
                            parseToComponent(e.displayName(), TAB_COL2_PX),
                            skin, target.getPing(), skinChanged);
                }
            }
        }

        // ── Collect all slots (empty slots fill unused rows) ───────────────
        List<PacketTablistManager.TabSlot> slots = new ArrayList<>(numCols * COL_ROWS);
        for (int c = 0; c < numCols; c++) {
            String prefix = COL_PREFIXES[c];
            for (int i = 0; i < COL_ROWS; i++) {
                slots.add(cols[c][i] != null ? cols[c][i]
                        : new PacketTablistManager.TabSlot(
                                PacketTablistManager.colSlotUuid(c, i), prefix + fmt2(i),
                                Component.empty(), PacketTablistManager.NO_SKIN, 1));
            }
        }
        return slots;
    }

    /** Build a decoration slot (no skin). */
    private PacketTablistManager.TabSlot makeDecorSlot(UUID uuid, String sortKey, String text) {
        return makeDecorSlot(uuid, sortKey, text, TAB_COL_PX);
    }

    private PacketTablistManager.TabSlot makeDecorSlot(UUID uuid, String sortKey, String text, int colPx) {
        Component display = (text == null || text.isBlank()) ? Component.empty() : parseToComponent(text, colPx);
        return new PacketTablistManager.TabSlot(uuid, sortKey, display, PacketTablistManager.NO_SKIN, 1);
    }

    /**
     * Return the current animated frame for a 2-column section, split by newline.
     * Empty array if the config path has no entries.
     */
    private String[] getTwoColSectionLines(FileConfiguration cfg, String path,
                                           Player viewer, int onlineCount) {
        List<String> frames = cfg.getStringList(path);
        if (frames.isEmpty()) return new String[0];
        String frame = frames.get(currentFrame % frames.size());
        frame = applyPlaceholders(viewer, frame, onlineCount);
        return frame.split("\n", -1);
    }

    /** Zero-padded 2-digit row index, e.g. "03". */
    private static String fmt2(int n) { return String.format("%02d", n); }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private List<PlayerTabEntry> buildSortedPlayerEntries(
            List<Player> players, FileConfiguration cfg, int onlineCount) {

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
                .comparingInt(PlayerTabEntry::rankPriority).reversed()
                .thenComparing(e -> e.player().getName(), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    /**
     * Pre-process {@code <center>…</center>} blocks by prepending spaces so the
     * inner text appears approximately centred in a tab-list column.
     * The estimate uses 6 px average char width against a 320 px target width.
     */
    private static String processCenterTags(String text) {
        return processCenterTags(text, TAB_COL_PX);
    }

    private static String processCenterTags(String text, int colPx) {
        if (text == null || text.isEmpty()) return text;
        if (!text.toLowerCase().contains("<center>")) return text;
        Matcher m = CENTER_TAG.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String inner = m.group(1).trim();
            String plain = STRIP_FORMAT_FOR_MEASURE.matcher(inner).replaceAll("");
            int leading = Math.max(0, (colPx - plain.length() * AVG_CHAR_PX) / 2 / SPACE_PX);
            m.appendReplacement(sb, Matcher.quoteReplacement(" ".repeat(leading) + inner));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Component parseToComponent(String text) {
        return parseToComponent(text, TAB_COL_PX);
    }

    /** Parse a legacy/MiniMessage string to a Component (for packet-mode display names). */
    private Component parseToComponent(String text, int colPx) {
        if (text == null || text.isEmpty()) return Component.empty();
        text = processCenterTags(text, colPx);
        String s = AMP_HEX.matcher(text).replaceAll("<#$1>");
        s = CLOSING_COLOR_TAG.matcher(s).replaceAll("<reset>");
        s = s.replace('\u00A7', '&');
        s = MiniMessageCompat.normalizeTagAliases(convertAmpToMiniMessage(s));
        try {
            return MM.deserialize(s);
        } catch (Throwable e) {
            return Component.text(ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    private void advanceFrame(int changeInterval) {
        int safeInterval = Math.max(1, changeInterval);
        long now = System.currentTimeMillis();
        long ticksPassed = (now - lastFrameChange) / 50L;
        if (lastFrameChange == 0L || ticksPassed >= safeInterval) {
            currentFrame++;
            lastFrameChange = now;
        }
    }

    private String getAnimatedText(FileConfiguration cfg, String path, Player viewer, int onlineCount) {
        List<String> frames = cfg.getStringList(path);
        if (frames.isEmpty()) return "";
        String frame = frames.get(currentFrame % frames.size());
        frame = applyPlaceholders(viewer, frame, onlineCount);
        return colorMaybeCached(frame);
    }

    private SharedHeaderFooter resolveSharedHeaderFooter(FileConfiguration cfg) {
        return new SharedHeaderFooter(
                resolveSharedAnimatedText(cfg, "tab.custom-layout.top-section.texts"),
                resolveSharedAnimatedText(cfg, "tab.custom-layout.bottom-section.texts"));
    }

    private String resolveSharedAnimatedText(FileConfiguration cfg, String path) {
        List<String> frames = cfg.getStringList(path);
        if (frames.isEmpty() || !allFramesShareable(frames)) return null;
        return colorMaybeCached(frames.get(currentFrame % frames.size()));
    }

    private boolean allFramesShareable(List<String> frames) {
        for (String f : frames) { if (!isShareableText(f)) return false; }
        return true;
    }

    private boolean isShareableText(String text) {
        return text != null && text.indexOf('%') == -1 && text.indexOf('{') == -1 && !text.contains("<papi:");
    }

    private String formatPlayerName(Player player, FileConfiguration cfg, String primaryGroup, boolean afk) {
        String format = cfg.getString("tab.custom-layout.player-section.player-format.format",
                "%player_color%%player_name%%afk_indicator%");
        String rankColor = getRankColor(primaryGroup, cfg);
        String afkIndicator = "";
        if (afk && cfg.getBoolean("tab.custom-layout.player-section.player-format.show-afk", true)) {
            afkIndicator = colorMaybeCached(cfg.getString("tab.custom-layout.player-section.player-format.afk-format", " &7AFK"));
        }
        return format.replace("%player_color%", rankColor)
                     .replace("%player_name%", player.getName())
                     .replace("%afk_indicator%", afkIndicator);
    }

    private String getRankColor(String rank, FileConfiguration cfg) {
        ConfigurationSection rankColors = cfg.getConfigurationSection("tab.custom-layout.player-section.player-format.rank-colors");
        if (rankColors != null && rankColors.contains(rank)) return rankColors.getString(rank, "<white>");
        return rankColors != null ? rankColors.getString("default", "<white>") : "<white>";
    }

    private int getRankPriority(String rank, FileConfiguration cfg) {
        ConfigurationSection priorities = cfg.getConfigurationSection("tab.custom-layout.sorting.rank-priority");
        if (priorities != null && priorities.contains(rank)) return priorities.getInt(rank, 10);
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
        text = text.replace("%player_displayname%", player.getDisplayName())
                   .replace("%player_name%", player.getName())
                   .replace("%player_world%", player.getWorld().getName())
                   .replace("%player_ping%", String.valueOf(player.getPing()))
                   .replace("%oe_network_online%", String.valueOf(onlineCount));
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
            if (method == null) return text;
            Object result = method.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Throwable ignored) { return text; }
    }

    private void pruneCaches(List<Player> players) {
        Set<UUID> onlineIds = new HashSet<>(players.size());
        for (Player player : players) onlineIds.add(player.getUniqueId());
        headerCache.keySet().removeIf(id -> !onlineIds.contains(id));
        footerCache.keySet().removeIf(id -> !onlineIds.contains(id));
        playerListNameCache.keySet().removeIf(id -> !onlineIds.contains(id));
    }

    private String colorMaybeCached(String s) {
        if (!isShareableText(s)) return color(s);
        return sharedColorCache.computeIfAbsent(s, CustomTablistLayout::color);
    }

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
            } catch (Throwable ignored) {}
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static final Map<Character, String> AMP_TO_MINI = Map.ofEntries(
            Map.entry('l', "<bold>"), Map.entry('o', "<italic>"), Map.entry('n', "<underlined>"),
            Map.entry('m', "<strikethrough>"), Map.entry('k', "<obfuscated>"), Map.entry('r', "<reset>"),
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"));

    private static String convertAmpToMiniMessage(String s) {
        if (s == null || s.indexOf('&') == -1) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String mini = AMP_TO_MINI.get(code);
                if (mini != null) { out.append(mini); i++; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record PlayerTabEntry(Player player, int rankPriority, String displayName) {}
    private record SharedHeaderFooter(String headerText, String footerText) {}
}
