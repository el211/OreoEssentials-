package fr.elias.oreoEssentials.holograms.virtual;

import fr.elias.oreoEssentials.holograms.HoloText;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualTextHologram {

    private final VirtualHologramsManager manager;

    private final String name;
    private Location location;

    private List<String> lines = new ArrayList<>();
    private double viewDistance = 64.0;
    private long updateIntervalTicks = 20L;

    private long lastRefreshTick = 0L;

    // viewer -> entityId (client-side virtual entity)
    private final Map<UUID, Integer> viewers = new ConcurrentHashMap<>();

    public VirtualTextHologram(VirtualHologramsManager manager, String name, Location location) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.name = Objects.requireNonNull(name, "name");
        this.location = location == null ? null : location.clone();
    }

    public String getName() { return name; }

    public Location getLocation() { return location == null ? null : location.clone(); }

    public void setLocation(Location loc) {
        this.location = (loc == null ? null : loc.clone());
        // teleport for current viewers
        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player p = manager.getOnline(uuid);
            if (p != null) teleport(p);
        }
    }

    public void setLines(List<String> lines) {
        this.lines = (lines == null ? new ArrayList<>() : new ArrayList<>(lines));
        // instant refresh for viewers
        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player p = manager.getOnline(uuid);
            if (p != null) refresh(p);
        }
    }

    public void setViewDistance(double viewDistance) {
        this.viewDistance = Math.max(2.0, viewDistance);
    }

    public void setUpdateIntervalTicks(long updateIntervalTicks) {
        this.updateIntervalTicks = Math.max(0L, updateIntervalTicks);
    }

    public boolean isViewer(Player p) {
        return p != null && viewers.containsKey(p.getUniqueId());
    }

    public void tick() {
        if (location == null || location.getWorld() == null) {
            hideAll();
            return;
        }

        // show/hide by distance
        for (Player p : location.getWorld().getPlayers()) {
            if (p == null || !p.isOnline()) continue;

            double d2 = p.getLocation().distanceSquared(location);
            boolean inRange = d2 <= (viewDistance * viewDistance);
            boolean viewing = isViewer(p);

            if (inRange && !viewing) show(p);
            if (!inRange && viewing) hide(p);
        }

        if (updateIntervalTicks <= 0) return;

        long nowTick = manager.currentTick();
        if (nowTick - lastRefreshTick < updateIntervalTicks) return;
        lastRefreshTick = nowTick;

        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player p = manager.getOnline(uuid);
            if (p != null) refresh(p);
        }
    }

    public boolean show(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (location == null || location.getWorld() == null) return false;
        if (player.getWorld() != location.getWorld()) return false;

        if (isViewer(player)) return true;

        int entityId = manager.allocateEntityId();
        viewers.put(player.getUniqueId(), entityId);

        manager.nms().spawnTextDisplay(player, entityId, location);
        refresh(player); // IMPORTANT: send text metadata per player
        return true;
    }

    public boolean hide(Player player) {
        if (player == null) return false;

        Integer entityId = viewers.remove(player.getUniqueId());
        if (entityId == null) return false;

        manager.nms().destroy(player, entityId);
        return true;
    }

    public void hideAll() {
        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player p = manager.getOnline(uuid);
            if (p != null) hide(p);
            else viewers.remove(uuid);
        }
    }

    public void forceCleanup(Player player) {
        if (player == null) return;
        hide(player);
    }

    public void teleport(Player player) {
        Integer entityId = viewers.get(player.getUniqueId());
        if (entityId == null) return;
        if (location == null) return;

        manager.nms().teleport(player, entityId, location);
    }

    public void refresh(Player player) {
        Integer entityId = viewers.get(player.getUniqueId());
        if (entityId == null) return;

        // ✅ THIS is the missing piece in your current system:
        // render using THIS viewer so PlaceholderAPI resolves per-player.
        Component text = HoloText.render(lines, player);
        manager.nms().updateText(player, entityId, text);
    }
}
