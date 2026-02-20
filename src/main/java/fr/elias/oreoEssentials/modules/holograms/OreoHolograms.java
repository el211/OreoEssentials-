package fr.elias.oreoEssentials.modules.holograms;

import fr.elias.oreoEssentials.modules.freeze.HoloTags;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class OreoHolograms {

    private final Plugin plugin;
    private final OreoHologramsStore store;
    private final Map<String, OreoHologram> holos = new ConcurrentHashMap<>();
    private final HoloTags tags;
    public OreoHolograms(Plugin plugin) {
        this.plugin = plugin;
        this.store = new OreoHologramsStore(plugin.getDataFolder());
        this.tags   = new HoloTags(plugin);

    }


    public void load() {
        holos.clear();
        boolean migrated = false;

        for (OreoHologramData d : store.loadAll()) {
            try {
                if (d.billboard == null) {
                    d.billboard = OreoHologramBillboard.CENTER;
                    migrated = true;
                }

                if (d.visibilityDistance <= 0) {
                    d.visibilityDistance = 64.0;
                    migrated = true;
                }

                OreoHologram h = OreoHologramFactory.fromData(d);
                holos.put(h.getName().toLowerCase(Locale.ROOT), h);
                h.spawnIfMissing();
            } catch (Throwable t) {
                plugin.getLogger().warning("[OreoHolograms] Failed to load " + d.name + ": " + t.getMessage());
            }
        }

        if (migrated) save();
    }

    public void save() {
        List<OreoHologramData> all = new ArrayList<>(holos.size());
        for (OreoHologram h : holos.values()) all.add(h.toData());
        store.saveAll(all);
    }

    public void unload() {
        for (OreoHologram h : holos.values()) {
            try { h.despawn(); } catch (Throwable ignored) {}
        }
        holos.clear();
    }
    public HoloTags tags() { return tags; }

    public void tickAll() {
        for (OreoHologram h : holos.values()) {
            try { h.tick(); } catch (Throwable ignored) {}
        }
    }

    /* ---------------------- CRUD ----------------------- */

    public boolean exists(String name) { return holos.containsKey(name.toLowerCase(Locale.ROOT)); }
    public OreoHologram get(String name) { return holos.get(name.toLowerCase(Locale.ROOT)); }
    public Collection<OreoHologram> all() { return Collections.unmodifiableCollection(holos.values()); }

    public OreoHologram create(OreoHologramType type, String name, OreoHologramLocation loc) {
        if (exists(name)) throw new IllegalArgumentException("Hologram already exists: " + name);
        OreoHologram h = OreoHologramFactory.create(type, name, loc);
        holos.put(name.toLowerCase(Locale.ROOT), h);
        h.spawnIfMissing();
        save();
        return h;
    }

    public void remove(String name) {
        OreoHologram h = holos.remove(name.toLowerCase(Locale.ROOT));
        if (h != null) {
            try { h.despawn(); } catch (Throwable ignored) {}
            save();
        }
    }

    public OreoHologram copy(String from, String to) {
        OreoHologram src = get(from);
        if (src == null) throw new IllegalArgumentException("Source hologram not found: " + from);
        if (exists(to)) throw new IllegalArgumentException("Target hologram already exists: " + to);

        OreoHologramData c = deepCopy(src.toData());
        c.name = to;

        OreoHologram dst = OreoHologramFactory.fromData(c);
        holos.put(to.toLowerCase(Locale.ROOT), dst);
        dst.spawnIfMissing();
        save();
        return dst;
    }

    private OreoHologramData deepCopy(OreoHologramData d) {
        OreoHologramData c = new OreoHologramData();
        c.type = d.type;
        c.name = d.name;
        c.location = d.location;
        c.scale = d.scale;
        c.billboard = d.billboard;
        c.shadowStrength = d.shadowStrength;
        c.shadowRadius = d.shadowRadius;
        c.brightnessBlock = d.brightnessBlock;
        c.brightnessSky = d.brightnessSky;
        c.visibilityDistance = d.visibilityDistance;
        c.visibility = d.visibility;
        c.viewPermission = d.viewPermission;
        c.updateIntervalTicks = d.updateIntervalTicks;
        c.manualViewers = (d.manualViewers == null) ? new ArrayList<>() : new ArrayList<>(d.manualViewers);
        c.backgroundColor = d.backgroundColor;
        c.textShadow = d.textShadow;
        c.textAlign = d.textAlign;
        c.lines = (d.lines == null) ? new ArrayList<>() : new ArrayList<>(d.lines);
        c.blockType = d.blockType;
        c.itemStackBase64 = d.itemStackBase64;
        return c;
    }
}  