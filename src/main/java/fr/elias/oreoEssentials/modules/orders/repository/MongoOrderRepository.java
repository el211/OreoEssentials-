package fr.elias.oreoEssentials.modules.orders.repository;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
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


public final class MongoOrderRepository implements OrderRepository {

    private final MongoCollection<Document> col;
    private final Logger log;

    public MongoOrderRepository(MongoClient client, String database, String collection, Logger log) {
        this.log = log;


        this.col = client.getDatabase(database)
                .getCollection(collection)
                .withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY);

        log.info("[Orders/Mongo] Collection pinned: db=" + database + " col=" + collection
                + " readPref=PRIMARY writeConcern=MAJORITY");

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
            Document doc = toDoc(order);

            com.mongodb.client.result.UpdateResult result = col.replaceOne(
                    Filters.eq("_id", order.getId()),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );


            boolean upserted  = result.getUpsertedId() != null;
            boolean matched   = result.getMatchedCount() > 0;
            boolean modified  = result.getModifiedCount() > 0;

            log.info("[Orders/Mongo] save() replaceOne for orderId=" + order.getId()
                    + " upserted=" + upserted
                    + " matched=" + matched
                    + " modified=" + modified
                    + " col=" + col.getNamespace().getFullName());

            Document verify = col.find(Filters.eq("_id", order.getId())).first();
            if (verify == null) {
                throw new IllegalStateException(
                        "save() verification failed: document " + order.getId()
                                + " not found immediately after upsert into "
                                + col.getNamespace().getFullName()
                                + " (upserted=" + upserted + " matched=" + matched + ")"
                                + " — this means the write went to a different namespace."
                );
            }
            log.info("[Orders/Mongo] save() verified: document " + order.getId()
                    + " confirmed in " + col.getNamespace().getFullName());
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
                long now  = System.currentTimeMillis();
                double pay = unitPrice * fillQty;


                Document subtractQty = new Document("$subtract", Arrays.asList(
                        new Document("$toLong", "$remainingQty"),
                        (long) fillQty
                ));

                Document setStage = new Document()
                        // new remainingQty = max(remainingQty - fillQty, 0)
                        .append("remainingQty",
                                new Document("$max", Arrays.asList(subtractQty, 0L)))
                        // new escrowRemaining = max(escrowRemaining - pay, 0)
                        .append("escrowRemaining",
                                new Document("$max", Arrays.asList(
                                        new Document("$subtract", Arrays.asList(
                                                new Document("$toDouble", "$escrowRemaining"),
                                                pay
                                        )), 0.0)))
                        // status = COMPLETED when remainingQty - fillQty <= 0, else keep current
                        .append("status",
                                new Document("$cond", Arrays.asList(
                                        new Document("$lte", Arrays.asList(subtractQty, 0L)),
                                        "COMPLETED",
                                        "$status")))
                        .append("revision",
                                new Document("$add", Arrays.asList("$revision", 1L)))
                        .append("updatedAt", now);

                Document pipeline = new Document("$set", setStage);

                FindOneAndUpdateOptions opts = new FindOneAndUpdateOptions()
                        .returnDocument(ReturnDocument.AFTER);

                Document result = col.findOneAndUpdate(
                        Filters.and(
                                Filters.eq("_id", orderId),
                                Filters.eq("status", "ACTIVE"),
                                Filters.gte("remainingQty", fillQty)
                        ),
                        List.of(pipeline),
                        opts
                );

                if (result == null) {
                    // Document either doesn't exist, or failed the status/qty guard.
                    Document existing = col.find(Filters.eq("_id", orderId)).first();
                    if (existing == null) {
                        log.warning("[Orders/Mongo] atomicFill: document not found for orderId=" + orderId
                                + " — the save() call likely failed silently before this revision.");
                        return FillResult.notFound();
                    }
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
                        .append("revision", new Document("$add", Arrays.asList("$revision", 1L)))
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
        // MongoClient lifecycle is managed by OreoEssentials, not by this repository.
        return CompletableFuture.completedFuture(null);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private Document toDoc(Order o) {
        Document doc = new Document("_id", o.getId())
                .append("requesterUuid",   o.getRequesterUuid().toString())
                .append("requesterName",   nvl(o.getRequesterName()))
                .append("itemData",        nvl(o.getItemData()))
                .append("displayItemName", nvl(o.getDisplayItemName()))
                .append("totalQty",        (long) o.getTotalQty())       // always Long for $subtract safety
                .append("remainingQty",    (long) o.getRemainingQty())   // always Long
                .append("unitPrice",       o.getUnitPrice())
                .append("escrowTotal",     o.getEscrowTotal())
                .append("escrowRemaining", o.getEscrowRemaining())
                .append("status",          o.getStatus().name())
                .append("createdAt",       o.getCreatedAt())
                .append("updatedAt",       o.getUpdatedAt())
                .append("revision",        (long) o.getRevision());      // always Long

        // Nullable fields — only written when present so fromDoc's containsKey check is consistent
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
        o.setTotalQty(getInt(doc, "totalQty"));
        o.setRemainingQty(getInt(doc, "remainingQty"));
        o.setUnitPrice(getDouble(doc, "unitPrice"));
        o.setEscrowTotal(getDouble(doc, "escrowTotal"));
        o.setEscrowRemaining(getDouble(doc, "escrowRemaining"));
        o.setStatus(OrderStatus.valueOf(doc.getString("status")));
        o.setCreatedAt(getLong(doc, "createdAt"));
        o.setUpdatedAt(getLong(doc, "updatedAt"));
        o.setRevision(getInt(doc, "revision"));
        if (doc.containsKey("currencyId"))   o.setCurrencyId(doc.getString("currencyId"));
        if (doc.containsKey("itemsAdderId")) o.setItemsAdderId(doc.getString("itemsAdderId"));
        if (doc.containsKey("nexoId"))       o.setNexoId(doc.getString("nexoId"));
        if (doc.containsKey("oraxenId"))     o.setOraxenId(doc.getString("oraxenId"));
        return o;
    }



    private static int getInt(Document doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.intValue();
        return 0;
    }

    private static long getLong(Document doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double getDouble(Document doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}