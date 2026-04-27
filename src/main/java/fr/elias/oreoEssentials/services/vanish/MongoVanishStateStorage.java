package fr.elias.oreoEssentials.services.vanish;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

public final class MongoVanishStateStorage implements VanishStateStorage {
    private final MongoCollection<Document> coll;

    public MongoVanishStateStorage(MongoClient client, String database, String prefix) {
        this.coll = client.getDatabase(database).getCollection(prefix + "vanish_state");
    }

    @Override
    public boolean isVanished(UUID playerId) {
        if (playerId == null) return false;
        Document doc = coll.find(eq("_id", playerId.toString())).first();
        return doc != null && doc.getBoolean("vanished", false);
    }

    @Override
    public void setVanished(UUID playerId, boolean vanished) {
        if (playerId == null) return;
        if (!vanished) {
            coll.deleteOne(eq("_id", playerId.toString()));
            return;
        }

        Document doc = new Document("_id", playerId.toString())
                .append("vanished", true)
                .append("updatedAt", System.currentTimeMillis());
        coll.replaceOne(eq("_id", playerId.toString()), doc, new ReplaceOptions().upsert(true));
    }
}
