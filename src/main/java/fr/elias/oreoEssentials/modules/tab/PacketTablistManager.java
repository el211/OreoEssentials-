package fr.elias.oreoEssentials.modules.tab;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a fully packet-based custom tab list via PacketEvents.
 *
 * Fake entries (decorators, separators, player rows) are identified by stable
 * deterministic UUIDs so incremental UPDATE_DISPLAY_NAME packets can be sent
 * instead of remove+re-add (which would cause visible flicker).
 *
 * Layout:
 *   Slots with profile-name prefix "\u0001" → top decoration section (sorted first)
 *   Slots with profile-name prefix "\u0002" → player section (sorted middle)
 *   Slots with profile-name prefix "\u0003" → bottom decoration section (sorted last)
 */
public class PacketTablistManager {

    // ── UUID namespaces for fake entries (section-specific high bits) ─────────
    private static final long TOP_HIGH    = 0x4F72656F54616201L; // "OreoTab\1"
    private static final long PLAYER_HIGH = 0x4F72656F54616202L; // "OreoTab\2"
    private static final long BOTTOM_HIGH = 0x4F72656F54616203L; // "OreoTab\3"
    /** Multi-column layout: column 1 (leftmost). */
    private static final long COL1_HIGH   = 0x4F72656F54616204L; // "OreoTab\4"
    /** Multi-column layout: column 2. */
    private static final long COL2_HIGH   = 0x4F72656F54616205L; // "OreoTab\5"
    /** Multi-column layout: column 3 (rightmost in 3-col mode). */
    private static final long COL3_HIGH   = 0x4F72656F54616206L; // "OreoTab\6"

    private static final long[] COL_HIGHS = {COL1_HIGH, COL2_HIGH, COL3_HIGH};

    /** Stable UUID for a top/bottom fake row at index {@code i}. */
    public static UUID topSlotUuid(int i)    { return new UUID(TOP_HIGH,    i); }
    public static UUID bottomSlotUuid(int i) { return new UUID(BOTTOM_HIGH, i); }

    /** Stable UUID for slot {@code i} in column {@code col} (0-based). Max col = 2. */
    public static UUID colSlotUuid(int col, int i) { return new UUID(COL_HIGHS[col], i); }

    // Convenience aliases kept for compatibility
    public static UUID col1SlotUuid(int i) { return colSlotUuid(0, i); }
    public static UUID col2SlotUuid(int i) { return colSlotUuid(1, i); }

    /**
     * Stable UUID for a real player's fake tab representation.
     * XOR prevents collision with the original player UUID while remaining deterministic.
     */
    public static UUID playerFakeUuid(UUID realUuid) {
        return new UUID(PLAYER_HIGH, realUuid.getLeastSignificantBits());
    }

    /** No-skin constant (shows default Steve/Alex skin). */
    public static final List<TextureProperty> NO_SKIN = Collections.emptyList();

    // ── Per-viewer state ──────────────────────────────────────────────────────

    /**
     * viewer UUID → (slotUUID → last text string sent).
     * Comparing strings is cheaper than Component.equals across many slots.
     */
    private final Map<UUID, Map<UUID, String>> viewerTextCache = new ConcurrentHashMap<>();

    /** Viewers for whom a full ADD_PLAYER init has been sent. */
    private final Set<UUID> initializedViewers = ConcurrentHashMap.newKeySet();

