package fr.elias.oreoEssentials.modules.orders.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.orders.OrdersConfig;
import fr.elias.oreoEssentials.modules.orders.gui.OrdersGuiManager;
import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.model.OrderStatus;
import fr.elias.oreoEssentials.modules.orders.model.PendingDelivery;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrderSyncPacket;
import fr.elias.oreoEssentials.modules.orders.rabbitmq.OrdersEventBus;
import fr.elias.oreoEssentials.modules.orders.repository.OrderRepository;
import fr.elias.oreoEssentials.modules.orders.repository.PendingDeliveryRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;


public final class OrderService {

    private final OreoEssentials           plugin;
    private final OrdersConfig             cfg;
    private final OrderRepository          repo;
    private final OrderCurrencyAdapter     currency;
    private final OrdersEventBus           eventBus;
    private final PendingDeliveryRepository deliveryRepo;
    private final Logger                   log;
    private OrdersGuiManager               guiManager;

    public void setGuiManager(OrdersGuiManager guiManager) { this.guiManager = guiManager; }

    /** In-memory view of all ACTIVE orders — updated optimistically on write. */
    private final List<Order> activeOrders = new CopyOnWriteArrayList<>();

    public OrderService(OreoEssentials plugin, OrdersConfig cfg, OrderRepository repo,
                        OrderCurrencyAdapter currency, OrdersEventBus eventBus,
                        PendingDeliveryRepository deliveryRepo) {
        this.plugin       = plugin;
        this.cfg          = cfg;
        this.repo         = repo;
        this.currency     = currency;
        this.eventBus     = eventBus;
        this.deliveryRepo = deliveryRepo;
        this.log          = plugin.getLogger();
    }


    // ── Load ──────────────────────────────────────────────────────────────────

    public CompletableFuture<Void> loadActive() {
        return repo.loadActive().thenAccept(orders -> {
            activeOrders.clear();
            activeOrders.addAll(orders);
            log.info("[Orders] Loaded " + orders.size() + " active orders.");
        });
    }


    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new buy-request order. Withdraws escrow immediately.
     *
     * BUG A FIX: repo.save() now propagates DB exceptions (see MongoOrderRepository).
     * If save fails, we refund the escrow and return an error key — the order is
     * never added to activeOrders, so the GUI never shows a phantom entry.
     *
     * @return error message key on failure, null on success.
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

        double escrow      = unitPrice * qty;
        double fee         = cfg.feesEnabled() ? escrow * cfg.createFeePercent() / 100.0 : 0.0;
        double totalCharge = escrow + fee;

        if (cfg.debug()) {
            log.info("[Orders] createOrder: player=" + requester.getName()
                    + " item=" + item.getType()
                    + " qty=" + qty + " price=" + unitPrice
                    + " currency=" + currencyId
                    + " totalCharge=" + totalCharge);
        }

