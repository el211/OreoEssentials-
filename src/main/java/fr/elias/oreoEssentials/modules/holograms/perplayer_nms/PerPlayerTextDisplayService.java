package fr.elias.oreoEssentials.modules.holograms.perplayer_nms;

import fr.elias.oreoEssentials.modules.holograms.nms.NmsHologramBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


public final class PerPlayerTextDisplayService {



    public static final class Entry {
        public final TextDisplay entity;
        public final Supplier<List<String>> lines;
        public final double viewDistance;
        public final long refreshEveryTicks;

        public Entry(TextDisplay entity,
                     Supplier<List<String>> lines,
                     double viewDistance,
                     long refreshEveryTicks) {
            this.entity           = entity;
            this.lines            = lines;
            this.viewDistance     = Math.max(2.0, viewDistance);
            this.refreshEveryTicks = Math.max(0L, refreshEveryTicks);
        }
    }



    private final Plugin plugin;
    private final PerPlayerTextDisplayController controller;

    private final Map<UUID, Entry> tracked = new ConcurrentHashMap<>();



    public PerPlayerTextDisplayService(Plugin plugin, NmsHologramBridge nms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");


        this.controller = new PerPlayerTextDisplayController(
                Objects.requireNonNull(nms, "nms"),
                holoId -> {
                    Entry e = tracked.get(holoId);
                    return (e == null) ? List.of() : safeLines(e.lines);
                }
        );
    }


    public void track(TextDisplay td,
                      Supplier<List<String>> lines,
                      double viewDistance,
                      long refreshEveryTicks) {
        if (td == null || td.isDead()) return;
        tracked.put(td.getUniqueId(), new Entry(td, lines, viewDistance, refreshEveryTicks));
    }

    public void untrack(UUID uuid) {
        if (uuid == null) return;
        Entry e = tracked.remove(uuid);
        if (e == null) return;

        controller.untrackHologram(uuid);

        for (Player p : Bukkit.getOnlinePlayers()) {
            controller.hide(e.entity, p);
        }
    }

    public void untrack(TextDisplay td) {
        if (td != null) untrack(td.getUniqueId());
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(tracked.keySet())) untrack(id);
        tracked.clear();
    }


    public void tick() {

        for (Entry e : tracked.values()) {
            final TextDisplay td = e.entity;
            if (td == null || td.isDead() || td.getWorld() == null) continue;

            final Location loc  = td.getLocation();
            final double  maxD2 = e.viewDistance * e.viewDistance;

            for (Player p : loc.getWorld().getPlayers()) {
                if (p == null || !p.isOnline()) continue;

                double  d2       = p.getLocation().distanceSquared(loc);
                boolean inRange  = d2 <= maxD2;

                if (inRange) {

                    if (!controller.isTracked(td.getUniqueId(), p.getUniqueId())) {
                        controller.show(td, p, safeLines(e.lines));
                    }

                } else {
                    controller.hide(td, p);
                }
            }
        }


        long minRefresh = Long.MAX_VALUE;
        for (Entry e : tracked.values()) {
            if (e.refreshEveryTicks > 0 && e.refreshEveryTicks < minRefresh) {
                minRefresh = e.refreshEveryTicks;
            }
        }

        if (minRefresh != Long.MAX_VALUE && minRefresh > 0) {
            controller.tick(activeTextDisplays(), minRefresh);
        }
    }


    public void cleanupPlayer(Player p) {
        if (p == null) return;
        for (Entry e : tracked.values()) {
            try { controller.hide(e.entity, p); } catch (Throwable ignored) {}
        }
    }



    private Collection<TextDisplay> activeTextDisplays() {
        List<TextDisplay> list = new ArrayList<>(tracked.size());
        for (Entry e : tracked.values()) {
            if (e.entity != null && !e.entity.isDead()) list.add(e.entity);
        }
        return list;
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