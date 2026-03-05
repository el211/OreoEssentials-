package fr.elias.oreoEssentials.modules.orders.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.OrdersConfig;
import fr.elias.oreoEssentials.modules.orders.OrdersModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players with OrderBrowserMenu open and provides debounced live-refresh.
 * Registered as a Bukkit listener to unregister viewers on inventory close.
 */
public final class OrdersGuiManager implements Listener {

    private final OreoEssentials plugin;
    private final OrdersModule module;

    /** page the player is currently viewing */
    private final Map<UUID, Integer> viewers = new ConcurrentHashMap<>();

    /** Debounce task IDs — cancel previous before scheduling new one */
    private final Map<UUID, Integer> debounceTaskIds = new ConcurrentHashMap<>();

    /** Global refresh task ID — cancel on scheduleRefreshAll */
    private int globalRefreshTaskId = -1;

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
        cancelDebounce(uuid);
    }


    /**
     * Schedule a debounced refresh for a single player (e.g. after their own action).
     */
    public void scheduleRefreshFor(Player p) {
        cancelDebounce(p.getUniqueId());
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewers.containsKey(p.getUniqueId()) && p.isOnline()) {
                openBrowserAtPage(p, viewers.getOrDefault(p.getUniqueId(), 0));
            }
        }, module.getConfig().liveRefreshDebounceTicks()).getTaskId();
        debounceTaskIds.put(p.getUniqueId(), taskId);
    }

    /**
     * Schedule a global debounced refresh for all open viewers.
     * Called when a cross-server event arrives.
     */
    public void scheduleRefreshAll() {
        if (globalRefreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(globalRefreshTaskId);
        }
        globalRefreshTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            globalRefreshTaskId = -1;
            for (Map.Entry<UUID, Integer> entry : viewers.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) {
                    viewers.remove(entry.getKey());
                    continue;
                }
                openBrowserAtPage(p, entry.getValue());
            }
        }, module.getConfig().liveRefreshDebounceTicks()).getTaskId();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void openBrowserAtPage(Player p, int page) {
        OrderBrowserMenu.getInventory(module).open(p, page);
    }

    private void cancelDebounce(UUID uuid) {
        Integer taskId = debounceTaskIds.remove(uuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        unregisterViewer(p.getUniqueId());
    }
}