        return currency.getBalance(requester.getUniqueId(), currencyId)
                .thenCompose(balance -> {
                    if (balance < totalCharge) {
                        if (cfg.debug()) log.info("[Orders] createOrder DENIED — insufficient balance (" + balance + " < " + totalCharge + ")");
                        return CompletableFuture.completedFuture("errors.not-enough-money");
                    }

                    return currency.withdraw(requester.getUniqueId(), currencyId, totalCharge)
                            .thenCompose(ok -> {
                                if (!ok) {
                                    log.warning("[Orders] createOrder — withdraw failed for " + requester.getName());
                                    return CompletableFuture.completedFuture("errors.withdraw-failed");
                                }

                                String id       = UUID.randomUUID().toString();
                                String dispName = displayName(item);
                                Order  order    = new Order(id, requester.getUniqueId(), requester.getName(),
                                        itemData, dispName, qty, currencyId, unitPrice);
                                detectCustomItem(order, item);

                                // BUG A FIX: handle save() failure explicitly.
                                // If the DB write fails we refund the escrow and return an error key.
                                // The order is NOT added to activeOrders so no phantom GUI entry is created.
                                return repo.save(order)
                                        .thenApply(v -> {
                                            // Only reach here if save() succeeded
                                            activeOrders.add(order);
                                            eventBus.publishCreated(order);
                                            refreshGui();
                                            log.info("[Orders] Created order " + id
                                                    + " by " + requester.getName()
                                                    + " (" + qty + "x " + dispName + " @ " + unitPrice + ")");
                                            return (String) null; // success
                                        })
                                        .exceptionally(t -> {
                                            // DB write failed — refund the withdrawn escrow
                                            log.severe("[Orders] createOrder: save() failed for player "
                                                    + requester.getName() + " — refunding " + totalCharge
                                                    + " and aborting. Cause: " + t.getMessage());
                                            currency.deposit(requester.getUniqueId(), currencyId, totalCharge)
                                                    .thenAccept(refundOk -> {
                                                        if (!refundOk) {
                                                            log.severe("[Orders] createOrder: REFUND ALSO FAILED for "
                                                                    + requester.getName() + " amount=" + totalCharge
                                                                    + " — manual intervention required!");
                                                        } else if (cfg.debug()) {
                                                            log.info("[Orders] createOrder: escrow of " + totalCharge
                                                                    + " refunded to " + requester.getName() + " after DB failure.");
                                                        }
                                                    });
                                            return "errors.save-failed"; // caller will show this message
                                        });
                            });
                });
    }


    // ── Fill ──────────────────────────────────────────────────────────────────

    /**
     * Fills {@code fillQty} units of order {@code orderId}.
     *
     * Execution order (prevents rollback problems):
     *  1. Validate order is ACTIVE in local cache.
     *  2. On MAIN THREAD: verify + take items from filler's inventory.
     *  3. Execute atomicFill in storage.
     *  4. If atomicFill fails: give items back to filler, clean stale cache.
     *  5. If atomicFill succeeds: pay filler, deliver items to creator (or queue for later).
     *  6. Update cache, broadcast, refresh GUI.
     *
     * @return FillResult — check isSuccess() and getOutcome() for details.
     */
    public CompletableFuture<FillResult> fillOrder(Player filler, String orderId, int fillQty) {

        // ── Validate from local cache (fast path, optimistic) ─────────────────
        Order order = activeOrders.stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst().orElse(null);

        if (order == null) {
            if (cfg.debug()) log.info("[Orders] fillOrder: order " + orderId + " not in local cache");
            return CompletableFuture.completedFuture(FillResult.notFound());
        }
        if (order.getStatus() != OrderStatus.ACTIVE) {
            if (cfg.debug()) log.info("[Orders] fillOrder: order " + orderId + " is " + order.getStatus() + " locally");
            removeStaleOrder(orderId);
            return CompletableFuture.completedFuture(FillResult.alreadyClosed());
        }
        if (order.getRemainingQty() < fillQty) {
            if (cfg.debug()) log.info("[Orders] fillOrder: order " + orderId + " only has " + order.getRemainingQty() + " remaining (wanted " + fillQty + ")");
            return CompletableFuture.completedFuture(FillResult.insufficientQty());
        }
        if (order.getRequesterUuid().equals(filler.getUniqueId())) {
            return CompletableFuture.completedFuture(FillResult.error());
        }

        // ── Deserialize item template ─────────────────────────────────────────
        ItemStack template = deserializeItem(order.getItemData());
        if (template == null) {
            log.warning("[Orders] fillOrder: could not deserialize item for order " + orderId);
            return CompletableFuture.completedFuture(FillResult.error());
        }

        if (cfg.debug()) {
            log.info("[Orders] fillOrder: " + filler.getName()
                    + " attempting to fill " + fillQty + "x " + order.getDisplayItemName()
                    + " for order " + orderId);
        }

        // ── Step 2: Take items on main thread BEFORE the DB write ─────────────
        CompletableFuture<FillResult> result = new CompletableFuture<>();

        OreScheduler.run(plugin, () -> {

            if (!hasEnoughItems(filler, template, fillQty)) {
                log.info("[Orders] fillOrder: " + filler.getName() + " does not have enough items");
                result.complete(FillResult.insufficientQty());
                return;
            }

            if (!takeItems(filler, template, fillQty)) {
                log.warning("[Orders] fillOrder: takeItems() failed for " + filler.getName()
                        + " despite hasEnoughItems passing — concurrent inventory modification?");
                result.complete(FillResult.insufficientQty());
                return;
            }

            if (cfg.debug()) {
                log.info("[Orders] fillOrder: removed " + fillQty + "x items from "
                        + filler.getName() + "'s inventory");
            }

            // ── Step 3: Atomic fill in storage ────────────────────────────────
            repo.atomicFill(orderId, fillQty, order.getUnitPrice())
                    .thenAccept(fillResult -> {
                        if (!fillResult.isSuccess()) {
                            log.warning("[Orders] fillOrder: atomicFill returned "
                                    + fillResult.getOutcome() + " for order " + orderId
                                    + " — returning items to " + filler.getName());

                            OreScheduler.run(plugin, () ->
                                    giveOrDrop(filler, buildStack(template, fillQty)));

                            if (fillResult.getOutcome() == FillResult.Outcome.NOT_FOUND
                                    || fillResult.getOutcome() == FillResult.Outcome.ALREADY_CLOSED) {
                                removeStaleOrder(orderId);
                            }

                            result.complete(fillResult);
                            return;
                        }

                        // ── Step 4: Pay filler ────────────────────────────────
                        double gross   = fillResult.getPaidToSeller();
                        double fillFee = cfg.feesEnabled()
                                ? gross * cfg.fillFeePercent() / 100.0 : 0.0;
                        double netPay  = gross - fillFee;

                        if (cfg.debug()) {
                            log.info("[Orders] fillOrder: paying " + filler.getName()
                                    + " " + netPay + " (gross=" + gross + " fee=" + fillFee + ")");
                        }

                        currency.deposit(filler.getUniqueId(), order.getCurrencyId(), netPay)
                                .thenRun(() -> {
                                    if (cfg.debug()) {
                                        log.info("[Orders] fillOrder: payment of " + netPay
                                                + " deposited to " + filler.getName());
                                    }

                                    // ── Step 5: Deliver items to order creator ─
                                    deliverItemsToCreator(order, template, fillQty);

                                    // ── Step 6: Update cache + broadcast + GUI ─
                                    Order updated = fillResult.getUpdatedOrder();
                                    if (updated != null) applyUpdate(updated);
                                    eventBus.publishUpdated(updated != null ? updated : order);
                                    refreshGui();

                                    log.info("[Orders] fillOrder: SUCCESS — " + filler.getName()
                                            + " filled " + fillQty + "x " + order.getDisplayItemName()
                                            + " on order " + orderId
                                            + " (remaining=" + (updated != null ? updated.getRemainingQty() : "?") + ")");

                                    result.complete(fillResult);
                                });
                    });
        });

        return result;
    }

    /**
     * Delivers {@code fillQty} units of {@code template} to the order requester.
     * If they are online on this server, gives items directly (drops overflow).
     * Otherwise, persists a PendingDelivery row for the OrdersJoinListener to handle.
     */
    private void deliverItemsToCreator(Order order, ItemStack template, int fillQty) {
        UUID   creatorId = order.getRequesterUuid();
        Player creator   = Bukkit.getPlayer(creatorId);

        if (creator != null && creator.isOnline()) {
            ItemStack toGive = buildStack(template, fillQty);
            OreScheduler.run(plugin, () -> {
                giveOrDrop(creator, toGive);
                if (cfg.debug()) {
                    log.info("[Orders] deliverItemsToCreator: gave " + fillQty
                            + "x " + template.getType() + " to " + creator.getName()
                            + " (order=" + order.getId() + ")");
                }
            });
        } else {
            String deliveryId = UUID.randomUUID().toString();
            String itemData   = serializeItem(buildStack(template, 1));
            if (itemData == null) {
                log.severe("[Orders] deliverItemsToCreator: could not serialize item for pending delivery"
                        + " (orderId=" + order.getId() + ", player=" + creatorId + ")");
                return;
            }
            PendingDelivery delivery = new PendingDelivery(deliveryId, creatorId, itemData, fillQty, order.getId());
            deliveryRepo.save(delivery).exceptionally(t -> {
                log.severe("[Orders] deliverItemsToCreator: failed to persist pending delivery for "
                        + creatorId + " — items may be lost! Error: " + t.getMessage());
                return null;
            });
            log.info("[Orders] deliverItemsToCreator: queued " + fillQty
                    + "x " + template.getType() + " for offline/remote player " + creatorId
                    + " (deliveryId=" + deliveryId + ")");
        }
    }


    // ── Cancel ────────────────────────────────────────────────────────────────

    /**
     * Cancels the order and refunds remaining escrow to the requester.
     * @return true if cancelled AND refund succeeded; false otherwise.
     */
    public CompletableFuture<Boolean> cancelOrder(Player requester, String orderId) {

        Order order = activeOrders.stream()
                .filter(o -> o.getId().equals(orderId)
                        && o.getRequesterUuid().equals(requester.getUniqueId()))
                .findFirst().orElse(null);

        if (order == null) {
            if (cfg.debug()) log.info("[Orders] cancelOrder: order " + orderId + " not found in cache for " + requester.getName());
            return CompletableFuture.completedFuture(false);
        }

        if (cfg.debug()) {
            log.info("[Orders] cancelOrder: " + requester.getName()
                    + " attempting cancel of order " + orderId
                    + " (escrowRemaining=" + order.getEscrowRemaining() + ")");
        }

        return repo.atomicCancel(orderId, requester.getUniqueId())
                .thenCompose(ok -> {
                    if (!ok) {
                        log.warning("[Orders] cancelOrder: atomicCancel returned false for order "
                                + orderId + " — likely already closed. Cleaning stale cache.");
                        removeStaleOrder(orderId);
                        return CompletableFuture.completedFuture(false);
                    }

                    double refund     = order.getEscrowRemaining();
                    String currencyId = order.getCurrencyId();

                    removeStaleOrder(orderId);
                    order.setStatus(OrderStatus.CANCELLED);

                    eventBus.publishRemoved(order);
                    refreshGui();

                    log.info("[Orders] cancelOrder: order " + orderId
                            + " cancelled by " + requester.getName()
                            + " — refunding " + refund);

                    return currency.deposit(requester.getUniqueId(), currencyId, refund)
                            .thenApply(depositOk -> {
                                if (depositOk) {
                                    if (cfg.debug()) {
                                        log.info("[Orders] cancelOrder: refund of " + refund
                                                + " paid to " + requester.getName() + " — OK");
                                    }
                                } else {
                                    log.severe("[Orders] cancelOrder: REFUND FAILED for order "
                                            + orderId + ", player=" + requester.getName()
                                            + ", amount=" + refund
                                            + " — manual intervention required!");
                                }
                                return depositOk;
                            });
                });
    }


    // ── Queries ───────────────────────────────────────────────────────────────

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


    // ── Cross-server event application ────────────────────────────────────────

    public void applyIncomingEvent(OrderSyncPacket pkt) {
        if (pkt == null) return;
        if (cfg.debug()) log.info("[Orders] applyIncomingEvent: type=" + pkt.getType()
                + " orderId=" + pkt.getOrderId() + " from=" + pkt.getServerId());
        switch (pkt.getType()) {
            case ORDER_CREATED -> applyRemoteCreate(pkt);
            case ORDER_UPDATED -> applyRemoteUpdate(pkt);
            case ORDER_REMOVED -> applyRemoteRemove(pkt);
        }
    }

    private void applyRemoteCreate(OrderSyncPacket pkt) {
        if (activeOrders.stream().anyMatch(o -> o.getId().equals(pkt.getOrderId()))) {
            if (cfg.debug()) log.info("[Orders] applyRemoteCreate: already have " + pkt.getOrderId());
            return;
        }
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
        if (cfg.debug()) log.info("[Orders] applyRemoteCreate: added " + pkt.getOrderId());
    }

    private void applyRemoteUpdate(OrderSyncPacket pkt) {
        for (Order o : activeOrders) {
            if (!o.getId().equals(pkt.getOrderId())) continue;
            if (pkt.getRevision() <= o.getRevision()) {
                if (cfg.debug()) log.info("[Orders] applyRemoteUpdate: stale event for " + pkt.getOrderId()
                        + " (local rev=" + o.getRevision() + ", pkt rev=" + pkt.getRevision() + ")");
                return;
            }
            o.setRemainingQty(pkt.getRemainingQty());
            o.setEscrowRemaining(pkt.getEscrowRemaining());
            o.setRevision(pkt.getRevision());
            o.setUpdatedAt(pkt.getUpdatedAt());
            o.setStatus(pkt.getStatus());

            if (pkt.getStatus() == OrderStatus.COMPLETED
                    || pkt.getStatus() == OrderStatus.CANCELLED) {
                activeOrders.remove(o);
                if (cfg.debug()) log.info("[Orders] applyRemoteUpdate: removed " + pkt.getOrderId()
                        + " (status=" + pkt.getStatus() + ")");
            } else {
                if (cfg.debug()) log.info("[Orders] applyRemoteUpdate: updated " + pkt.getOrderId()
                        + " remainingQty=" + pkt.getRemainingQty());
            }
            return;
        }
        if (cfg.debug()) log.info("[Orders] applyRemoteUpdate: order " + pkt.getOrderId() + " not in cache, ignoring");
    }

    private void applyRemoteRemove(OrderSyncPacket pkt) {
        boolean removed = activeOrders.removeIf(o -> o.getId().equals(pkt.getOrderId()));
        if (cfg.debug()) log.info("[Orders] applyRemoteRemove: order=" + pkt.getOrderId()
                + " removed=" + removed);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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

    private void removeStaleOrder(String orderId) {
        boolean removed = activeOrders.removeIf(o -> o.getId().equals(orderId));
        if (removed) {
            if (cfg.debug()) log.info("[Orders] removeStaleOrder: purged " + orderId + " from cache");
            refreshGui();
        }
    }

    private void refreshGui() {
        if (guiManager == null) return;
        if (Bukkit.isPrimaryThread()) {
            guiManager.scheduleRefreshAll();
        } else {
            OreScheduler.run(plugin, guiManager::scheduleRefreshAll);
        }
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private boolean hasEnoughItems(Player p, ItemStack template, int qty) {
        int count = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && itemMatches(is, template)) count += is.getAmount();
            if (count >= qty) return true;
        }
        return false;
    }

    private boolean takeItems(Player p, ItemStack template, int qty) {
        if (!hasEnoughItems(p, template, qty)) return false;
        int remaining = qty;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || !itemMatches(is, template)) continue;
            int take = Math.min(is.getAmount(), remaining);
            is.setAmount(is.getAmount() - take);
            remaining -= take;
        }
        p.getInventory().setContents(contents);
        return remaining == 0;
    }

    private boolean itemMatches(ItemStack candidate, ItemStack template) {
        if (candidate.getType() != template.getType()) return false;
        if (template.hasItemMeta()) {
            return template.getItemMeta().equals(candidate.getItemMeta());
        }
        return true;
    }

    private static ItemStack buildStack(ItemStack template, int qty) {
        ItemStack s = template.clone();
        s.setAmount(Math.min(qty, template.getMaxStackSize()));
        return s;
    }

    public static void giveOrDrop(Player player, ItemStack item) {
        if (item == null || player == null) return;
        int remaining = item.getAmount();
        int maxStack  = item.getType().getMaxStackSize();
        while (remaining > 0) {
            ItemStack stack = item.clone();
            stack.setAmount(Math.min(remaining, maxStack));
            remaining -= stack.getAmount();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }


    // ── Serialization ─────────────────────────────────────────────────────────

    private static String serializeItem(ItemStack item) {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             org.bukkit.util.io.BukkitObjectOutputStream oos =
                     new org.bukkit.util.io.BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return null; }
    }

    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) return null;
        try (java.io.ByteArrayInputStream bais =
                     new java.io.ByteArrayInputStream(Base64.getDecoder().decode(data));
             org.bukkit.util.io.BukkitObjectInputStream ois =
                     new org.bukkit.util.io.BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        } catch (Exception e) { return null; }
    }

    private static String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
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