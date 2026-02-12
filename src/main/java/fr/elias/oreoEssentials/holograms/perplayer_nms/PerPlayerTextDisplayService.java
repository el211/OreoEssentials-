// File: src/main/java/fr/elias/oreoEssentials/holograms/perplayer_nms/PerPlayerTextDisplayService.java
package fr.elias.oreoEssentials.holograms.perplayer_nms;

import fr.elias.oreoEssentials.holograms.nms.NmsHologramBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Wires your PerPlayerTextDisplayController to real TextDisplay entities.
 * - Tracks TextDisplays you register (uuid -> metadata)
 * - Every tick:
 *   - show/hide viewers by distance
 *   - refresh per-player placeholders on interval
 */
public final class PerPlayerTextDisplayService {

    public static final class Entry {
        public final TextDisplay entity;
        public final Supplier<List<String>> lines;
        public final double viewDistance;
        public final long refreshEveryTicks;
        public Entry(TextDisplay entity, Supplier<List<String>> lines, double viewDistance, long refreshEveryTicks) {
            this.entity = entity;
            this.lines = lines;
            this.viewDistance = Math.max(2.0, viewDistance);
            this.refreshEveryTicks = Math.max(0L, refreshEveryTicks);
        }
    }

    private final Plugin plugin;
    private final PerPlayerTextDisplayController controller;

    // entityId -> entry
    private final Map<UUID, Entry> tracked = new ConcurrentHashMap<>();

    public PerPlayerTextDisplayService(Plugin plugin, NmsHologramBridge nms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.controller = new PerPlayerTextDisplayController(Objects.requireNonNull(nms, "nms"));
    }

    public void track(TextDisplay td, Supplier<List<String>> lines, double viewDistance, long refreshEveryTicks) {
        if (td == null || td.isDead()) return;
        tracked.put(td.getUniqueId(), new Entry(td, lines, viewDistance, refreshEveryTicks));
    }

    public void untrack(UUID uuid) {
        if (uuid == null) return;
        Entry e = tracked.remove(uuid);
        if (e != null) {
            // Hide from all tracked viewers (clean)
            for (Player p : Bukkit.getOnlinePlayers()) {
                controller.hide(e.entity, p);
            }
        }
    }

    public void untrack(TextDisplay td) {
        if (td != null) untrack(td.getUniqueId());
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(tracked.keySet())) untrack(id);
        tracked.clear();
    }

    /** Call this on a repeating task (e.g., every 10 ticks). */
    public void tick() {
        // 1) Range show/hide
        for (Entry e : tracked.values()) {
            final TextDisplay td = e.entity;
            if (td == null || td.isDead() || td.getWorld() == null) continue;

            final Location loc = td.getLocation();
            final double maxD2 = e.viewDistance * e.viewDistance;

            for (Player p : loc.getWorld().getPlayers()) {
                if (p == null || !p.isOnline()) continue;

                double d2 = p.getLocation().distanceSquared(loc);
                boolean inRange = d2 <= maxD2;

                // Use controller's tracking
                if (inRange) {
                    controller.show(td, p, safeLines(e.lines));
                } else {
                    controller.hide(td, p);
                }
            }
        }

        // 2) Per-player refresh cadence per hologram (batched by min refresh)
        // Compute a minimum refresh interval across tracked entries, then let controller do the per-holo throttling.
        long minRefresh = Long.MAX_VALUE;
        for (Entry e : tracked.values()) {
            if (e.refreshEveryTicks > 0 && e.refreshEveryTicks < minRefresh) minRefresh = e.refreshEveryTicks;
        }
        if (minRefresh == Long.MAX_VALUE) minRefresh = 0L;

        if (minRefresh > 0) {
            controller.tick(activeTextDisplays(), minRefresh);
        }
    }

    private Collection<TextDisplay> activeTextDisplays() {
        List<TextDisplay> list = new ArrayList<>(tracked.size());
        for (Entry e : tracked.values()) {
            if (e.entity != null && !e.entity.isDead()) list.add(e.entity);
        }
        return list;
    }
    public void cleanupPlayer(Player p) {
        if (p == null) return;
        for (Entry e : tracked.values()) {
            try { controller.hide(e.entity, p); } catch (Throwable ignored) {}
        }
    }
    private static List<String> safeLines(Supplier<List<String>> sup) {
        try {
            List<String> v = sup.get();
            return (v == null) ? List.of() : v;
        } catch (Throwable t) {
            return List.of();
        }
    }
}
