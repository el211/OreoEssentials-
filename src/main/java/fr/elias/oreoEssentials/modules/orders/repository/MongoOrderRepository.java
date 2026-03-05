package fr.elias.oreoEssentials.modules.orders.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import fr.elias.oreoEssentials.modules.orders.model.FillResult;
import fr.elias.oreoEssentials.modules.orders.model.Order;
import fr.elias.oreoEssentials.modules.orders.model.OrderStatus;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * MongoDB-backed repository.
 * Uses aggregation-pipeline findOneAndUpdate (MongoDB 4.2+) for atomic fills/cancels,
 * ensuring correctness even when multiple servers race.
 */
public final class MongoOrderRepository implements OrderRepository {

    private final MongoCollection<Document> col;
    private final Logger log;

    public MongoOrderRepository(MongoClient client, String database, String collection, Logger log) {
        this.col = client.getDatabase(database).getCollection(collection);
        this.log = log;

        // Ensure indexes
        try {
            col.createIndex(new Document("status", 1));
            col.createIndex(new Document("requesterUuid", 1).append("status", 1));
        } catch (Exception e) {
            log.warning("[Orders/Mongo] Index creation warning: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> save(Order order) {
        return CompletableFuture.runAsync(() -> {
            try {
                Document doc = toDoc(order);
                col.replaceOne(Filters.eq("_id", order.getId()), doc,
                        new com.mongodb.client.model.ReplaceOptions().upsert(true));
            } catch (Exception e) {
                log.severe("[Orders/Mongo] save() failed: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<Order> list = new ArrayList<>();
            try {
                for (Document doc : col.find()) {
                    try { list.add(fromDoc(doc)); }
                    catch (Exception e) { log.warning("[Orders/Mongo] Skipping corrupt doc: " + e.getMessage()); }
                }
            } catch (Exception e) {
                log.severe("[Orders/Mongo] loadAll() failed: " + e.getMessage());
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<List<Order>> loadActive() {
        return CompletableFuture.supplyAsync(() -> {
            List<Order> list = new ArrayList<>();
            try {
                for (Document doc : col.find(Filters.eq("status", "ACTIVE"))) {
                    try { list.add(fromDoc(doc)); }
                    catch (Exception e) { log.warning("[Orders/Mongo] Skipping corrupt doc: " + e.getMessage()); }
                }
            } catch (Exception e) {
                log.severe("[Orders/Mongo] loadActive() failed: " + e.getMessage());
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<List<Order>> loadByRequester(UUID requesterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Order> list = new ArrayList<>();
            try {
                for (Document doc : col.find(Filters.eq("requesterUuid", requesterUuid.toString()))) {
                    try { list.add(fromDoc(doc)); }
                    catch (Exception e) { log.warning("[Orders/Mongo] Skipping corrupt doc: " + e.getMessage()); }
                }
            } catch (Exception e) {
                log.severe("[Orders/Mongo] loadByRequester() failed: " + e.getMessage());
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<FillResult> atomicFill(String orderId, int fillQty, double unitPrice) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                double pay = unitPrice * fillQty;

                /*
                 * Aggregation-pipeline update (MongoDB 4.2+):
                 * - Decrements remainingQty by fillQty
                 * - Decrements escrowRemaining by pay
                 * - Sets status to COMPLETED when remainingQty reaches 0
                 * - Increments revision
                 * All in a single atomic operation on the server.
                 */
                List<Document> pipeline = List.of(new Document("$set", new Document()
                        .append("remainingQty", new Document("$subtract",
                                Arrays.asList("$remainingQty", fillQty)))
                        .append("escrowRemaining", new Document("$subtract",
                                Arrays.asList("$escrowRemaining", pay)))
                        .append("status", new Document("$cond", Arrays.asList(
                                new Document("$lte", Arrays.asList(
                                        new Document("$subtract", Arrays.asList("$remainingQty", fillQty)), 0)),
                                "COMPLETED",
                                "$status")))
                        .append("revision", new Document("$add", Arrays.asList("$revision", 1)))
                        .append("updatedAt", now)
                ));

                FindOneAndUpdateOptions opts = new FindOneAndUpdateOptions()
                        .returnDocument(ReturnDocument.AFTER);

                Document result = col.findOneAndUpdate(
                        Filters.and(
                                Filters.eq("_id", orderId),
                                Filters.eq("status", "ACTIVE"),
                                Filters.gte("remainingQty", fillQty)
                        ),
                        pipeline,
                        opts
                );

                if (result == null) {
                    // Check why — not found vs wrong status/qty
                    Document existing = col.find(Filters.eq("_id", orderId)).first();
                    if (existing == null) return FillResult.notFound();
                    String st = existing.getString("status");
                    if (!"ACTIVE".equals(st)) return FillResult.alreadyClosed();
                    return FillResult.insufficientQty();
                }

                Order updated = fromDoc(result);
                return FillResult.success(fillQty, pay, updated);

            } catch (Exception e) {
                log.severe("[Orders/Mongo] atomicFill() failed: " + e.getMessage());
                return FillResult.error();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> atomicCancel(String orderId, UUID requesterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                List<Document> pipeline = List.of(new Document("$set", new Document()
                        .append("status", "CANCELLED")
                        .append("revision", new Document("$add", Arrays.asList("$revision", 1)))
                        .append("updatedAt", now)
                ));
                Document result = col.findOneAndUpdate(
                        Filters.and(
                                Filters.eq("_id", orderId),
                                Filters.eq("requesterUuid", requesterUuid.toString()),
                                Filters.eq("status", "ACTIVE")
                        ),
                        pipeline,
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                );
                return result != null;
            } catch (Exception e) {
                log.severe("[Orders/Mongo] atomicCancel() failed: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Document toDoc(Order o) {
        Document doc = new Document("_id", o.getId())
                .append("requesterUuid",   o.getRequesterUuid().toString())
                .append("requesterName",   o.getRequesterName())
                .append("itemData",        o.getItemData())
                .append("displayItemName", nvl(o.getDisplayItemName()))
                .append("totalQty",        o.getTotalQty())
                .append("remainingQty",    o.getRemainingQty())
                .append("unitPrice",       o.getUnitPrice())
                .append("escrowTotal",     o.getEscrowTotal())
                .append("escrowRemaining", o.getEscrowRemaining())
                .append("status",          o.getStatus().name())
                .append("createdAt",       o.getCreatedAt())
                .append("updatedAt",       o.getUpdatedAt())
                .append("revision",        o.getRevision());

        if (o.getCurrencyId()   != null) doc.append("currencyId",   o.getCurrencyId());
        if (o.getItemsAdderId() != null) doc.append("itemsAdderId", o.getItemsAdderId());
        if (o.getNexoId()       != null) doc.append("nexoId",       o.getNexoId());
        if (o.getOraxenId()     != null) doc.append("oraxenId",     o.getOraxenId());
        return doc;
    }

    private Order fromDoc(Document doc) {
        Order o = new Order();
        o.setId(doc.getString("_id"));
        o.setRequesterUuid(UUID.fromString(doc.getString("requesterUuid")));
        o.setRequesterName(doc.getString("requesterName"));
        o.setItemData(doc.getString("itemData"));
        o.setDisplayItemName(doc.getString("displayItemName"));
        o.setTotalQty(doc.getInteger("totalQty", 0));
        o.setRemainingQty(doc.getInteger("remainingQty", 0));
        o.setUnitPrice(doc.getDouble("unitPrice"));
        o.setEscrowTotal(doc.getDouble("escrowTotal"));
        o.setEscrowRemaining(doc.getDouble("escrowRemaining"));
        o.setStatus(OrderStatus.valueOf(doc.getString("status")));
        o.setCreatedAt(doc.getLong("createdAt"));
        o.setUpdatedAt(doc.getLong("updatedAt"));
        o.setRevision(doc.getInteger("revision", 0));
        if (doc.containsKey("currencyId"))   o.setCurrencyId(doc.getString("currencyId"));
        if (doc.containsKey("itemsAdderId")) o.setItemsAdderId(doc.getString("itemsAdderId"));
        if (doc.containsKey("nexoId"))       o.setNexoId(doc.getString("nexoId"));
        if (doc.containsKey("oraxenId"))     o.setOraxenId(doc.getString("oraxenId"));
        return o;
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