    /** real player UUID → texture value from last render (detects skin changes). */
    private final Map<UUID, String> skinVersionCache = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a full ADD_PLAYER init for a viewer.
     * Call once after a viewer joins (or to force re-init).
     */
    public void initViewer(Player viewer, List<TabSlot> slots) {
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> infos = new ArrayList<>(slots.size());
        Map<UUID, String> textCache = new HashMap<>(slots.size());

        for (TabSlot slot : slots) {
            UserProfile profile = buildProfile(slot);
            infos.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile, true, slot.latency(), GameMode.SURVIVAL, slot.displayName(), null
            ));
            textCache.put(slot.uuid(), miniText(slot.displayName()));
        }

        if (!infos.isEmpty()) {
            try {
                WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                        EnumSet.of(
                                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY
                        ), infos
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            } catch (Exception ignored) {}
        }

        viewerTextCache.put(viewer.getUniqueId(), textCache);
        initializedViewers.add(viewer.getUniqueId());
    }

    /**
     * Incremental update — only sends packets for slots whose text or skin changed.
     * Falls back to {@link #initViewer} if this viewer hasn't been initialised yet.
     */
    public void updateViewer(Player viewer, List<TabSlot> slots) {
        if (!initializedViewers.contains(viewer.getUniqueId())) {
            initViewer(viewer, slots);
            return;
        }

        UUID vid = viewer.getUniqueId();
        Map<UUID, String> cache = viewerTextCache.computeIfAbsent(vid, k -> new ConcurrentHashMap<>());

        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> toUpdateText  = new ArrayList<>();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> toReinit = new ArrayList<>(); // skin changed
        Set<UUID> currentSlotUuids = new HashSet<>(slots.size());

        for (TabSlot slot : slots) {
            currentSlotUuids.add(slot.uuid());
            String text = miniText(slot.displayName());
            String cached = cache.get(slot.uuid());

            if (cached == null) {
                // New slot that wasn't in the last render — full ADD_PLAYER
                toReinit.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        buildProfile(slot), true, slot.latency(), GameMode.SURVIVAL, slot.displayName(), null
                ));
                cache.put(slot.uuid(), text);
            } else if (slot.skinChanged()) {
                // Skin changed — must re-send ADD_PLAYER to push new texture
                toReinit.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        buildProfile(slot), true, slot.latency(), GameMode.SURVIVAL, slot.displayName(), null
                ));
                cache.put(slot.uuid(), text);
            } else if (!text.equals(cached)) {
                toUpdateText.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        buildProfile(slot), true, slot.latency(), GameMode.SURVIVAL, slot.displayName(), null
                ));
                cache.put(slot.uuid(), text);
            }
        }

        // Remove slots that disappeared from the layout
        Set<UUID> gone = new HashSet<>(cache.keySet());
        gone.removeAll(currentSlotUuids);
        if (!gone.isEmpty()) {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(
                        viewer, new WrapperPlayServerPlayerInfoRemove(new ArrayList<>(gone)));
            } catch (Exception ignored) {}
            gone.forEach(cache::remove);
        }

        if (!toReinit.isEmpty()) {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                        new WrapperPlayServerPlayerInfoUpdate(
                                EnumSet.of(
                                        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY
                                ), toReinit));
            } catch (Exception ignored) {}
        }

        if (!toUpdateText.isEmpty()) {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                        new WrapperPlayServerPlayerInfoUpdate(
                                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
                                toUpdateText));
            } catch (Exception ignored) {}
        }
    }

    /**
     * Hide all real players from their vanilla tab positions for this viewer.
     * Must be called before (or alongside) initViewer so real entries don't
     * bleed through alongside fake ones.
     */
    public void delistRealPlayers(Player viewer) {
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> infos = new ArrayList<>();
        for (Player real : Bukkit.getOnlinePlayers()) {
            if (!real.isOnline()) continue;
            UserProfile profile = getRealProfile(real);
            infos.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile, false, 0, GameMode.SURVIVAL, null, null
            ));
        }
        if (!infos.isEmpty()) {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                        new WrapperPlayServerPlayerInfoUpdate(
                                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED), infos));
            } catch (Exception ignored) {}
        }
    }

    /**
     * De-list a specific real player from all other viewers' vanilla tab.
     * Call when a new player joins so they don't appear in other viewers' vanilla list.
     */
    public void delistPlayerForAll(Player newPlayer) {
        UserProfile profile = getRealProfile(newPlayer);
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> infos = List.of(
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile, false, 0, GameMode.SURVIVAL, null, null)
        );
        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED), infos
        );
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(newPlayer)) continue;
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Remove the fake entry for a player who quit from all other viewers.
     * Also invalidates their skin cache entry.
     */
    public void removePlayerFakeEntry(Player quitter) {
        UUID fakeUuid = playerFakeUuid(quitter.getUniqueId());
        skinVersionCache.remove(quitter.getUniqueId());

        WrapperPlayServerPlayerInfoRemove removePacket =
                new WrapperPlayServerPlayerInfoRemove(List.of(fakeUuid));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(quitter)) continue;
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
            } catch (Exception ignored) {}
            Map<UUID, String> cache = viewerTextCache.get(viewer.getUniqueId());
            if (cache != null) cache.remove(fakeUuid);
        }
    }

    /** Clean up all state for a viewer who disconnected. */
    public void cleanupViewer(UUID viewerUuid) {
        viewerTextCache.remove(viewerUuid);
        initializedViewers.remove(viewerUuid);
    }

    /** Clean up all state (called on plugin disable / tab stop). */
    public void cleanupAll() {
        viewerTextCache.clear();
        initializedViewers.clear();
        skinVersionCache.clear();
    }

    // ── Skin helpers ──────────────────────────────────────────────────────────

    /**
     * Get texture properties for a real player (from PacketEvents UserProfile).
     * Returns NO_SKIN if unavailable.
     */
    public List<TextureProperty> getPlayerSkin(Player player) {
        try {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) return NO_SKIN;
            UserProfile profile = user.getProfile();
            if (profile == null) return NO_SKIN;
            List<TextureProperty> props = profile.getTextureProperties();
            return (props != null && !props.isEmpty()) ? props : NO_SKIN;
        } catch (Throwable ignored) {
            return NO_SKIN;
        }
    }

    /**
     * Check whether a player's skin has changed since the last render.
     * Returns true if the skin is new/different; as a side effect updates the cache.
     */
    public boolean checkAndUpdateSkinVersion(UUID playerUuid, List<TextureProperty> props) {
        String newValue = props.isEmpty() ? "" : props.get(0).getValue();
        String oldValue = skinVersionCache.put(playerUuid, newValue);
        return !newValue.equals(oldValue == null ? "" : oldValue);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static UserProfile buildProfile(TabSlot slot) {
        List<TextureProperty> skin = slot.skin();
        if (skin == null || skin.isEmpty()) {
            return new UserProfile(slot.uuid(), slot.profileName());
        }
        return new UserProfile(slot.uuid(), slot.profileName(), skin);
    }

    private static UserProfile getRealProfile(Player player) {
        try {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null && user.getProfile() != null) return user.getProfile();
        } catch (Throwable ignored) {}
        return new UserProfile(player.getUniqueId(), player.getName());
    }

    /** Serialise a Component to a stable string key for cache comparison. */
    private static String miniText(Component c) {
        if (c == null) return "";
        // Use toString() — Component implementations have stable toString
        return c.toString();
    }

    // ── TabSlot record ────────────────────────────────────────────────────────

    /**
     * One row in the custom tab list.
     *
     * @param uuid        Stable deterministic UUID for this slot.
     * @param profileName Sort key (controls ordering in the tab list).
     * @param displayName What the viewer sees in the tab row.
     * @param skin        Texture properties (head icon). Use {@link #NO_SKIN} for default.
     * @param latency     Ping bars: 1 = green, ~300 = yellow, ~600 = red, -1 = no bars (X icon on some clients).
     * @param skinChanged Whether the skin changed since last render (triggers ADD_PLAYER re-send).
     */
    public record TabSlot(
            UUID uuid,
            String profileName,
            Component displayName,
            List<TextureProperty> skin,
            int latency,
            boolean skinChanged
    ) {
        /** Convenience ctor — skinChanged defaults to false. */
        public TabSlot(UUID uuid, String profileName, Component displayName, List<TextureProperty> skin, int latency) {
            this(uuid, profileName, displayName, skin, latency, false);
        }
    }
}
