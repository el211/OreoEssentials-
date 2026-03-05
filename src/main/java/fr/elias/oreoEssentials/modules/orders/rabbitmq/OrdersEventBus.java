package fr.elias.oreoEssentials.modules.orders.rabbitmq;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.service.OrderService;
import fr.elias.oreoEssentials.modules.orders.gui.OrdersGuiManager;
import org.bukkit.Bukkit;

/**
 * Handles publishing OrderSyncPackets to RabbitMQ and receiving them from other servers.
 * On receive: applies to OrderService memory + triggers a debounced GUI refresh.
 */
public final class OrdersEventBus {

    private final OreoEssentials plugin;
    private final String serverId;
    private OrderService service;        // set after construction to break circular dep
    private OrdersGuiManager guiManager; // set after GUI is ready

    public OrdersEventBus(OreoEssentials plugin, String serverId) {
        this.plugin   = plugin;
        this.serverId = serverId;
    }

    public void setService(OrderService service)         { this.service = service; }
    public void setGuiManager(OrdersGuiManager manager)  { this.guiManager = manager; }


    public void publishCreated(Order order) {
        sendPacket(OrderSyncPacket.created(serverId, order));
    }

    public void publishUpdated(Order order) {
        sendPacket(OrderSyncPacket.updated(serverId, order));
    }

    public void publishRemoved(Order order) {
        sendPacket(OrderSyncPacket.removed(serverId, order));
    }


    /**
     * Called by OreoEssentials when an OrderSyncPacket arrives from another server.
     * Runs on the RabbitMQ consumer thread — schedules main-thread work as needed.
     */
    public void onReceive(OrderSyncPacket pkt) {
        if (pkt == null) return;
        if (serverId.equals(pkt.getServerId())) return; // ignore our own broadcasts

        // Apply to in-memory service (CopyOnWriteArrayList is thread-safe for reads;
        // we schedule mutating state on main thread for safety and GUI consistency)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (service != null) {
                service.applyIncomingEvent(pkt);
            }
            if (guiManager != null) {
                guiManager.scheduleRefreshAll();
            }
        });
    }


    private void sendPacket(OrderSyncPacket pkt) {
        try {
            var pm = plugin.getPacketManager();
            if (pm != null && pm.isInitialized()) pm.sendPacket(pkt);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Orders] Failed to broadcast packet: " + t.getMessage());
        }
    }
}
