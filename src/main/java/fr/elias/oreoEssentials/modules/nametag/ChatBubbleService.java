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
import org.bukkit.util.Transformation;

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
 *  - Per-player custom color command (/bubblecolor)
 *  - Optional background-icon overlay (ItemsAdder/Nexo glyph as separate TextDisplay)
 *  - Folia-compatible
 */
public final class ChatBubbleService implements Listener {

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

    private boolean bgIconEnabled;
    private String bgIconText;
    private float bgIconScale;
    private double bgIconOffsetX;
    private double bgIconOffsetY;

    private final ConcurrentHashMap<UUID, UUID> activeBubbles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> activeBgIcons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> playerBubbleColors = new ConcurrentHashMap<>();

    private final OreoEssentials plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

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

        ConfigurationSection bgSec = s.getConfigurationSection("background-icon");
        if (bgSec != null) {
            this.bgIconEnabled = bgSec.getBoolean("enabled", false);
            this.bgIconText = bgSec.getString("text", "");
            this.bgIconScale = (float) bgSec.getDouble("scale", 1.0);
            this.bgIconOffsetX = bgSec.getDouble("offset-x", 0.0);
            this.bgIconOffsetY = bgSec.getDouble("offset-y", 0.0);
        } else {
            this.bgIconEnabled = false;
            this.bgIconText = "";
            this.bgIconScale = 1.0f;
            this.bgIconOffsetX = 0.0;
            this.bgIconOffsetY = 0.0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;
        Player sender = event.getPlayer();

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (rawMessage.startsWith("/")) return;

        OreScheduler.runForEntity(plugin, sender, () -> {
            if (!sender.isOnline()) return;
            if (!NametageCondition.evaluateAll(senderConditions, sender)) return;
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

    private void spawnBubble(Player sender, String rawMessage) {
        removeBubble(sender.getUniqueId());

        String displayText = truncateToMaxLines(rawMessage);
        String playerColor = playerBubbleColors.get(sender.getUniqueId());
        String coloredMsg = (playerColor != null && !playerColor.isEmpty())
                ? (playerColor + displayText + "<reset>")
                : displayText;
        String full = textPrefix + coloredMsg + textSuffix;

        Component parsed;
        try { parsed = MM.deserialize(full); }
        catch (Exception e) { parsed = Component.text(full); }
        final Component text = parsed;

        Location spawnLoc = sender.getLocation().add(0, yOffset, 0);

        OreScheduler.runAtLocation(plugin, spawnLoc, () -> {
            if (!sender.isOnline()) return;

            if (bgIconEnabled && !bgIconText.isEmpty()) {
                Location bgLoc = spawnLoc.clone().add(bgIconOffsetX, bgIconOffsetY, 0);
                TextDisplay bgDisplay = (TextDisplay) bgLoc.getWorld().spawnEntity(bgLoc, EntityType.TEXT_DISPLAY);
                configureBgIcon(bgDisplay);

                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    OreScheduler.runForEntity(plugin, viewer, () -> viewer.hideEntity(plugin, bgDisplay));
                }
                activeBgIcons.put(sender.getUniqueId(), bgDisplay.getUniqueId());
                updateEntityVisibility(sender, bgDisplay);
                startPositionTracker(sender, bgDisplay, bgIconOffsetX, yOffset + bgIconOffsetY);
                animateAppear(bgDisplay, null);
            }

            TextDisplay display = (TextDisplay) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.TEXT_DISPLAY);
            configureBubbleEntity(display, text);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                OreScheduler.runForEntity(plugin, viewer, () -> viewer.hideEntity(plugin, display));
            }
            activeBubbles.put(sender.getUniqueId(), display.getUniqueId());
            updateEntityVisibility(sender, display);

            animateAppear(display, () -> OreScheduler.runLaterForEntity(plugin, display, () -> {
                if (display.isValid()) {
                    animateDisappear(display, () -> {
                        if (display.isValid()) display.remove();
                        activeBubbles.remove(sender.getUniqueId(), display.getUniqueId());
                    });
                }

                UUID bgUuid = activeBgIcons.get(sender.getUniqueId());
                if (bgUuid != null) {
                    org.bukkit.entity.Entity bgEnt = Bukkit.getEntity(bgUuid);
                    if (bgEnt instanceof TextDisplay bgDisp) {
                        OreScheduler.runForEntity(plugin, bgDisp, () -> {
                            if (!bgDisp.isValid()) return;
                            animateDisappear(bgDisp, () -> {
                                if (bgDisp.isValid()) bgDisp.remove();
                                activeBgIcons.remove(sender.getUniqueId(), bgUuid);
                            });
                        });
                    }
                }
            }, stayTicks));

            startPositionTracker(sender, display, 0, yOffset);
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
        display.setTextOpacity((byte) 0);

        if (defaultBackground) {
            display.setDefaultBackground(true);
        } else {
            display.setDefaultBackground(false);
            display.setBackgroundColor(backgroundColor != null ? backgroundColor : Color.fromARGB(0));
        }

        double viewRange = Math.sqrt(viewRangeSquared) / 64.0;
        display.setViewRange((float) Math.min(viewRange, 1.0));
    }

    private void configureBgIcon(TextDisplay display) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.addScoreboardTag("oe_bubble_bg");

        Component bg;
        try { bg = MM.deserialize(bgIconText); }
        catch (Exception e) { bg = Component.text(bgIconText); }
        display.text(bg);

        display.setShadowed(false);
        display.setSeeThrough(false);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0));
        display.setTextOpacity((byte) 0);

