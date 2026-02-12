package fr.elias.oreoEssentials.modules.chat.channels;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MongoDB-based persistence for player channel preferences
 * Enables cross-server channel memory - player stays in same channel across all servers
 */
public class MongoChannelPersistence implements ChannelPersistenceProvider {

    private final MongoCollection<Document> collection;

    public MongoChannelPersistence(MongoClient mongoClient, String databaseName, String collectionPrefix) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.collection = database.getCollection(collectionPrefix + "player_channels");

        // Create index on UUID for faster lookups
        try {
            collection.createIndex(new Document("uuid", 1));
            Bukkit.getLogger().info("[Channels] MongoDB persistence initialized (cross-server enabled)");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Channels] Failed to create MongoDB index: " + e.getMessage());
        }
    }

    @Override
    public Map<UUID, String> loadAll() {
        Map<UUID, String> data = new HashMap<>();

        try {
            for (Document doc : collection.find()) {
                try {
                    String uuidStr = doc.getString("uuid");
                    String channelId = doc.getString("channel");

                    if (uuidStr != null && channelId != null) {
                        UUID uuid = UUID.fromString(uuidStr);
                        data.put(uuid, channelId);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Channels] Failed to load channel data from MongoDB: " + e.getMessage());
        }

        return data;
    }

    @Override
    public void saveAll(Map<UUID, String> data) {
        // MongoDB saves individual entries immediately, so this is just a bulk sync
        for (Map.Entry<UUID, String> entry : data.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void save(UUID uuid, String channelId) {
        try {
            Document query = new Document("uuid", uuid.toString());
            Document update = new Document("$set", new Document()
                    .append("uuid", uuid.toString())
                    .append("channel", channelId)
                    .append("lastUpdated", System.currentTimeMillis())
            );

            collection.updateOne(query, update, new com.mongodb.client.model.UpdateOptions().upsert(true));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Channels] Failed to save channel for " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    public void remove(UUID uuid) {
        try {
            Document query = new Document("uuid", uuid.toString());
            collection.deleteOne(query);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Channels] Failed to remove channel for " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    public String get(UUID uuid) {
        try {
            Document query = new Document("uuid", uuid.toString());
            Document doc = collection.find(query).first();

            if (doc != null) {
                return doc.getString("channel");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Channels] Failed to get channel for " + uuid + ": " + e.getMessage());
        }

        return null;
    }
}