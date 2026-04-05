package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays chat messages as TextDisplay bubbles floating above the sender's head.
 *
 * Features:
 *  - Configurable appear / stay / disappear durations with opacity fading
 *  - Per-sender and per-viewer conditions
 *  - Max line count with word-wrap
 *  - MiniMessage formatting prefix/suffix
 *  - Channel blacklist / whitelist (uses message source channel tag if available)
 *  - Folia-compatible
 */
public final class ChatBubbleService implements Listener {

    // ── Config ────────────────────────────────────────────────────────────────
    private boolean enabled;
    private double yOffset;
    private double viewRangeSquared;
    private int stayTicks;
    private int appearTicks;
    private int disappearTicks;
    private int maxLines;
    private int lineWidth;
    private boolean shadow;
    private boolean seeThrough;
    private boolean defaultBackground;
    private Color backgroundColor;
    private String textPrefix;
    private String textSuffix;
    private List<NametageCondition> senderConditions;
    private List<NametageCondition> viewerConditions;

    // ── State ─────────────────────────────────────────────────────────────────
    /** owner UUID → active bubble entity UUID (only one bubble per player at a time) */
    private final ConcurrentHashMap<UUID, UUID> activeBubbles = new ConcurrentHashMap<>();

    private final OreoEssentials plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChatBubbleService(OreoEssentials plugin, FileConfiguration config) {
        this.plugin = plugin;
        loadConfig(config);

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[ChatBubble] Chat bubbles enabled.");
        } else {
            plugin.getLogger().info("[ChatBubble] Disabled in config.");
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private void loadConfig(FileConfiguration config) {
        ConfigurationSection s = config.getConfigurationSection("chat-bubbles");
        this.enabled = s != null && s.getBoolean("enabled", false);
        if (s == null) return;

        this.yOffset = s.getDouble("y-offset", 2.6);
        double viewRange = s.getDouble("view-range", 32.0);
        this.viewRangeSquared = viewRange * viewRange;
        this.stayTicks = s.getInt("stay-duration", 80);
        this.appearTicks = Math.max(1, s.getInt("appear-duration", 5));
        this.disappearTicks = Math.max(1, s.getInt("disappear-duration", 10));
        this.maxLines = s.getInt("max-lines", 3);
        this.lineWidth = s.getInt("line-width", 160);
        this.shadow = s.getBoolean("shadow", false);
        this.seeThrough = s.getBoolean("see-through", false);
        this.textPrefix = s.getString("text-prefix", "");
        this.textSuffix = s.getString("text-suffix", "");

        String bgStr = s.getString("background", "default").toLowerCase();
        this.defaultBackground = bgStr.equals("default");
        this.backgroundColor = null;
        if (bgStr.equals("transparent")) {
            this.backgroundColor = Color.fromARGB(0);
            this.defaultBackground = false;
        } else if (bgStr.startsWith("#") || bgStr.startsWith("0x")) {
            try {
                long argb = Long.parseLong(bgStr.replace("#", "").replace("0x", ""), 16);
                this.backgroundColor = Color.fromARGB((int) argb);
                this.defaultBackground = false;
            } catch (NumberFormatException ignored) {}
        }

        this.senderConditions = NametageCondition.parseList(s, "sender-conditions");
        this.viewerConditions = NametageCondition.parseList(s, "viewer-conditions");
    }

    // ── Event handler ─────────────────────────────────────────────────────────

    // ignoreCancelled = false because the OreoEssentials chat channel system cancels
    // every AsyncChatEvent at HIGHEST priority to reformat it — we still want bubbles.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;
        Player sender = event.getPlayer();

        // Skip GUI input (AH price, order qty, etc.) — those messages start with digits or
        // are cancelled without being displayed. Simple heuristic: skip command-like input.
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (rawMessage.startsWith("/")) return;

        if (!NametageCondition.evaluateAll(senderConditions, sender)) return;

        // Schedule on the player's entity thread (we're async here)
        OreScheduler.runForEntity(plugin, sender, () -> {
            if (!sender.isOnline()) return;
            spawnBubble(sender, rawMessage);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeBubble(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeBubble(event.getPlayer().getUniqueId());
    }

    // ── Bubble lifecycle ──────────────────────────────────────────────────────

    private void spawnBubble(Player sender, String rawMessage) {
        // Remove any existing bubble first
        removeBubble(sender.getUniqueId());

        // Truncate lines if needed
        String displayText = truncateToMaxLines(rawMessage);

        // Build the component
        String full = textPrefix + displayText + textSuffix;
        Component parsed;
        try { parsed = MM.deserialize(full); }
        catch (Exception e) { parsed = Component.text(full); }
        final Component text = parsed;

        Location spawnLoc = sender.getLocation().add(0, yOffset, 0);

        OreScheduler.runAtLocation(plugin, spawnLoc, () -> {
            if (!sender.isOnline()) return;

            TextDisplay display = (TextDisplay) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.TEXT_DISPLAY);
            configureBubbleEntity(display, text);

            // Hide from everyone initially; selectively show based on conditions
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.hideEntity(plugin, display);
            }

            activeBubbles.put(sender.getUniqueId(), display.getUniqueId());

            // Show to eligible viewers
            updateBubbleVisibility(sender, display);

            // Start appear animation (opacity 0 → 127)
            animateAppear(display, () -> {
                // After appear, schedule disappear after stay duration
                OreScheduler.runLater(plugin, () -> {
                    if (!display.isValid()) return;
                    animateDisappear(display, () -> {
                        display.remove();
                        activeBubbles.remove(sender.getUniqueId(), display.getUniqueId());
                    });
                }, stayTicks);
            });

            // Keep bubble positioned above sender while alive
            startPositionTracker(sender, display);
        });
    }

    private void configureBubbleEntity(TextDisplay display, Component text) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.addScoreboardTag("oe_bubble");

        display.text(text);
        display.setShadowed(shadow);
        display.setSeeThrough(seeThrough);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(lineWidth);
        display.setTextOpacity((byte) 0); // start transparent for fade-in

        if (defaultBackground) {
            display.setDefaultBackground(true);
        } else {
            display.setDefaultBackground(false);
            display.setBackgroundColor(backgroundColor != null ? backgroundColor : Color.fromARGB(0));
        }

        double viewRange = Math.sqrt(viewRangeSquared) / 64.0;
        display.setViewRange((float) Math.min(viewRange, 1.0));
    }

