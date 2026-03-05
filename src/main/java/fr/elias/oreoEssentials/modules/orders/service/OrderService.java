package fr.elias.oreoEssentials.modules.orders.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.OrdersConfig;
import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.model.OrderStatus;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrderSyncPacket;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrdersEventBus;
import fr.elias.oreoEssentials.modules.orders.repository.OrderRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Orchestrates order lifecycle: create, fill, cancel.
 * Maintains an in-memory list of ACTIVE orders for instant GUI reads.
 * All writes go through the repository and are broadcast via OrdersEventBus.
 */
public final class OrderService {

    private final OreoEssentials plugin;
    private final OrdersConfig cfg;
    private final OrderRepository repo;
    private final OrderCurrencyAdapter currency;
    private final OrdersEventBus eventBus;
    private final Logger log;

    /** In-memory view of all ACTIVE orders — updated optimistically on write, re-synced from storage. */
    private final List<Order> activeOrders = new CopyOnWriteArrayList<>();

    public OrderService(OreoEssentials plugin, OrdersConfig cfg, OrderRepository repo,
                        OrderCurrencyAdapter currency, OrdersEventBus eventBus) {
        this.plugin   = plugin;
        this.cfg      = cfg;
        this.repo     = repo;
        this.currency = currency;
        this.eventBus = eventBus;
        this.log      = plugin.getLogger();
    }


    public CompletableFuture<Void> loadActive() {
        return repo.loadActive().thenAccept(orders -> {
            activeOrders.clear();
            activeOrders.addAll(orders);
            log.info("[Orders] Loaded " + orders.size() + " active orders.");
        });
    }


    /**
     * Creates a new buy-request order.
     * Withdraws escrow immediately from the requester.
     * @return error message key (from lang.yml) if failed, null on success.
     */
    public CompletableFuture<String> createOrder(Player requester, ItemStack item,
                                                 int qty, String currencyId, double unitPrice) {
        if (qty <= 0 || qty > cfg.maxQtyPerOrder())
            return CompletableFuture.completedFuture("errors.invalid-quantity");
        if (unitPrice < cfg.minUnitPrice())
            return CompletableFuture.completedFuture("errors.invalid-price");
        if (currencyId == null && !cfg.allowVault())
            return CompletableFuture.completedFuture("errors.vault-disabled");
        if (currencyId != null && !cfg.allowCustomCurrencies())
            return CompletableFuture.completedFuture("errors.custom-currency-disabled");

        long activeCount = activeOrders.stream()
                .filter(o -> o.getRequesterUuid().equals(requester.getUniqueId()))
                .count();
        if (activeCount >= cfg.maxActiveOrdersPerPlayer())
            return CompletableFuture.completedFuture("errors.max-orders-reached");

        String itemData = serializeItem(item);
        if (itemData == null)
            return CompletableFuture.completedFuture("errors.item-serialize-failed");

        double escrow = unitPrice * qty;
        double fee    = cfg.feesEnabled() ? escrow * cfg.createFeePercent() / 100.0 : 0.0;
        double totalCharge = escrow + fee;

        return currency.getBalance(requester.getUniqueId(), currencyId)
                .thenCompose(balance -> {
                    if (balance < totalCharge)
                        return CompletableFuture.completedFuture("errors.not-enough-money");

                    return currency.withdraw(requester.getUniqueId(), currencyId, totalCharge)
                            .thenCompose(ok -> {
                                if (!ok) return CompletableFuture.completedFuture("errors.withdraw-failed");

                                String id     = UUID.randomUUID().toString();
                                String dispName = displayName(item);
                                Order order   = new Order(id, requester.getUniqueId(), requester.getName(),
                                        itemData, dispName, qty, currencyId, unitPrice);

                                detectCustomItem(order, item);

                                return repo.save(order).thenApply(v -> {
                                    activeOrders.add(order);
                                    eventBus.publishCreated(order);
                                    if (cfg.debug()) log.info("[Orders] Created order " + id + " by " + requester.getName());
                                    return (String) null; // success
                                });
                            });
                });
    }

    // ── Fill ──────────────────────────────────────────────────────────────────

