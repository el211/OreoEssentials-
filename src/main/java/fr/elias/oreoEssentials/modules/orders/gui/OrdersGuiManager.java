package fr.elias.oreoEssentials.modules.orders.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players with OrderBrowserMenu open and provides debounced live-refresh.
 * Refresh is done in-place via a dirty flag consumed by OrderBrowserMenu.update(),
 * so the inventory never closes/reopens — zero flicker for the viewer.
 */
public final class OrdersGuiManager implements Listener {

    private final OreoEssentials plugin;
    private final OrdersModule module;

    /** page the player is currently viewing */
    private final Map<UUID, Integer> viewers = new ConcurrentHashMap<>();

    /** Players whose browser inventory needs an in-place re-render next update() tick */
    private final Set<UUID> dirtyViewers = ConcurrentHashMap.newKeySet();

    /** Debounce tasks — cancel previous before scheduling new one */
    private final Map<UUID, OreTask> debounceTasks = new ConcurrentHashMap<>();

    /** Global debounce task */
    private OreTask globalDebounceTask = OreTask.EMPTY;

    public OrdersGuiManager(OreoEssentials plugin, OrdersModule module) {
        this.plugin = plugin;
        this.module = module;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerViewer(UUID uuid, int page) {
        viewers.put(uuid, page);
    }

    public void updatePage(UUID uuid, int page) {
        viewers.computeIfPresent(uuid, (k, v) -> page);
    }

    public void unregisterViewer(UUID uuid) {
        viewers.remove(uuid);
        dirtyViewers.remove(uuid);
        cancelDebounceTask(uuid);
    }

    /**
     * Schedule a debounced in-place refresh for a single player (e.g. after their own action).
     */
    public void scheduleRefreshFor(Player p) {
        cancelDebounceTask(p.getUniqueId());
        OreTask task = OreScheduler.runLater(plugin, () -> {
            if (viewers.containsKey(p.getUniqueId()) && p.isOnline()) {
                dirtyViewers.add(p.getUniqueId());
            }
        }, module.getConfig().liveRefreshDebounceTicks());
        debounceTasks.put(p.getUniqueId(), task);
    }

    /**
     * Schedule a global debounced in-place refresh for all open viewers.
     * Called when a cross-server event arrives. Marks all current viewers
     * dirty so OrderBrowserMenu.update() re-renders them without close/reopen.
     */
    public void scheduleRefreshAll() {
        globalDebounceTask.cancel();
        globalDebounceTask = OreScheduler.runLater(plugin, () -> {
            globalDebounceTask = OreTask.EMPTY;
            // Remove offline players and mark the rest dirty
            viewers.keySet().removeIf(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) return true;
                dirtyViewers.add(uuid);
                return false;
            });
        }, module.getConfig().liveRefreshDebounceTicks());
    }

    /**
     * Called from OrderBrowserMenu.update() each tick.
     * Returns true (and clears the flag) if this player's inventory needs a re-render.
     */
    public boolean consumeDirty(UUID uuid) {
        return dirtyViewers.remove(uuid);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void cancelDebounceTask(UUID uuid) {
        OreTask task = debounceTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        unregisterViewer(p.getUniqueId());
    }
}