    private void updateBubbleVisibility(Player sender, TextDisplay display) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean shouldShow = canViewerSee(sender, viewer);
            if (shouldShow) {
                OreScheduler.runForEntity(plugin, viewer, () -> viewer.showEntity(plugin, display));
            } else {
                OreScheduler.runForEntity(plugin, viewer, () -> viewer.hideEntity(plugin, display));
            }
        }
    }

    private boolean canViewerSee(Player sender, Player viewer) {
        if (!sender.getWorld().equals(viewer.getWorld())) return false;
        if (sender.getLocation().distanceSquared(viewer.getLocation()) > viewRangeSquared) return false;
        return NametageCondition.evaluateAll(viewerConditions, viewer);
    }

    private void startPositionTracker(Player sender, TextDisplay display) {
        // Update position every 2 ticks while bubble is alive
        OreTask[] taskRef = {null};
        taskRef[0] = OreScheduler.runTimerForEntity(plugin, display, () -> {
            if (!display.isValid() || !sender.isOnline()) {
                if (taskRef[0] != null) taskRef[0].cancel();
                display.remove();
                return;
            }
            Location target = sender.getLocation().add(0, yOffset, 0);
            display.teleport(target);
        }, 2L, 2L);
    }

    // ── Animations ────────────────────────────────────────────────────────────

    /** Fades opacity from 0 to 127 over appearTicks ticks, then calls callback. */
    private void animateAppear(TextDisplay display, Runnable onDone) {
        int steps = Math.max(1, appearTicks);
        animateOpacity(display, 0, 127, steps, onDone);
    }

    /** Fades opacity from 127 to 0 over disappearTicks ticks, then calls callback. */
    private void animateDisappear(TextDisplay display, Runnable onDone) {
        int steps = Math.max(1, disappearTicks);
        animateOpacity(display, 127, 0, steps, onDone);
    }

    private void animateOpacity(TextDisplay display, int from, int to, int totalTicks, Runnable onDone) {
        if (!display.isValid()) { if (onDone != null) onDone.run(); return; }

        // Split into steps, one step per tick
        int[] step = {0};
        OreTask[] taskRef = {null};
        taskRef[0] = OreScheduler.runTimerForEntity(plugin, display, () -> {
            if (!display.isValid()) {
                if (taskRef[0] != null) taskRef[0].cancel();
                return;
            }
            step[0]++;
            int opacity = from + (int) ((to - from) * ((double) step[0] / totalTicks));
            opacity = Math.max(0, Math.min(127, opacity));
            display.setTextOpacity((byte) opacity);

            if (step[0] >= totalTicks) {
                display.setTextOpacity((byte) to);
                if (taskRef[0] != null) taskRef[0].cancel();
                if (onDone != null) OreScheduler.run(plugin, onDone);
            }
        }, 1L, 1L);
    }

    private void removeBubble(UUID ownerUuid) {
        UUID entityUuid = activeBubbles.remove(ownerUuid);
        if (entityUuid == null) return;
        org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
        if (entity != null) {
            OreScheduler.runForEntity(plugin, entity, entity::remove);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncateToMaxLines(String message) {
        // Rough word-wrap: split into words and build lines
        String[] words = message.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int charsPerLine = Math.max(10, lineWidth / 6); // rough pixel-to-char estimate

        for (String word : words) {
            if (current.length() + word.length() + 1 > charsPerLine && current.length() > 0) {
                lines.add(current.toString().trim());
                current = new StringBuilder();
                if (lines.size() >= maxLines) break;
            }
            current.append(word).append(" ");
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString().trim());
        }

        return String.join("\n", lines);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public void reload(FileConfiguration config) {
        // Pop all active bubbles then re-read config
        for (UUID ownerUuid : new HashSet<>(activeBubbles.keySet())) removeBubble(ownerUuid);
        activeBubbles.clear();
        loadConfig(config);
        plugin.getLogger().info("[ChatBubble] Reloaded.");
    }

    public void shutdown() {
        for (UUID ownerUuid : new HashSet<>(activeBubbles.keySet())) {
            removeBubble(ownerUuid);
        }
        activeBubbles.clear();
        plugin.getLogger().info("[ChatBubble] Shutdown complete.");
    }
}
