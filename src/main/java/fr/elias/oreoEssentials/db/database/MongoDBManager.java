package fr.elias.oreoEssentials.db.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MongoDBManager implements PlayerEconomyDatabase {
    private static final long LOCAL_BALANCE_CACHE_TTL_MS = 5000L;

    private static final class CachedBalance {
        private final double balance;
        private final long expiresAtMillis;

        private CachedBalance(double balance, long expiresAtMillis) {
            this.balance = balance;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final RedisManager redis;
    private final Map<UUID, CachedBalance> localBalanceCache = new ConcurrentHashMap<>();
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private volatile boolean connected = false;

    private static final double STARTING_BALANCE = 100.0;
    private static final double MIN_BALANCE = 0.0;
    private final double MAX_BALANCE;
    private static final boolean ALLOW_NEGATIVE = false;

    public MongoDBManager(OreoEssentials plugin, RedisManager redis)
    {
        this.redis = redis;
        this.MAX_BALANCE = plugin.getConfig().getDouble("economy.max-balance", 1_000_000_000.0);

    }

    @Override
    public boolean connect(String uri, String database, String collection) {
        try {
            this.mongoClient = MongoClients.create(uri);
            this.database = mongoClient.getDatabase(database);
            this.collection = this.database.getCollection(collection);

            this.collection.createIndex(
                    Indexes.ascending("playerUUID"),
                    new IndexOptions().unique(true)
            );

            this.connected = true;
            System.out.println("[OreoEssentials]  Connected to MongoDB: " + database);
            return true;
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ MongoDB connection failed — economy features disabled.");
            System.err.println("[OreoEssentials]    Check your settings.yml (mongodb.uri / database / collection) and make sure the MongoDB server is reachable.");
            System.err.println("[OreoEssentials]    Cause: " + e.getMessage());
            return false;
        }
    }

    /** Returns true if MongoDB is connected and usable. */
    public boolean isConnected() { return connected; }

    @Override
    public void giveBalance(UUID playerUUID, String name, double amount) {
        if (!connected) return;
        final String id = playerUUID.toString();
        try {
            collection.updateOne(
                    Filters.eq("playerUUID", id),
                    Updates.combine(
                            Updates.set("playerUUID", id),
                            Updates.set("name", name),
                            Updates.inc("balance", amount)
                    ),
                    new UpdateOptions().upsert(true)
            );
            localBalanceCache.remove(playerUUID);
            redis.deleteBalance(playerUUID);
            cacheBalance(playerUUID, getBalance(playerUUID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void takeBalance(UUID playerUUID, String name, double amount) {
        double current = getBalance(playerUUID);
        double target = current - amount;
        setBalance(playerUUID, name, target);
    }

    @Override
    public void setBalance(UUID playerUUID, String name, double amount) {
        if (!connected) return;
        final String id = playerUUID.toString();
        double clamped = clamp(amount, MIN_BALANCE, MAX_BALANCE, ALLOW_NEGATIVE);

        Document doc = new Document("playerUUID", id)
                .append("name", name)
                .append("balance", clamped);

        try {
            collection.replaceOne(Filters.eq("playerUUID", id), doc, new ReplaceOptions().upsert(true));
            cacheBalance(playerUUID, clamped);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        if (!connected) return STARTING_BALANCE;
        Double local = getLocalBalance(playerUUID);
        if (local != null) return local;

        Double cached = redis.getBalance(playerUUID);
        if (cached != null) {
            cacheBalance(playerUUID, cached);
            return cached;
        }

        Document doc = collection.find(Filters.eq("playerUUID", playerUUID.toString())).first();
        if (doc != null) {
            double balance = readNumber(doc, "balance", STARTING_BALANCE);
            cacheBalance(playerUUID, balance);
            return balance;
        }
        return STARTING_BALANCE;
    }

    @Override
    public double getOrCreateBalance(UUID playerUUID, String name) {
        if (!connected) return STARTING_BALANCE;
        Double local = getLocalBalance(playerUUID);
        if (local != null) return local;

        Double cached = redis.getBalance(playerUUID);
        if (cached != null) {
            cacheBalance(playerUUID, cached);
            return cached;
        }

        Document doc = collection.find(Filters.eq("playerUUID", playerUUID.toString())).first();
        if (doc != null) {
            double balance = readNumber(doc, "balance", STARTING_BALANCE);
            cacheBalance(playerUUID, balance);
            return balance;
        }

        setBalance(playerUUID, name, STARTING_BALANCE);
        return STARTING_BALANCE;
    }

    @Override
    public void populateCache(OfflinePlayerCache cache) {
        if (!connected) return;
        for (Document doc : collection.find()) {
            try {
                String s = doc.getString("playerUUID");
                if (s == null) continue;
                UUID id = UUID.fromString(s);
                String name = doc.getString("name");
                if (name == null) name = Bukkit.getOfflinePlayer(id).getName();
                if (name != null) cache.add(name, id);
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void clearCache() {
        localBalanceCache.clear();
        redis.clearCache();
    }

    @Override
    public void close() {
        localBalanceCache.clear();
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("[OreoEssentials] MongoDB connection closed.");
        }
    }


    @Override
    public boolean supportsLeaderboard() { return true; }

    @Override
    public List<TopEntry> topBalances(int limit) {
        if (!connected) return new ArrayList<>();
        List<TopEntry> out = new ArrayList<>();
        int lim = Math.max(1, limit);

        try {
            for (Document d : collection.find()
                    .projection(new Document("playerUUID", 1).append("name", 1).append("balance", 1))
                    .sort(Sorts.descending("balance"))
                    .limit(lim)) {

                String uuidStr = d.getString("playerUUID");
                if (uuidStr == null) continue;

                UUID uuid;
                try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { continue; }

                double bal = readNumber(d, "balance", 0.0);
                String name = d.getString("name");
                if (name == null || name.isBlank()) {
                    String lookedUp = Bukkit.getOfflinePlayer(uuid).getName();
                    name = (lookedUp != null) ? lookedUp : uuid.toString();
                }
                out.add(new TopEntry(uuid, name, bal));
            }
        } catch (Exception e) {
            System.err.println("[OreoEssentials] [ECON] topBalances failed: " + e.getMessage());
        }
        return out;
    }


    private static double readNumber(Document doc, String key, double def) {
        Object v = doc.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    private static double clamp(double value, double min, double max, boolean allowNegative) {
        double v = value;
        if (!allowNegative) v = Math.max(min, v);
        return Math.min(max, v);
    }

    private Double getLocalBalance(UUID playerUUID) {
        CachedBalance cached = localBalanceCache.get(playerUUID);
        if (cached == null) return null;

        long now = System.currentTimeMillis();
        if (cached.expiresAtMillis < now) {
            localBalanceCache.remove(playerUUID, cached);
            return null;
        }
        return cached.balance;
    }

    private void cacheBalance(UUID playerUUID, double balance) {
        redis.setBalance(playerUUID, balance);
        localBalanceCache.put(playerUUID, new CachedBalance(balance, System.currentTimeMillis() + LOCAL_BALANCE_CACHE_TTL_MS));
    }
}
