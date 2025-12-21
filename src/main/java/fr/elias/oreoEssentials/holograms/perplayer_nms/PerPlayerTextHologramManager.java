package fr.elias.oreoEssentials.holograms.perplayer_nms;

import fr.elias.oreoEssentials.holograms.OreoHologramData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PerPlayerTextHologramManager {

    private final Plugin plugin;

    // nameLower -> holo
    private final Map<String, PerPlayerTextHologram> holos = new ConcurrentHashMap<>();

    public PerPlayerTextHologramManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean exists(String name) {
        if (name == null) return false;
        return holos.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Optional<PerPlayerTextHologram> get(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(holos.get(name.toLowerCase(Locale.ROOT)));
    }

    public PerPlayerTextHologram createOrGet(String name, Location loc) {
        String key = name.toLowerCase(Locale.ROOT);
        return holos.computeIfAbsent(key, k -> new PerPlayerTextHologram(this, name, loc));
    }

    public void remove(String name) {
        if (name == null) return;
        PerPlayerTextHologram h = holos.remove(name.toLowerCase(Locale.ROOT));
        if (h != null) {
            try { h.destroyForAll(); } catch (Throwable ignored) {}
        }
    }

    public void loadFromData(List<OreoHologramData> all) {
        holos.clear();
        if (all == null) return;

        for (OreoHologramData d : all) {
            if (d == null || d.type == null || d.location == null) continue;
            if (!d.type.name().equalsIgnoreCase("TEXT")) continue;

            Location loc = d.location.toLocation();
            if (loc == null) continue;

            PerPlayerTextHologram h = createOrGet(d.name, loc);
            h.setLines(d.lines);
            h.setViewDistance(d.visibilityDistance > 0 ? d.visibilityDistance : 64.0);
            h.setUpdateIntervalTicks(d.updateIntervalTicks > 0 ? d.updateIntervalTicks : 20L);
        }
    }

    public void unload() {
        for (PerPlayerTextHologram h : holos.values()) {
            try { h.destroyForAll(); } catch (Throwable ignored) {}
        }
        holos.clear();
    }

    public void tick() {
        for (PerPlayerTextHologram h : holos.values()) {
            try { h.tick(); }
            catch (Throwable t) {
                plugin.getLogger().warning("[PerPlayerHolograms] tick failed for " + h.getName() + ": " + t.getMessage());
            }
        }
    }

    // safety cleanup on quit
    public void onQuit(Player p) {
        for (PerPlayerTextHologram h : holos.values()) {
            try { h.forceDestroy(p); } catch (Throwable ignored) {}
        }
    }
}
