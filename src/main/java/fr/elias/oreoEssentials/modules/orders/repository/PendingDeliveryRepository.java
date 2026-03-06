package fr.elias.oreoEssentials.modules.orders.repository;

import fr.elias.oreoEssentials.modules.orders.model.PendingDelivery;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists items that could not be delivered immediately
 * (player offline or on a different server).
 */
public interface PendingDeliveryRepository {

    /** Save a new pending delivery. */
    CompletableFuture<Void> save(PendingDelivery delivery);

    /** Load all pending deliveries for a given player. */
    CompletableFuture<List<PendingDelivery>> loadForPlayer(UUID playerUuid);

    /** Delete a delivery after it has been successfully given to the player. */
    CompletableFuture<Void> delete(String deliveryId);

    /** Flush/close underlying connections. */
    CompletableFuture<Void> close();
}