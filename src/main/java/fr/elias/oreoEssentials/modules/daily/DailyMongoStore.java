package fr.elias.oreoEssentials.modules.daily;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import fr.elias.oreoEssentials.OreoEssentials;
import org.bson.Document;

import java.time.LocalDate;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

public final class DailyMongoStore {

    public static final class Record {
        public UUID uuid;
        public String name;
        public int streak;
        public long lastClaimEpochDay;
        public int totalClaims;

        public LocalDate lastClaimDate() {
            return lastClaimEpochDay <= 0 ? null : LocalDate.ofEpochDay(lastClaimEpochDay);
        }
    }

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private MongoClient client;
    private MongoCollection<Document> col;
    private boolean connected = false;

    public DailyMongoStore(OreoEssentials plugin, DailyConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public void connect() {
        if (!cfg.mongo.enabled) {
            plugin.getLogger().info("[Daily] MongoDB storage is disabled. Using file-based storage.");
            connected = false;
            return;
        }

        try {
            client = MongoClients.create(cfg.mongo.uri);
            MongoDatabase db = client.getDatabase(cfg.mongo.database);
            col = db.getCollection(cfg.mongo.collection);
            connected = true;
            plugin.getLogger().info("[Daily] Connected to MongoDB " + cfg.mongo.database + "." + cfg.mongo.collection);
        } catch (Exception e) {
            plugin.getLogger().severe("[Daily] Failed to connect to MongoDB: " + e.getMessage());
            plugin.getLogger().severe("[Daily] Falling back to file-based storage.");
            connected = false;
        }
    }

    public void close() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Throwable ignored) {}
    }

    public boolean isConnected() {
        return connected && col != null;
    }

    public Record get(UUID uuid) {
        if (!isConnected()) {
            throw new IllegalStateException("MongoDB is not connected. Use file-based storage instead.");
        }

        try {
            Document d = col.find(eq("_id", uuid.toString())).first();
            if (d == null) return null;

            Record r = new Record();
            r.uuid = uuid;
            r.name = d.getString("name");
            r.streak = d.getInteger("streak", 0);
            r.lastClaimEpochDay = d.getLong("lastClaimEpochDay") == null ? 0L : d.getLong("lastClaimEpochDay");
            r.totalClaims = d.getInteger("totalClaims", 0);
            return r;
        } catch (Exception e) {
            plugin.getLogger().severe("[Daily] Error reading from MongoDB: " + e.getMessage());
            return null;
        }
    }

    public Record ensure(UUID uuid, String name) {
        if (!isConnected()) {
            throw new IllegalStateException("MongoDB is not connected. Use file-based storage instead.");
        }

        try {
            Record r = get(uuid);
            if (r != null) return r;

            Document d = new Document("_id", uuid.toString())
                    .append("name", name)
                    .append("streak", 0)
                    .append("lastClaimEpochDay", 0L)
                    .append("totalClaims", 0);
            col.insertOne(d);

            r = new Record();
            r.uuid = uuid;
            r.name = name;
            r.streak = 0;
            r.lastClaimEpochDay = 0;
            r.totalClaims = 0;
            return r;
        } catch (Exception e) {
            plugin.getLogger().severe("[Daily] Error ensuring record in MongoDB: " + e.getMessage());
            return null;
        }
    }

    public void updateOnClaim(UUID uuid, String name, int newStreak, LocalDate date) {
        if (!isConnected()) {
            throw new IllegalStateException("MongoDB is not connected. Use file-based storage instead.");
        }

        try {
            col.updateOne(eq("_id", uuid.toString()),
                    combine(
                            set("name", name),
                            set("streak", newStreak),
                            set("lastClaimEpochDay", date.toEpochDay()),
                            inc("totalClaims", 1)
                    ),
                    new com.mongodb.client.model.UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            plugin.getLogger().severe("[Daily] Error updating claim in MongoDB: " + e.getMessage());
        }
    }

    public void resetStreak(UUID uuid) {
        if (!isConnected()) {
            throw new IllegalStateException("MongoDB is not connected. Use file-based storage instead.");
        }

        try {
            col.updateOne(eq("_id", uuid.toString()),
                    combine(set("streak", 0), set("lastClaimEpochDay", 0L)));
        } catch (Exception e) {
            plugin.getLogger().severe("[Daily] Error resetting streak in MongoDB: " + e.getMessage());
        }
    }
}