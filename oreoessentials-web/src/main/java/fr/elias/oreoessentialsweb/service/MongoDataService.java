package fr.elias.oreoessentialsweb.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages per-server MongoDB connections for DIRECT_MONGO data source mode.
 *
 * SECURITY NOTE:
 *   Direct MongoDB access requires that the owner's MongoDB is network-accessible
 *   from this web server. This is inherently riskier than the API_SYNC approach.
 *   Prefer API_SYNC whenever possible. Treat this as a convenience/fallback.
 *
 * Clients are pooled per server to avoid reconnecting on every request.
 * Call {@link #evictClient(Long)} when a server's URI changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDataService {

    private final EncryptionService encryptionService;

    @Value("${app.mongo.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.mongo.socket-timeout-ms:10000}")
    private int socketTimeoutMs;

    @Value("${app.mongo.max-pool-size:3}")
    private int maxPoolSize;

    /** Cached MongoClient instances keyed by server ID. */
    private final Map<Long, MongoClient> clientCache = new ConcurrentHashMap<>();

    /**
     * Fetches raw player data document from the owner's MongoDB.
     *
     * @param encryptedUri  The AES-encrypted MongoDB URI from ServerConfig.
     * @param databaseName  The database name OreoEssentials uses on this server.
     * @param serverId      Used for client caching / eviction.
     * @param playerUuid    The Minecraft player UUID to look up.
     * @return              The raw BSON document, or null if not found.
     */
    public Document fetchPlayerData(
            String encryptedUri,
            String databaseName,
            Long serverId,
            UUID playerUuid
    ) {
        MongoClient client = getOrCreateClient(serverId, encryptedUri);
        MongoDatabase db = client.getDatabase(databaseName);

        // OreoEssentials stores player sync data — adjust collection names to match
        // what OreoEssentials actually writes in the shared MongoDB.
        MongoCollection<Document> collection = db.getCollection("player_data");

        return collection.find(new Document("uuid", playerUuid.toString())).first();
    }

    /**
     * Fetches raw order/market data for a player from the owner's MongoDB.
     */
    public Document fetchPlayerOrders(
            String encryptedUri,
            String databaseName,
            Long serverId,
            UUID playerUuid
    ) {
        MongoClient client = getOrCreateClient(serverId, encryptedUri);
        MongoDatabase db = client.getDatabase(databaseName);
        MongoCollection<Document> collection = db.getCollection("orders");

        return collection.find(new Document("playerUuid", playerUuid.toString())).first();
    }

    /**
     * Tests connectivity. Returns true if a ping succeeds within the timeout.
     */
    public boolean testConnection(String rawMongoUri) {
        try (MongoClient testClient = buildClient(rawMongoUri)) {
            testClient.getDatabase("admin").runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.warn("MongoDB connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /** Call this when a server's MongoDB URI is rotated. */
    public void evictClient(Long serverId) {
        MongoClient old = clientCache.remove(serverId);
        if (old != null) {
            try { old.close(); } catch (Exception ignored) {}
            log.info("Evicted MongoDB client for server {}", serverId);
        }
    }

    /** Close all pooled clients on application shutdown. */
    @PreDestroy
    public void closeAll() {
        clientCache.values().forEach(client -> {
            try { client.close(); } catch (Exception ignored) {}
        });
        clientCache.clear();
        log.info("Closed all pooled external MongoDB clients");
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private MongoClient getOrCreateClient(Long serverId, String encryptedUri) {
        return clientCache.computeIfAbsent(serverId, id -> {
            String decryptedUri = encryptionService.decrypt(encryptedUri);
            return buildClient(decryptedUri);
        });
    }

    private MongoClient buildClient(String uri) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToConnectionPoolSettings(pool -> pool
                        .maxSize(maxPoolSize)
                        .minSize(1))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .build();
        return MongoClients.create(settings);
    }
}
