package fr.elias.oreoessentialsweb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.elias.oreoessentialsweb.dto.player.PlayerDataResponse;
import fr.elias.oreoessentialsweb.model.*;
import fr.elias.oreoessentialsweb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerDataService {

    private final PlayerServerLinkRepository linkRepository;
    private final CachedPlayerDataRepository cachedDataRepository;
    private final ServerConfigRepository serverConfigRepository;
    private final MongoDataService mongoDataService;
    private final PlayerVerificationService verificationService;
    private final ObjectMapper objectMapper;

    /**
     * Returns player data for a given player–server link.
     *
     * Strategy depends on the server's {@link ServerConfig.DataSourceMode}:
     *   API_SYNC    → return cached data (pushed by plugin).
     *   DIRECT_MONGO → read live from the owner's MongoDB.
     *   HYBRID      → return cached data if fresh (< 5 min), else fall back to Mongo.
     */
    @Transactional(readOnly = true)
    public PlayerDataResponse getPlayerData(Long playerId, Long serverId) {
        PlayerServerLink link = linkRepository.findByPlayerIdAndServerId(playerId, serverId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No verified link between player and server"));

        if (!link.isVerified()) {
            throw new IllegalStateException("Player–server link is not yet verified");
        }

        ServerConfig config = serverConfigRepository.findByServerId(serverId)
                .orElseThrow(() -> new IllegalStateException("Server config not found"));

        return switch (config.getDataSourceMode()) {
            case API_SYNC     -> fromCache(link);
            case DIRECT_MONGO -> fromMongo(link, config);
            case HYBRID       -> fromHybrid(link, config);
        };
    }

    // ─── Data source strategies ──────────────────────────────────────────────

    private PlayerDataResponse fromCache(PlayerServerLink link) {
        CachedPlayerData cached = cachedDataRepository.findByLinkId(link.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No synced data yet. The plugin may not have pushed data for this player."));
        return parseJson(cached.getDataJson(), cached.getLastUpdatedAt());
    }

    private PlayerDataResponse fromMongo(PlayerServerLink link, ServerConfig config) {
        if (config.getEncryptedMongoUri() == null) {
            throw new IllegalStateException(
                    "Server is configured for DIRECT_MONGO but no MongoDB URI is set.");
        }
        Document doc = mongoDataService.fetchPlayerData(
                config.getEncryptedMongoUri(),
                config.getMongoDatabaseName(),
                link.getServerId(),
                link.getMinecraftUuid()
        );
        if (doc == null) {
            throw new IllegalStateException("Player not found in server's MongoDB");
        }
        return mapDocumentToResponse(doc);
    }

    private PlayerDataResponse fromHybrid(PlayerServerLink link, ServerConfig config) {
        // Prefer cache if it's fresh (< 5 minutes)
        return cachedDataRepository.findByLinkId(link.getId())
                .filter(cached -> cached.getLastUpdatedAt().isAfter(Instant.now().minusSeconds(300)))
                .map(cached -> parseJson(cached.getDataJson(), cached.getLastUpdatedAt()))
                .orElseGet(() -> {
                    log.debug("HYBRID: cache stale for link {}, falling back to Mongo", link.getId());
                    return fromMongo(link, config);
                });
    }

    // ─── Plugin sync ingestion ───────────────────────────────────────────────

    /**
     * Called by PluginApiController when the plugin pushes a player sync payload.
     * The JSON structure is defined by the plugin (OreoEssentials PlayerSyncSnapshot + extras).
     */
    @Transactional
    public void ingestPlayerSync(Long serverId, UUID playerUuid, String dataJson) {
        PlayerServerLink link = linkRepository.findByServerIdAndMinecraftUuid(serverId, playerUuid)
                .orElse(null);

        if (link == null) {
            // Player hasn't linked their web account yet — nothing to cache
            log.debug("Received sync for unlinked player {} on server {}", playerUuid, serverId);
            return;
        }

        // Auto-verify unverified links — first sync proves the UUID exists on this server
        if (!link.isVerified()) {
            verificationService.verifyLink(serverId, playerUuid);
        }

        CachedPlayerData cached = cachedDataRepository.findByLinkId(link.getId())
                .orElseGet(() -> CachedPlayerData.builder().linkId(link.getId()).build());

        cached.setDataJson(dataJson);
        cached.setLastUpdatedAt(Instant.now());
        cachedDataRepository.save(cached);
    }

    // ─── Link management ─────────────────────────────────────────────────────

    @Transactional
    public PlayerServerLink linkPlayer(Long playerId, Long serverId, UUID minecraftUuid) {
        if (linkRepository.existsByPlayerIdAndServerId(playerId, serverId)) {
            throw new IllegalArgumentException("Already linked to this server");
        }
        PlayerServerLink link = PlayerServerLink.builder()
                .playerId(playerId)
                .serverId(serverId)
                .minecraftUuid(minecraftUuid)
                .verified(false)
                .build();
        return linkRepository.save(link);
    }

    // ─── Parsing helpers ─────────────────────────────────────────────────────

    private PlayerDataResponse parseJson(String json, Instant lastUpdated) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});
            return new PlayerDataResponse(data, lastUpdated);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cached player data", e);
        }
    }

    private PlayerDataResponse mapDocumentToResponse(Document doc) {
        // Convert BSON document to a generic Map for the response
        Map<String, Object> data = objectMapper.convertValue(
                objectMapper.convertValue(doc.toJson(), Object.class),
                new TypeReference<>() {}
        );
        return new PlayerDataResponse(data, Instant.now());
    }
}
