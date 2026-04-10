package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages per-player nametag TextDisplay entities.
 *
 * Each online player gets one or more TextDisplay entities floating above their head.
 * Visibility is controlled per-viewer using Paper's showEntity/hideEntity API.
 * Conditions on the owner and the viewer determine who sees what.
 *
 * Fully Folia-compatible via OreScheduler.
 */
public class PlayerNametagManager implements Listener {

    // ── Config ────────────────────────────────────────────────────────────────
    private final OreoEssentials plugin;
    private FileConfiguration config;

    private boolean enabled;
    private int updateIntervalTicks;   // text / condition refresh
    private int positionIntervalTicks; // how often entities teleport to follow the player
    private boolean showToSelf;
    private float entityViewRange;     // TextDisplay render distance in blocks
    private double viewRangeSquared;   // view range in blocks² to avoid sqrt

    private List<NametageLayerConfig> layers = new ArrayList<>();
    private static final String VANILLA_HIDE_TEAM = "oe_nt_hidden";

    // ── State ─────────────────────────────────────────────────────────────────
    /** owner UUID → list of TextDisplay entity UUIDs, one per layer */
    private final ConcurrentHashMap<UUID, List<UUID>> ownerToEntities = new ConcurrentHashMap<>();
    /** owner UUID → set of viewer UUIDs currently showing this nametag */
    private final ConcurrentHashMap<UUID, Set<UUID>> ownerToViewers = new ConcurrentHashMap<>();
    /** entity UUID → last text Component sent to the client (avoids redundant metadata packets) */
    private final ConcurrentHashMap<UUID, Component> lastTextCache = new ConcurrentHashMap<>();
    /** owner UUIDs that have a spawnNametag callback already queued — prevents duplicate spawns */
    private final Set<UUID> pendingSpawn = ConcurrentHashMap.newKeySet();

    private OreTask updateTask;
    private OreTask positionTask;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Legacy color translation ───────────────────────────────────────────────
    /** &#RRGGBB — BungeeCord/CMI hex format */
    private static final Pattern HEX_BUNGEECORD = Pattern.compile("&#([0-9A-Fa-f]{6})");
    /** &x&R&R&G&G&B&B — Spigot hex format */
    private static final Pattern HEX_SPIGOT = Pattern.compile(
            "[&§]x[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])[&§]([0-9A-Fa-f])");

    private static final Map<Character, String> LEGACY_COLOR_MAP;
    static {
        Map<Character, String> m = new HashMap<>();
        m.put('0', "black");        m.put('1', "dark_blue");
        m.put('2', "dark_green");   m.put('3', "dark_aqua");
        m.put('4', "dark_red");     m.put('5', "dark_purple");
        m.put('6', "gold");         m.put('7', "gray");
        m.put('8', "dark_gray");    m.put('9', "blue");
        m.put('a', "green");        m.put('b', "aqua");
        m.put('c', "red");          m.put('d', "light_purple");
        m.put('e', "yellow");       m.put('f', "white");
        m.put('k', "obfuscated");   m.put('l', "bold");
        m.put('m', "strikethrough");m.put('n', "underlined");
        m.put('o', "italic");       m.put('r', "reset");
        LEGACY_COLOR_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Translates legacy Minecraft color codes to MiniMessage tags so that
     * PlaceholderAPI results containing &X / &#RRGGBB / §X color codes render
     * correctly inside a MiniMessage template.
     */
    private static String translateLegacy(String input) {
        if (input == null) return "";
        if (!input.contains("&") && !input.contains("§")) return input;

        // &x&R&R&G&G&B&B → <#RRGGBB>
        Matcher spigot = HEX_SPIGOT.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (spigot.find()) {
            spigot.appendReplacement(sb, "<#" + spigot.group(1) + spigot.group(2)
                    + spigot.group(3) + spigot.group(4) + spigot.group(5) + spigot.group(6) + ">");
        }
        spigot.appendTail(sb);
        input = sb.toString();

        // &#RRGGBB → <#RRGGBB>
        input = HEX_BUNGEECORD.matcher(input).replaceAll(mr -> "<#" + mr.group(1) + ">");

        // &X / §X → <miniMessage tag>
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(i + 1));
                if (next == '&' || next == '§') { out.append(c); i++; continue; } // &&/§§ → literal
                String tag = LEGACY_COLOR_MAP.get(next);
                if (tag != null) { out.append('<').append(tag).append('>'); i++; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Injected after construction — may be null if toggle feature is not set up yet. */
    private NametageToggleStore toggleStore;

    public void setToggleStore(NametageToggleStore store) { this.toggleStore = store; }

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlayerNametagManager(OreoEssentials plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;

        loadConfig();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startTasks();

            // Show nametags for players already online on reload
            OreScheduler.runLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) spawnNametag(p);
                refreshVanillaNameHiding();
            }, 20L);

