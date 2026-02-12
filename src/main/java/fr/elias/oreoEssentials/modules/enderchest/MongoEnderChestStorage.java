package fr.elias.oreoEssentials.modules.enderchest;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Logger;

public class MongoEnderChestStorage implements EnderChestStorage {

    private final Logger log;
    private final MongoCollection<Document> coll;

    public MongoEnderChestStorage(MongoClient client, String dbName, String prefix, Logger log) {
        this.log = log;
        this.coll = client.getDatabase(dbName).getCollection(prefix + "enderchest");
        this.log.info("[EC] MongoEnderChestStorage bound to " + dbName + "." + prefix + "enderchest");
    }

    @Override
    public ItemStack[] load(UUID playerId, int rows) {
        try {
            Document doc = coll.find(Filters.eq("_id", playerId.toString())).first();
            if (doc == null) return null;
            String data = doc.getString("data");
            ItemStack[] stored = EnderChestStorage.deserialize(data);
            return EnderChestStorage.clamp(stored, rows);
        } catch (Exception e) {
            log.warning("[EC] Mongo load failed for " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void save(UUID playerId, int rows, ItemStack[] contents) {
        try {
            ItemStack[] clipped = EnderChestStorage.clamp(contents, rows);
            String data = EnderChestStorage.serialize(clipped);
            Document doc = new Document("_id", playerId.toString())
                    .append("rows", rows)
                    .append("data", data)
                    .append("updatedAt", System.currentTimeMillis());
            coll.replaceOne(Filters.eq("_id", playerId.toString()), doc, new ReplaceOptions().upsert(true));
        } catch (Exception e) {
            log.warning("[EC] Mongo save failed for " + playerId + ": " + e.getMessage());
        }
    }
}
