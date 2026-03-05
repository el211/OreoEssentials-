package fr.elias.oreoEssentials.modules.orders.repository;

import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage abstraction for Orders.
 * All methods are async (CompletableFuture) to avoid blocking the main thread.
 */
public interface OrderRepository {

    /** Persist a new order (INSERT or UPSERT). */
    CompletableFuture<Void> save(Order order);

    /** Load every order regardless of status. */
    CompletableFuture<List<Order>> loadAll();

    /** Load only ACTIVE orders — the typical list needed by the browser. */
    CompletableFuture<List<Order>> loadActive();

    /** Load all orders belonging to a given player. */
    CompletableFuture<List<Order>> loadByRequester(UUID requesterUuid);

    /**
     * Atomically fill {@code fillQty} units of order {@code orderId}.
     * <ul>
     *   <li>Decrements remainingQty and escrowRemaining proportionally.</li>
     *   <li>Sets status to COMPLETED when remainingQty reaches 0.</li>
     *   <li>Increments revision on success.</li>
     *   <li>Rejects (returns INSUFFICIENT_QTY/ALREADY_CLOSED/NOT_FOUND) if preconditions fail.</li>
     * </ul>
     */
    CompletableFuture<FillResult> atomicFill(String orderId, int fillQty, double unitPrice);

    /**
     * Atomically cancel an order — only succeeds if status is ACTIVE
     * and the UUID matches the requester.
     *
     * @return true if cancelled, false if not found / already closed / wrong owner
     */
    CompletableFuture<Boolean> atomicCancel(String orderId, UUID requesterUuid);

    /** Flush/close underlying connections. */
    CompletableFuture<Void> close();
}
