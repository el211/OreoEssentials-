package fr.elias.oreoEssentials.modules.auctionhouse.storage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoClient;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionStatus;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.ItemSerializer;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;

public final class MongoAuctionStorage implements AuctionStorage {

    private final MongoCollection<Document> col;
    private final Logger logger;

    public MongoAuctionStorage(MongoClient client, String database, String collection, Logger logger) {
        this.col = client.getDatabase(database).getCollection(collection);
        this.logger = logger;
    }

    @Override
    public AuctionSnapshot loadAll() {
        List<Auction> active  = new ArrayList<>();
        List<Auction> expired = new ArrayList<>();
        List<Auction> sold    = new ArrayList<>();

        try {
            for (Document doc : col.find()) {
                try {
                    Auction a = fromDoc(doc);
                    switch (a.getStatus()) {
                        case ACTIVE    -> active.add(a);
                        case EXPIRED   -> expired.add(a);
                        case SOLD      -> sold.add(a);
                        case CANCELLED -> expired.add(a); // treat cancelled like expired
                    }
                } catch (Exception e) {
                    logger.warning("[AuctionHouse] Skipping corrupt Mongo doc: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.severe("[AuctionHouse] Failed to load from MongoDB: " + e.getMessage());
        }
        return new AuctionSnapshot(active, expired, sold);
    }

    @Override
    public synchronized void saveAll(AuctionSnapshot snap) {
        try {
            col.deleteMany(new Document());

            List<Document> docs = new ArrayList<>();
            for (Auction a : snap.active())  docs.add(toDoc(a));
            for (Auction a : snap.expired()) docs.add(toDoc(a));
            for (Auction a : snap.sold())    docs.add(toDoc(a));

            if (!docs.isEmpty()) col.insertMany(docs);
        } catch (Exception e) {
            logger.severe("[AuctionHouse] Failed to save to MongoDB: " + e.getMessage());
        }
    }


    private Document toDoc(Auction a) {
        Document doc = new Document("_id", a.getId())
                .append("seller",         a.getSeller().toString())
                .append("sellerName",     a.getSellerName())
                .append("item",           ItemSerializer.serialize(a.getItem()))
                .append("price",          a.getPrice())
                .append("listedTime",     a.getListedTime())
                .append("expirationTime", a.getExpirationTime())
                .append("category",       a.getCategory().name())
                .append("status",         a.getStatus().name());

        if (a.getBuyer() != null) {
            doc.append("buyer",     a.getBuyer().toString())
                    .append("buyerName", a.getBuyerName())
                    .append("soldTime",  a.getSoldTime());
        }
        if (a.getItemsAdderID()    != null) doc.append("itemsAdderID",    a.getItemsAdderID());
        if (a.getNexoID()          != null) doc.append("nexoID",          a.getNexoID());
        if (a.getOraxenID()        != null) doc.append("oraxenID",        a.getOraxenID());
        if (a.getCustomModelData() != null) doc.append("customModelData", a.getCustomModelData());
        if (a.getCurrencyId()      != null) doc.append("currencyId",      a.getCurrencyId());
        return doc;
    }

    private Auction fromDoc(Document doc) {
        String id           = doc.getString("_id");
        UUID seller         = UUID.fromString(doc.getString("seller"));
        String sellerName   = doc.getString("sellerName");
        ItemStack item      = ItemSerializer.deserialize(doc.getString("item"));
        double price        = doc.getDouble("price");
        long listedTime     = doc.getLong("listedTime");
        long expirationTime = doc.getLong("expirationTime");
        AuctionCategory cat = AuctionCategory.valueOf(doc.getString("category"));
        AuctionStatus st    = AuctionStatus.valueOf(doc.getString("status"));
        UUID buyer          = doc.containsKey("buyer") ? UUID.fromString(doc.getString("buyer")) : null;
        String buyerName    = doc.getString("buyerName");
        long soldTime       = doc.containsKey("soldTime") ? doc.getLong("soldTime") : 0;

        Auction a = new Auction(id, seller, sellerName, item, price,
                listedTime, expirationTime, cat, st, buyer, buyerName, soldTime);

        if (doc.containsKey("itemsAdderID"))    a.setItemsAdderID(doc.getString("itemsAdderID"));
        if (doc.containsKey("nexoID"))          a.setNexoID(doc.getString("nexoID"));
        if (doc.containsKey("oraxenID"))        a.setOraxenID(doc.getString("oraxenID"));
        if (doc.containsKey("customModelData")) a.setCustomModelData(doc.getInteger("customModelData"));
        if (doc.containsKey("currencyId"))      a.setCurrencyId(doc.getString("currencyId"));
        return a;
    }
}