        if (bgIconScale != 1.0f) {
            Transformation t = display.getTransformation();
            t.getScale().set(bgIconScale, bgIconScale, bgIconScale);
            display.setTransformation(t);
        }

        double viewRange = Math.sqrt(viewRangeSquared) / 64.0;
        display.setViewRange((float) Math.min(viewRange, 1.0));
    }

    private void updateEntityVisibility(Player sender, TextDisplay display) {
        OreScheduler.runForEntity(plugin, sender, () -> {
            if (!sender.isOnline()) return;
            final Location senderLoc = sender.getLocation().clone();
            final UUID senderWorld = senderLoc.getWorld() != null ? senderLoc.getWorld().getUID() : null;

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                OreScheduler.runForEntity(plugin, viewer, () -> {
                    if (!viewer.isOnline() || senderWorld == null || !viewer.getWorld().getUID().equals(senderWorld)) {
                        viewer.hideEntity(plugin, display);
                        return;
                    }
                    boolean inRange = viewer.getLocation().distanceSquared(senderLoc) <= viewRangeSquared;
                    boolean allowed = inRange && NametageCondition.evaluateAll(viewerConditions, viewer);
                    if (allowed) viewer.showEntity(plugin, display);
                    else viewer.hideEntity(plugin, display);
                });
            }
        });
    }

    private void startPositionTracker(Player sender, TextDisplay display, double xOffset, double yOffsetTotal) {
        OreTask[] taskRef = {null};
        taskRef[0] = OreScheduler.runTimerForEntity(plugin, display, () -> {
            if (!display.isValid()) {
                if (taskRef[0] != null) taskRef[0].cancel();
                return;
            }
            if (!sender.isOnline()) {
                if (taskRef[0] != null) taskRef[0].cancel();
                display.remove();
                return;
            }

            OreScheduler.runForEntity(plugin, sender, () -> {
                if (!sender.isOnline() || !display.isValid()) return;
                Location target = sender.getLocation().clone().add(xOffset, yOffsetTotal, 0);
                try {
                    if (OreScheduler.isFolia()) display.teleportAsync(target);
                    else display.teleport(target);
                } catch (Throwable t) {
                    if (taskRef[0] != null) taskRef[0].cancel();
                    plugin.getLogger().fine("[ChatBubble] Position tracker stopped: " + t.getMessage());
                }
            });
        }, 2L, 2L);
    }

    private void animateAppear(TextDisplay display, Runnable onDone) {
        animateOpacity(display, 0, 127, Math.max(1, appearTicks), onDone);
    }

    private void animateDisappear(TextDisplay display, Runnable onDone) {
        animateOpacity(display, 127, 0, Math.max(1, disappearTicks), onDone);
    }

    private void animateOpacity(TextDisplay display, int from, int to, int totalTicks, Runnable onDone) {
        if (!display.isValid()) { if (onDone != null) onDone.run(); return; }

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
                if (onDone != null) OreScheduler.runForEntity(plugin, display, onDone);
            }
        }, 1L, 1L);
    }

    private void removeBubble(UUID ownerUuid) {
        UUID entityUuid = activeBubbles.remove(ownerUuid);
        if (entityUuid != null) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
            if (entity != null) OreScheduler.runForEntity(plugin, entity, entity::remove);
        }
        UUID bgUuid = activeBgIcons.remove(ownerUuid);
        if (bgUuid != null) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(bgUuid);
            if (entity != null) OreScheduler.runForEntity(plugin, entity, entity::remove);
        }
    }

    private String truncateToMaxLines(String message) {
        String[] words = message.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int charsPerLine = Math.max(10, lineWidth / 6);

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

    public boolean isEnabled() { return enabled; }

    public void setPlayerColor(UUID uuid, String colorTag) {
        playerBubbleColors.put(uuid, colorTag);
    }

    public void clearPlayerColor(UUID uuid) {
        playerBubbleColors.remove(uuid);
    }

    public String getPlayerColor(UUID uuid) {
        return playerBubbleColors.get(uuid);
    }

    public void reload(FileConfiguration config) {
        for (UUID ownerUuid : new HashSet<>(activeBubbles.keySet())) removeBubble(ownerUuid);
        activeBubbles.clear();
        activeBgIcons.clear();
        loadConfig(config);
        plugin.getLogger().info("[ChatBubble] Reloaded.");
    }

    public void shutdown() {
        for (UUID ownerUuid : new HashSet<>(activeBubbles.keySet())) removeBubble(ownerUuid);
        activeBubbles.clear();
        activeBgIcons.clear();
        plugin.getLogger().info("[ChatBubble] Shutdown complete.");
    }
}
