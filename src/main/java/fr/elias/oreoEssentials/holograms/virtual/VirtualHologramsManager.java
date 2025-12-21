package fr.elias.oreoEssentials.holograms.virtual;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualHologramsManager {

    private final Plugin plugin;
    private final NmsBridge nms;

    private final AtomicInteger entityIdAlloc = new AtomicInteger(200_000);

    private final Map<String, VirtualTextHologram> textHolos = new ConcurrentHashMap<>();

    public VirtualHologramsManager(Plugin plugin, NmsBridge nms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.nms = Objects.requireNonNull(nms, "nms");
    }

    public Plugin plugin() { return plugin; }
    public NmsBridge nms() { return nms; }

    public int allocateEntityId() {
        int id = entityIdAlloc.getAndIncrement();
        if (id > 2_000_000_000) entityIdAlloc.set(200_000);
        return id;
    }

    public long currentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (Throwable t) {
            return System.currentTimeMillis() / 50L;
        }
    }

    public Player getOnline(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return (p != null && p.isOnline()) ? p : null;
    }

    public Optional<VirtualTextHologram> getText(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(textHolos.get(name.toLowerCase(Locale.ROOT)));
    }

    public VirtualTextHologram createOrGetText(String name, org.bukkit.Location location) {
        String key = name.toLowerCase(Locale.ROOT);
        return textHolos.computeIfAbsent(key, k -> new VirtualTextHologram(this, name, location));
    }

    public void removeText(String name) {
        if (name == null) return;
        VirtualTextHologram h = textHolos.remove(name.toLowerCase(Locale.ROOT));
        if (h != null) h.hideAll();
    }

    public void tick() {
        for (VirtualTextHologram h : textHolos.values()) {
            try { h.tick(); } catch (Throwable ignored) {}
        }
    }

    public void cleanupPlayer(Player p) {
        for (VirtualTextHologram h : textHolos.values()) {
            try { h.forceCleanup(p); } catch (Throwable ignored) {}
        }
    }
}
