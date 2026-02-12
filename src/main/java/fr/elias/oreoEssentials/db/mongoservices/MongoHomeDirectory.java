package fr.elias.oreoEssentials.db.mongoservices;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;
import org.bson.Document;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MongoHomeDirectory implements HomeDirectory {
    private final MongoCollection<Document> col;

    public MongoHomeDirectory(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);
        col.createIndex(Indexes.ascending("uuid", "name"), new IndexOptions().unique(true));
    }

    private static String keyName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void setHomeServer(UUID uuid, String name, String server) {
        String n = keyName(name);
        Document key = new Document("uuid", uuid.toString()).append("name", n);
        Document doc = new Document(key).append("server", server);
        col.replaceOne(key, doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public String getHomeServer(UUID uuid, String name) {
        String n = keyName(name);
        Document d = col.find(Filters.and(
                Filters.eq("uuid", uuid.toString()),
                Filters.eq("name", n)
        )).projection(new Document("server", 1)).first();
        return d == null ? null : d.getString("server");
    }

    @Override
    public Set<String> listHomes(UUID uuid) {
        Set<String> homes = new HashSet<>();
        try (MongoCursor<Document> cursor = col.find(Filters.eq("uuid", uuid.toString())).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                String name = doc.getString("name");
                if (name != null && !name.isBlank()) {
                    homes.add(name);
                }
            }
        }
        return homes;
    }

    @Override
    public void deleteHome(UUID uuid, String name) {
        String n = keyName(name);
        col.deleteOne(Filters.and(
                Filters.eq("uuid", uuid.toString()),
                Filters.eq("name", n)
        ));
    }
}