package fr.elias.oreoEssentials.playersync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.UUID;

public final class MongoPlayerSyncStorage implements PlayerSyncStorage {
    private final MongoCollection<Document> coll;

    public MongoPlayerSyncStorage(MongoClient client, String db, String prefix) {
        this.coll = client.getDatabase(db).getCollection(prefix + "player_sync");
    }

    @Override
    public void save(UUID uuid, PlayerSyncSnapshot snap) throws Exception {
        Document doc = new Document("_id", uuid.toString())
                .append("data", PlayerSyncSnapshot.toBase64(snap))
                .append("ts", System.currentTimeMillis());
        coll.replaceOne(Filters.eq("_id", uuid.toString()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public PlayerSyncSnapshot load(UUID uuid) throws Exception {
        Document d = coll.find(Filters.eq("_id", uuid.toString())).first();
        if (d == null) return null;
        String base64 = d.getString("data");
        if (base64 == null) return null;
        return PlayerSyncSnapshot.fromBase64(base64);
    }
}
