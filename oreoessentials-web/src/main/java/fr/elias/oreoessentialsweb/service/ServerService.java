package fr.elias.oreoessentialsweb.service;

import fr.elias.oreoessentialsweb.dto.server.*;
import fr.elias.oreoessentialsweb.model.Server;
import fr.elias.oreoessentialsweb.model.ServerConfig;
import fr.elias.oreoessentialsweb.repository.ServerConfigRepository;
import fr.elias.oreoessentialsweb.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerConfigRepository serverConfigRepository;
    private final EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ServerCreateResponse createServer(Long ownerId, ServerCreateRequest request) {
        if (serverRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException("Slug '" + request.slug() + "' is already taken");
        }

        // Generate the API key (shown to owner once, then hashed)
        String rawApiKey = generateApiKey();
        String keyPrefix = rawApiKey.split("_")[1];

        Server server = Server.builder()
                .name(request.name())
                .slug(request.slug())
                .logoUrl(request.logoUrl())
                .description(request.description())
                .ownerId(ownerId)
                .apiKeyHash(passwordEncoder.encode(rawApiKey))
                .apiKeyPrefix(keyPrefix)
                .build();

        server = serverRepository.save(server);

        // Build config
        ServerConfig config = ServerConfig.builder()
                .server(server)
                .dataSourceMode(request.dataSourceMode() != null
                        ? request.dataSourceMode()
                        : ServerConfig.DataSourceMode.API_SYNC)
                .build();

        if (request.mongoUri() != null && !request.mongoUri().isBlank()) {
            config.setEncryptedMongoUri(encryptionService.encrypt(request.mongoUri()));
            config.setMongoDatabaseName(request.mongoDatabaseName());
        }

        serverConfigRepository.save(config);

        return new ServerCreateResponse(
                server.getId(),
                server.getName(),
                server.getSlug(),
                rawApiKey,   // returned ONCE — owner must save this
                "Server created. Save your API key — it won't be shown again."
        );
    }

    @Transactional(readOnly = true)
    public List<ServerResponse> getOwnerServers(Long ownerId) {
        return serverRepository.findAllByOwnerId(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServerResponse getServerBySlug(String slug) {
        Server server = serverRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + slug));
        return toResponse(server);
    }

    @Transactional
    public String regenerateApiKey(Long serverId, Long requestingOwnerId) {
        Server server = getOwnedServer(serverId, requestingOwnerId);

        String rawApiKey = generateApiKey();
        String keyPrefix = rawApiKey.split("_")[1];

        server.setApiKeyHash(passwordEncoder.encode(rawApiKey));
        server.setApiKeyPrefix(keyPrefix);
        serverRepository.save(server);

        return rawApiKey;
    }

    @Transactional
    public void updateMongoUri(Long serverId, Long requestingOwnerId, String newUri, String dbName) {
        getOwnedServer(serverId, requestingOwnerId); // ownership check

        ServerConfig config = serverConfigRepository.findByServerId(serverId)
                .orElseThrow(() -> new IllegalStateException("Config not found for server: " + serverId));

        config.setEncryptedMongoUri(encryptionService.encrypt(newUri));
        config.setMongoDatabaseName(dbName);
        serverConfigRepository.save(config);
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private Server getOwnedServer(Long serverId, Long ownerId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));
        if (!server.getOwnerId().equals(ownerId)) {
            throw new SecurityException("Access denied");
        }
        return server;
    }

    private ServerResponse toResponse(Server s) {
        return new ServerResponse(s.getId(), s.getName(), s.getSlug(), s.getLogoUrl(),
                s.getDescription(), s.isActive(), s.getCreatedAt());
    }

    /**
     * Generates a random API key in the format: oreo_<8-char-prefix>_<32-char-secret>
     * The prefix is stored in plain for fast DB lookup; the full key is BCrypt-hashed.
     */
    private String generateApiKey() {
        SecureRandom rng = new SecureRandom();
        byte[] prefixBytes = new byte[6];
        byte[] secretBytes = new byte[24];
        rng.nextBytes(prefixBytes);
        rng.nextBytes(secretBytes);

        String prefix = Base64.getUrlEncoder().withoutPadding().encodeToString(prefixBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        return "oreo_" + prefix + "_" + secret;
    }
}
