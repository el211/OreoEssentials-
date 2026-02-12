package fr.elias.oreoEssentials.db.mongoservices;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import fr.elias.oreoEssentials.modules.spawn.SpawnDirectory;
import org.bson.Document;

public class MongoSpawnDirectory implements SpawnDirectory {
    private final MongoCollection<Document> col;

    public MongoSpawnDirectory(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);
    }

    @Override
    public void setSpawnServer(String server) {
        var key = new Document("type", "spawn_owner");
        var val = new Document("type", "spawn_owner").append("server", server);
        col.replaceOne(key, val, new ReplaceOptions().upsert(true));
    }

    @Override
    public String getSpawnServer() {
        var d = col.find(new Document("type", "spawn_owner"))
                .projection(new Document("server", 1)).first();
        return d == null ? null : d.getString("server");
    }
}