            plugin.getLogger().info("[Nametag] TextDisplay nametags enabled (" + layers.size() + " layer(s)).");
        } else {
            plugin.getLogger().info("[Nametag] Disabled in config.");
        }
    }

    // ── Config loading ────────────────────────────────────────────────────────

    private void loadConfig() {
        this.enabled = config.getBoolean("nametag.enabled", true);
        this.updateIntervalTicks = Math.max(1, config.getInt("nametag.update-interval-ticks", 40));
        this.positionIntervalTicks = Math.max(1, config.getInt("nametag.position-interval-ticks", 2));
        this.showToSelf = config.getBoolean("nametag.show-to-self", false);
        double viewRange = config.getDouble("nametag.view-range", 48.0);
        this.entityViewRange = (float) Math.max(1.0, viewRange);
        this.viewRangeSquared = viewRange * viewRange;

        layers.clear();
        ConfigurationSection layersSec = config.getConfigurationSection("nametag.layers");
        if (layersSec != null) {
            for (String key : layersSec.getKeys(false)) {
                ConfigurationSection sec = layersSec.getConfigurationSection(key);
                if (sec != null) {
                    try { layers.add(NametageLayerConfig.fromSection(sec)); }
                    catch (Exception e) {
                        plugin.getLogger().warning("[Nametag] Failed to parse layer '" + key + "': " + e.getMessage());
                    }
                }
            }
        }

        // Backward compat: if no layers section, create a default one from old prefix/suffix fields
        if (layers.isEmpty()) {
            String prefix = config.getString("nametag.team-prefix", "<white>%player_name%</white>");
            String suffix = config.getString("nametag.team-suffix", "");
            layers.add(new NametageLayerConfig(
                    prefix + suffix, 2.1, false, false, false,
                    org.bukkit.Color.fromARGB(0),
                    TextDisplay.TextAlignment.CENTER, 200,
                    Collections.emptyList(), Collections.emptyList()
            ));
        }
    }

    // ── Task management ───────────────────────────────────────────────────────

    private void startTasks() {
        stopTasks();

        // Text + visibility update (every N ticks)
        updateTask = OreScheduler.runTimer(plugin, this::updateAllNametags,
                updateIntervalTicks, updateIntervalTicks);

        // Position follow-player update (every M ticks)
        positionTask = OreScheduler.runTimer(plugin, this::updateAllPositions,
                positionIntervalTicks, positionIntervalTicks);
    }

    private void stopTasks() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        if (positionTask != null) { positionTask.cancel(); positionTask = null; }
    }

    // ── Spawn / remove nametag ────────────────────────────────────────────────

    private void spawnNametag(Player owner) {
        if (!enabled || owner == null || !owner.isOnline()) return;

        // If a spawn callback is already queued for this player, skip — the queued
        // callback will run shortly and spawning twice creates orphaned entities.
        if (!pendingSpawn.add(owner.getUniqueId())) return;

        // Remove any existing nametag first
        removeNametag(owner.getUniqueId());

        // All layers share the same X/Z as the player → same region chunk.
        // Spawn everything in one callback to avoid the multi-callback race.
        Location base = owner.getLocation();
        OreScheduler.runAtLocation(plugin, base, () -> {
            pendingSpawn.remove(owner.getUniqueId());

            if (!owner.isOnline()) return;

            List<UUID> entityIds = new ArrayList<>();

            for (int li = 0; li < layers.size(); li++) {
                NametageLayerConfig layer = layers.get(li);
                Location spawnLoc = owner.getLocation().add(0, layer.yOffset, 0);
                TextDisplay display = (TextDisplay) spawnLoc.getWorld()
                        .spawnEntity(spawnLoc, EntityType.TEXT_DISPLAY);
                configureEntity(display, layer, owner);
                entityIds.add(display.getUniqueId());
            }

            ownerToEntities.put(owner.getUniqueId(),
                    Collections.unmodifiableList(entityIds));
            Set<UUID> currentViewers = ConcurrentHashMap.newKeySet();
            ownerToViewers.put(owner.getUniqueId(), currentViewers);

            // Immediately set correct visibility for every online player.
            // The entity is visible by default after spawn — we only need to
            // hide it from players who should NOT see it.
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                boolean eligible = shouldViewerSee(owner, viewer);
                if (eligible) {
                    currentViewers.add(viewer.getUniqueId());
                    // Entity is already visible — no showEntity call needed.
                } else {
                    for (UUID id : entityIds) {
                        org.bukkit.entity.Entity e = Bukkit.getEntity(id);
                        if (e != null) viewer.hideEntity(plugin, e);
                    }
                }
            }
        });
    }

    private void configureEntity(TextDisplay display, NametageLayerConfig layer, Player owner) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBillboard(Display.Billboard.CENTER);
        display.addScoreboardTag("oe_nametag");
        display.addScoreboardTag("oe_nametag:" + owner.getName().toLowerCase());

        // Smooth client-side interpolation when the entity teleports to follow the player.
        // teleportDuration = how many ticks the client spends interpolating each position update.
        // +1 buffer so the client never "runs out" of interpolation if a tick arrives slightly late.
        display.setTeleportDuration(Math.max(1, positionIntervalTicks + 1));

        // Text
        Component initialText = renderText(layer.text, owner, null);
        display.text(initialText);
        lastTextCache.put(display.getUniqueId(), initialText);

        // Style
        display.setShadowed(layer.shadow);
        display.setSeeThrough(layer.seeThrough);
        display.setAlignment(layer.alignment);
        display.setLineWidth(layer.lineWidth);

        // Background
        if (layer.defaultBackground) {
            display.setDefaultBackground(true);
        } else {
            display.setDefaultBackground(false);
            display.setBackgroundColor(layer.backgroundColor != null ? layer.backgroundColor : org.bukkit.Color.fromARGB(0));
        }

        // View range — TextDisplay has its own render distance
        display.setViewRange(entityViewRange);
    }

    private void removeNametag(UUID ownerUuid) {
        pendingSpawn.remove(ownerUuid); // allow a fresh spawn after explicit remove
        List<UUID> entityUuids = ownerToEntities.remove(ownerUuid);
        ownerToViewers.remove(ownerUuid);

        if (entityUuids == null) return;

        for (UUID entityUuid : entityUuids) {
            lastTextCache.remove(entityUuid);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
            if (entity != null) {
                OreScheduler.runForEntity(plugin, entity, entity::remove);
            }
        }
    }

    // ── Text rendering ────────────────────────────────────────────────────────

    private Component renderText(String template, Player owner, Player viewer) {
        String text = template;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(owner, text);
                // Relational placeholders if viewer is available
                if (viewer != null) {
                    text = me.clip.placeholderapi.PlaceholderAPI.setRelationalPlaceholders(viewer, owner, text);
                }
            }
        } catch (Throwable ignored) {}

        // Built-in fallbacks
        text = text.replace("{player}", owner.getName())
                   .replace("%player_name%", owner.getName())
                   .replace("%player_displayname%", owner.getDisplayName());

        // Translate legacy &X / &#RRGGBB / §X codes (e.g. from LuckPerms prefixes)
        // to MiniMessage tags so they render correctly.
        text = translateLegacy(text);

        try { return MM.deserialize(text); }
        catch (Exception e) { return Component.text(text); }
    }

    // ── Update loops ──────────────────────────────────────────────────────────

    /** Refreshes text content and visibility for all nametags. */
    private void updateAllNametags() {
        refreshVanillaNameHiding();
        for (Player owner : Bukkit.getOnlinePlayers()) {
            if (!ownerToEntities.containsKey(owner.getUniqueId())) continue;
            updateTextFor(owner);
            updateViewersFor(owner);
        }
    }

    /** Updates text content for one player's nametag. Only sends a packet when text actually changed. */
    private void updateTextFor(Player owner) {
        List<UUID> entityUuids = ownerToEntities.get(owner.getUniqueId());
        if (entityUuids == null) return;

        for (int i = 0; i < entityUuids.size() && i < layers.size(); i++) {
            NametageLayerConfig layer = layers.get(i);
            UUID entityUuid = entityUuids.get(i);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
            if (entity == null) continue;
            if (!(entity instanceof TextDisplay display)) continue;

            Component text = renderText(layer.text, owner, null);

            // Skip the entity metadata packet if the rendered text hasn't changed —
            // sending it even with identical content causes a brief client-side flicker.
            Component prev = lastTextCache.put(entityUuid, text);
            if (text.equals(prev)) continue;

            OreScheduler.runForEntity(plugin, display, () -> display.text(text));
        }
    }

    /**
     * Evaluates conditions and calls showEntity/hideEntity for each viewer
     * relative to this owner's nametag.
     */
    private void updateViewersFor(Player owner) {
        List<UUID> entityUuids = ownerToEntities.get(owner.getUniqueId());
        if (entityUuids == null) return;

        Set<UUID> currentViewers = ownerToViewers.computeIfAbsent(owner.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean shouldShow = shouldViewerSee(owner, viewer);

            if (shouldShow && !currentViewers.contains(viewer.getUniqueId())) {
                showNametag(entityUuids, viewer);
                currentViewers.add(viewer.getUniqueId());
            } else if (!shouldShow && currentViewers.contains(viewer.getUniqueId())) {
                hideNametag(entityUuids, viewer);
                currentViewers.remove(viewer.getUniqueId());
            }
        }

        // Clean up viewers who left
        currentViewers.removeIf(viewerUuid -> Bukkit.getPlayer(viewerUuid) == null);
    }

    private boolean shouldViewerSee(Player owner, Player viewer) {
        if (!owner.isOnline()) return false;
        if (!showToSelf && viewer.getUniqueId().equals(owner.getUniqueId())) return false;
        // Respect the owner's own toggle
        if (toggleStore != null && toggleStore.isToggledOff(owner.getUniqueId())) return false;

        // Must be in the same world
        if (!owner.getWorld().equals(viewer.getWorld())) return false;

        // Range check
        if (owner.getLocation().distanceSquared(viewer.getLocation()) > viewRangeSquared) return false;

        // Check owner conditions across all layers — at least one visible layer must pass
        boolean anyLayerVisible = false;
        for (NametageLayerConfig layer : layers) {
            if (NametageCondition.evaluateAll(layer.ownerConditions, owner)
                    && NametageCondition.evaluateAll(layer.viewerConditions, viewer)) {
                anyLayerVisible = true;
                break;
            }
        }
        return anyLayerVisible;
    }

    private void showNametag(List<UUID> entityUuids, Player viewer) {
        for (UUID entityUuid : entityUuids) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
            if (entity != null) {
                OreScheduler.runForEntity(plugin, viewer, () -> viewer.showEntity(plugin, entity));
            }
        }
    }

    private void hideNametag(List<UUID> entityUuids, Player viewer) {
        for (UUID entityUuid : entityUuids) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
            if (entity != null) {
                OreScheduler.runForEntity(plugin, viewer, () -> viewer.hideEntity(plugin, entity));
            }
        }
    }

    /**
     * Hide the vanilla player name on the viewer scoreboard so the custom TextDisplay
     * is the only label rendered. This is a best-effort no-op on scoreboards that
     * do not support team mutations.
     */
    private void refreshVanillaNameHiding() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyVanillaNameHiding(viewer);
        }
    }

    private void applyVanillaNameHiding(Player viewer) {
        Scoreboard board = viewer.getScoreboard();
        if (board == null) return;

        final Team team;
        try {
            Team existing = board.getTeam(VANILLA_HIDE_TEAM);
            team = existing != null ? existing : board.registerNewTeam(VANILLA_HIDE_TEAM);
        } catch (Throwable ignored) {
            return;
        }

        try {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        } catch (Throwable ignored) {
            return;
        }

        Set<String> onlineNames = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            onlineNames.add(online.getName());
            if (!team.hasEntry(online.getName())) {
                team.addEntry(online.getName());
            }
        }

        for (String entry : new HashSet<>(team.getEntries())) {
            if (!onlineNames.contains(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private void restoreVanillaNames() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            if (board == null) continue;

            try {
                Team team = board.getTeam(VANILLA_HIDE_TEAM);
                if (team != null) team.unregister();
            } catch (Throwable ignored) {}
        }
    }

    /** Teleports all nametag entities to follow their owners. */
    private void updateAllPositions() {
        for (Player owner : Bukkit.getOnlinePlayers()) {
            List<UUID> entityUuids = ownerToEntities.get(owner.getUniqueId());
            if (entityUuids == null) continue;

            for (int i = 0; i < entityUuids.size() && i < layers.size(); i++) {
                UUID entityUuid = entityUuids.get(i);
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
                if (entity == null) continue;

                double yOff = layers.get(i).yOffset;
                Location target = owner.getLocation().add(0, yOff, 0);

                if (OreScheduler.isFolia()) {
                    entity.teleportAsync(target);
                } else {
                    OreScheduler.runForEntity(plugin, entity, () -> entity.teleport(target));
                }
            }
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player joining = event.getPlayer();

        OreScheduler.runLater(plugin, () -> {
            if (!joining.isOnline()) return;

            // Spawn this player's own nametag
            spawnNametag(joining);

            // Make the joining player potentially see all existing nametags
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(joining)) continue;
                List<UUID> entityUuids = ownerToEntities.get(other.getUniqueId());
                if (entityUuids == null) continue;

                if (shouldViewerSee(other, joining)) {
                    showNametag(entityUuids, joining);
                    ownerToViewers.computeIfAbsent(other.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                                  .add(joining.getUniqueId());
                }
            }

            refreshVanillaNameHiding();
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        UUID uuid = event.getPlayer().getUniqueId();

        // Remove this player's nametag entities
        removeNametag(uuid);

        // Remove them from all other owners' viewer sets
        for (Set<UUID> viewers : ownerToViewers.values()) {
            viewers.remove(uuid);
        }

        refreshVanillaNameHiding();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();

        // Despawn and re-spawn in the new world
        OreScheduler.runLater(plugin, () -> {
            if (player.isOnline()) spawnNametag(player);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (event.getTo() == null) return;

        // If teleporting between worlds, world change event handles it
        // For same-world long-distance teleports, just re-evaluate visibility
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) return;

        OreScheduler.runLater(plugin, () -> {
            if (player.isOnline()) updateViewersFor(player);
        }, 5L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void updateNametag(Player player) {
        if (player == null || !player.isOnline()) return;
        updateTextFor(player);
        updateViewersFor(player);
    }

    public void forceUpdate(Player player) {
        if (player != null && player.isOnline()) {
            removeNametag(player.getUniqueId());
            spawnNametag(player);
        }
    }

    public void forceUpdateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) forceUpdate(p);
    }

    public void disableNametag(Player player) {
        if (player != null) removeNametag(player.getUniqueId());
    }

    public boolean isEnabled() { return enabled; }

    public void reload(FileConfiguration newConfig) {
        plugin.getLogger().info("[Nametag] Reloading...");
        stopTasks();

        // Despawn all existing entities
        for (UUID uuid : new HashSet<>(ownerToEntities.keySet())) removeNametag(uuid);
        ownerToEntities.clear();
        ownerToViewers.clear();

        this.config = newConfig;
        loadConfig();

        if (enabled) {
            startTasks();
            OreScheduler.runLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) spawnNametag(p);
                refreshVanillaNameHiding();
            }, 10L);
            plugin.getLogger().info("[Nametag] Reload complete (" + layers.size() + " layer(s)).");
        } else {
            restoreVanillaNames();
            plugin.getLogger().info("[Nametag] Disabled after reload.");
        }
    }

    public void shutdown() {
        plugin.getLogger().info("[Nametag] Shutting down...");
        stopTasks();

        for (UUID uuid : new HashSet<>(ownerToEntities.keySet())) removeNametag(uuid);
        ownerToEntities.clear();
        ownerToViewers.clear();
        restoreVanillaNames();

        plugin.getLogger().info("[Nametag] Shutdown complete.");
    }
}
