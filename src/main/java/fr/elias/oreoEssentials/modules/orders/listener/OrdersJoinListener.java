package fr.elias.oreoEssentials.modules.orders.listener;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.repository.PendingDeliveryRepository;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.modules.orders.model.PendingDelivery;
import fr.elias.oreoEssentials.modules.orders.service.OrderService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.logging.Logger;

/**
 * Listens for player joins and delivers any items that were queued while they were offline
 * or on a different server.
 */
public final class OrdersJoinListener implements Listener {

    private final OreoEssentials           plugin;
    private final PendingDeliveryRepository deliveryRepo;
    private final Logger                   log;

    public OrdersJoinListener(OreoEssentials plugin, PendingDeliveryRepository deliveryRepo) {
        this.plugin       = plugin;
        this.deliveryRepo = deliveryRepo;
        this.log          = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Slight delay so the player is fully loaded before we touch their inventory
        OreScheduler.runLaterForEntity(plugin, player, () -> {
            if (!player.isOnline()) return;

            deliveryRepo.loadForPlayer(player.getUniqueId())
                    .thenAccept(deliveries -> {
                        if (deliveries.isEmpty()) return;

                        log.info("[Orders] Delivering " + deliveries.size()
                                + " pending item(s) to " + player.getName());

                        // Give items on main thread
                        OreScheduler.runForEntity(plugin, player, () -> {
                            for (PendingDelivery delivery : deliveries) {
                                tryDeliver(player, delivery);
                            }
                        });
                    });
        }, 20L); // 1 second after join
    }

    private void tryDeliver(Player player, PendingDelivery delivery) {
        if (!player.isOnline()) return;

        ItemStack item = OrderService.deserializeItem(delivery.getItemData());
        if (item == null) {
            log.warning("[Orders] Could not deserialize pending delivery " + delivery.getId()
                    + " for " + player.getName() + " — skipping.");
            deliveryRepo.delete(delivery.getId());
            return;
        }

        item.setAmount(delivery.getQuantity());
        OrderService.giveOrDrop(player, item);
        log.info("[Orders] Delivered " + delivery.getQuantity() + "x "
                + item.getType() + " to " + player.getName()
                + " (order=" + delivery.getOrderId() + ")");

        deliveryRepo.delete(delivery.getId());
    }
}