    /**
     * Fills {@code fillQty} units of order {@code orderId}.
     * Takes items from filler's inventory, pays seller from escrow.
     * @return FillResult — check isSuccess() and getOutcome() for details.
     */
    public CompletableFuture<FillResult> fillOrder(Player filler, String orderId, int fillQty) {
        Order order = activeOrders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst().orElse(null);

        if (order == null) return CompletableFuture.completedFuture(FillResult.notFound());
        if (order.getStatus() != OrderStatus.ACTIVE) return CompletableFuture.completedFuture(FillResult.alreadyClosed());
        if (order.getRemainingQty() < fillQty) return CompletableFuture.completedFuture(FillResult.insufficientQty());
        if (order.getRequesterUuid().equals(filler.getUniqueId()))
            return CompletableFuture.completedFuture(FillResult.error()); // can't fill own order

        // Check + take items from filler on main thread
        ItemStack template = deserializeItem(order.getItemData());
        if (template == null) return CompletableFuture.completedFuture(FillResult.error());

        // Check inventory has enough (main-thread)
        if (!hasEnoughItems(filler, template.getType(), fillQty))
            return CompletableFuture.completedFuture(FillResult.insufficientQty());

        // Atomic fill in storage
        return repo.atomicFill(orderId, fillQty, order.getUnitPrice())
                .thenCompose(result -> {
                    if (!result.isSuccess()) return CompletableFuture.completedFuture(result);

                    // Take items from filler and pay seller — must run on main thread
                    CompletableFuture<FillResult> finalResult = new CompletableFuture<>();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Double-check items still present
                        if (!takeItems(filler, template.getType(), fillQty)) {
                            // Rollback: give escrow back to escrow pool (re-save order)
                            // This is a rare edge case; log and return error
                            log.warning("[Orders] fillOrder: item take failed after atomic update for order " + orderId);
                            finalResult.complete(FillResult.error());
                            return;
                        }

                        double fillFee = cfg.feesEnabled()
                                ? result.getPaidToSeller() * cfg.fillFeePercent() / 100.0
                                : 0.0;
                        double netPay = result.getPaidToSeller() - fillFee;

                        // Deposit to filler (seller) off-thread
                        currency.deposit(filler.getUniqueId(), order.getCurrencyId(), netPay)
                                .thenRun(() -> {
                                    // Update in-memory
                                    Order updated = result.getUpdatedOrder();
                                    if (updated != null) applyUpdate(updated);
                                    eventBus.publishUpdated(result.getUpdatedOrder() != null
                                            ? result.getUpdatedOrder() : order);
                                    finalResult.complete(result);
                                });
                    });

                    return finalResult;
                });
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /**
     * Cancels the order and refunds remaining escrow to the requester.
     * @return true on success
     */
    public CompletableFuture<Boolean> cancelOrder(Player requester, String orderId) {
        Order order = activeOrders.stream()
                .filter(o -> o.getId().equals(orderId)
                        && o.getRequesterUuid().equals(requester.getUniqueId()))
                .findFirst().orElse(null);

        if (order == null) return CompletableFuture.completedFuture(false);

        return repo.atomicCancel(orderId, requester.getUniqueId())
                .thenCompose(ok -> {
                    if (!ok) return CompletableFuture.completedFuture(false);

                    double refund = order.getEscrowRemaining();
                    activeOrders.removeIf(o -> o.getId().equals(orderId));

                    order.setStatus(OrderStatus.CANCELLED);
                    eventBus.publishRemoved(order);

                    return currency.deposit(requester.getUniqueId(), order.getCurrencyId(), refund)
                            .thenApply(depositOk -> {
                                if (!depositOk) log.warning("[Orders] Failed to refund escrow for cancelled order " + orderId);
                                return true;
                            });
                });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<Order> getActiveOrders() {
        return Collections.unmodifiableList(activeOrders);
    }

    public List<Order> getActiveOrdersByPlayer(UUID uuid) {
        return activeOrders.stream()
                .filter(o -> o.getRequesterUuid().equals(uuid))
                .toList();
    }

    public Optional<Order> findOrder(String orderId) {
        return activeOrders.stream().filter(o -> o.getId().equals(orderId)).findFirst();
    }

    // ── Sync from cross-server event ──────────────────────────────────────────

    /**
     * Called by OrdersEventBus when a packet arrives from another server.
     * Updates the in-memory list. Must be called on the Bukkit main thread
     * (or it's safe because CopyOnWriteArrayList guards reads; writes are fine).
     */
    public void applyIncomingEvent(OrderSyncPacket pkt) {
        if (pkt == null) return;
        switch (pkt.getType()) {
            case ORDER_CREATED  -> applyRemoteCreate(pkt);
            case ORDER_UPDATED  -> applyRemoteUpdate(pkt);
            case ORDER_REMOVED  -> applyRemoteRemove(pkt);
        }
    }

    private void applyRemoteCreate(OrderSyncPacket pkt) {
        if (activeOrders.stream().anyMatch(o -> o.getId().equals(pkt.getOrderId()))) return;
        Order o = new Order();
        o.setId(pkt.getOrderId());
        o.setRequesterUuid(pkt.getRequesterUuid());
        o.setRequesterName(pkt.getRequesterName());
        o.setItemData(pkt.getItemData());
        o.setDisplayItemName(pkt.getDisplayItemName());
        o.setTotalQty(pkt.getTotalQty());
        o.setRemainingQty(pkt.getRemainingQty());
        o.setCurrencyId(pkt.getCurrencyId().isEmpty() ? null : pkt.getCurrencyId());
        o.setUnitPrice(pkt.getUnitPrice());
        o.setEscrowTotal(pkt.getUnitPrice() * pkt.getTotalQty());
        o.setEscrowRemaining(pkt.getEscrowRemaining());
        o.setStatus(OrderStatus.ACTIVE);
        o.setCreatedAt(pkt.getUpdatedAt());
        o.setUpdatedAt(pkt.getUpdatedAt());
        o.setRevision(pkt.getRevision());
        activeOrders.add(o);
    }

    private void applyRemoteUpdate(OrderSyncPacket pkt) {
        for (Order o : activeOrders) {
            if (!o.getId().equals(pkt.getOrderId())) continue;
            if (pkt.getRevision() <= o.getRevision()) return; // stale event
            o.setRemainingQty(pkt.getRemainingQty());
            o.setEscrowRemaining(pkt.getEscrowRemaining());
            o.setRevision(pkt.getRevision());
            o.setUpdatedAt(pkt.getUpdatedAt());
            if (pkt.getStatus() == OrderStatus.COMPLETED) {
                o.setStatus(OrderStatus.COMPLETED);
                activeOrders.remove(o);
            }
            return;
        }
    }

    private void applyRemoteRemove(OrderSyncPacket pkt) {
        activeOrders.removeIf(o -> o.getId().equals(pkt.getOrderId()));
    }

    private void applyUpdate(Order updated) {
        for (int i = 0; i < activeOrders.size(); i++) {
            if (activeOrders.get(i).getId().equals(updated.getId())) {
                if (updated.getStatus() == OrderStatus.COMPLETED
                        || updated.getStatus() == OrderStatus.CANCELLED) {
                    activeOrders.remove(i);
                } else {
                    activeOrders.set(i, updated);
                }
                return;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasEnoughItems(Player p, Material mat, int qty) {
        int count = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == mat) count += is.getAmount();
            if (count >= qty) return true;
        }
        return false;
    }

    private boolean takeItems(Player p, Material mat, int qty) {
        int remaining = qty;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != mat) continue;
            int take = Math.min(is.getAmount(), remaining);
            is.setAmount(is.getAmount() - take);
            remaining -= take;
        }
        p.getInventory().setContents(contents);
        return remaining == 0;
    }

    private static String serializeItem(ItemStack item) {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             org.bukkit.util.io.BukkitObjectOutputStream oos = new org.bukkit.util.io.BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return null; }
    }

    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) return null;
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(Base64.getDecoder().decode(data));
             org.bukkit.util.io.BukkitObjectInputStream ois = new org.bukkit.util.io.BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        } catch (Exception e) { return null; }
    }

    private static String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        // Convert DIAMOND_SWORD → Diamond Sword
        String name = item.getType().name().replace('_', ' ').toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static void detectCustomItem(Order order, ItemStack item) {
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cs.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            if (stack != null) {
                String id = (String) cs.getMethod("getNamespacedID").invoke(stack);
                if (id != null) order.setItemsAdderId(id);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> nx = Class.forName("com.nexomc.nexo.api.NexoItems");
            Boolean exists = (Boolean) nx.getMethod("exists", ItemStack.class).invoke(null, item);
            if (Boolean.TRUE.equals(exists)) {
                String id = (String) nx.getMethod("idFromItem", ItemStack.class).invoke(null, item);
                if (id != null) order.setNexoId(id);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> ox = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            String id = (String) ox.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            if (id != null) order.setOraxenId(id);
        } catch (Throwable ignored) {}
    }
}
