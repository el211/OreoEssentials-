package fr.elias.oreoessentialsweb.service;

import fr.elias.oreoessentialsweb.model.RefreshToken;
import fr.elias.oreoessentialsweb.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Manages server-side refresh token lifecycle.
 *
 * Raw tokens are never stored — only their SHA-256 hash.
 * This means even if the DB is compromised, tokens cannot be replayed
 * without knowing the original token value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Persists a refresh token by storing only its hash.
     * Call this right after generating the raw JWT refresh token.
     */
    @Transactional
    public void store(Long userId, String rawToken) {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();
        repository.save(token);
    }

    /**
     * Validates a raw refresh token against the stored hash.
     * Returns the RefreshToken record if valid, throws if not.
     */
    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not recognised"));
        if (!token.isValid()) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }
        return token;
    }

    /** Revokes all refresh tokens for a user (call on logout / password change). */
    @Transactional
    public void revokeAll(Long userId) {
        int count = repository.revokeAllForUser(userId, Instant.now());
        log.debug("Revoked {} refresh tokens for user {}", count, userId);
    }

    /** Scheduled cleanup of expired tokens — runs daily at 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        log.info("Purged {} expired refresh tokens", deleted);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
