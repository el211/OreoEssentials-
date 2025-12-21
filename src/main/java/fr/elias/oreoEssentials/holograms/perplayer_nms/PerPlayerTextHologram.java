package fr.elias.oreoEssentials.holograms.perplayer_nms;

import de.oliver.fancysitula.api.entities.FS_RealPlayer;
import de.oliver.fancysitula.api.entities.FS_TextDisplay;
import de.oliver.fancysitula.factories.FancySitula;
import fr.elias.oreoEssentials.holograms.HoloText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PerPlayerTextHologram {

    private final PerPlayerTextHologramManager manager;

    private final String name;
    private Location location;

    private List<String> lines = new ArrayList<>();
    private double viewDistance = 64.0;
    private long updateIntervalTicks = 20L;
    private long lastUpdateTick = 0L;

    // viewerUUID -> display instance (one per viewer)
    private final Map<UUID, FS_TextDisplay> displays = new ConcurrentHashMap<>();

    public PerPlayerTextHologram(PerPlayerTextHologramManager manager, String name, Location location) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.name = Objects.requireNonNull(name, "name");
        this.location = (location == null ? null : location.clone());
    }

    public String getName() { return name; }

    public Location getLocation() { return location == null ? null : location.clone(); }
    public List<String> getLinesCopy() { return new ArrayList<>(lines); }
    public double getViewDistance() { return viewDistance; }
    public long getUpdateIntervalTicks() { return updateIntervalTicks; }

    public void setLines(List<String> lines) {
        this.lines = (lines == null ? new ArrayList<>() : new ArrayList<>(lines));
        // refresh all current viewers instantly
        for (UUID uuid : new ArrayList<>(displays.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) refresh(p);
            else displays.remove(uuid);
        }
    }

    public void setViewDistance(double viewDistance) {
        this.viewDistance = Math.max(2.0, viewDistance);
    }

    public void setUpdateIntervalTicks(long ticks) {
        this.updateIntervalTicks = Math.max(0L, ticks);
    }

    public void moveTo(Location loc) {
        this.location = (loc == null ? null : loc.clone());
        // teleport all viewers
        for (UUID uuid : new ArrayList<>(displays.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) refresh(p);
            else displays.remove(uuid);
        }
    }

    /** Called from manager every tick (or every 2 ticks). */
    public void tick() {
        if (location == null || location.getWorld() == null) {
            destroyForAll();
            return;
        }

        // 1) enter/leave range
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;

            if (p.getWorld() != location.getWorld()) {
                hide(p);
                continue;
            }

            double dist2 = p.getLocation().distanceSquared(location);
            boolean inRange = dist2 <= (viewDistance * viewDistance);
            boolean has = displays.containsKey(p.getUniqueId());

            if (inRange && !has) show(p);
            if (!inRange && has) hide(p);
        }

        // 2) placeholder refresh timer
        if (updateIntervalTicks <= 0) return;

        long nowTick = Bukkit.getCurrentTick();
        if (nowTick - lastUpdateTick < updateIntervalTicks) return;
        lastUpdateTick = nowTick;

        for (UUID uuid : new ArrayList<>(displays.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) refresh(p);
            else displays.remove(uuid);
        }
    }

    public void destroyForAll() {
        for (UUID uuid : new ArrayList<>(displays.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) hide(p);
            else displays.remove(uuid);
        }
    }

    public void forceDestroy(Player p) {
        if (p == null) return;
        hide(p);
    }

    /* ---------------- Situla-style show/hide/refresh ---------------- */

    public boolean show(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (location == null || location.getWorld() == null) return false;
        if (player.getWorld() != location.getWorld()) return false;

        // already shown
        if (displays.containsKey(player.getUniqueId())) {
            refresh(player);
            return true;
        }

        FS_RealPlayer fsPlayer = new FS_RealPlayer(player);

        FS_TextDisplay display = new FS_TextDisplay();
        display.setLocation(location);

        // view range (culling client-side)
        display.setViewRange((float) viewDistance);

        // set text per-player
        display.setText(renderFor(player));

        // spawn only for this player
        FancySitula.ENTITY_FACTORY.spawnEntityFor(fsPlayer, display);

        displays.put(player.getUniqueId(), display);

        // push metadata right away
        FancySitula.ENTITY_FACTORY.setEntityDataFor(fsPlayer, display);
        return true;
    }

    public boolean hide(Player player) {
        if (player == null) return false;

        FS_TextDisplay display = displays.remove(player.getUniqueId());
        if (display == null) return false;

        try {
            FS_RealPlayer fsPlayer = new FS_RealPlayer(player);
            FancySitula.ENTITY_FACTORY.despawnEntityFor(fsPlayer, display);
        } catch (Throwable ignored) {}

        return true;
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        if (location == null || location.getWorld() == null) return;

        FS_TextDisplay display = displays.get(player.getUniqueId());
        if (display == null) {
            // not shown yet -> show
            show(player);
            return;
        }

        // keep it in sync
        display.setLocation(location);
        display.setViewRange((float) viewDistance);

        FS_RealPlayer fsPlayer = new FS_RealPlayer(player);

        // teleport packet (smooth refresh like Fancy)
        FancySitula.PACKET_FACTORY.createTeleportEntityPacket(
                display.getId(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                true
        ).send(fsPlayer);

        // set per-player text then update entity data
        display.setText(renderFor(player));
        FancySitula.ENTITY_FACTORY.setEntityDataFor(fsPlayer, display);
    }

    private Component renderFor(Player player) {
        return HoloText.render(lines, player);
    }
}
