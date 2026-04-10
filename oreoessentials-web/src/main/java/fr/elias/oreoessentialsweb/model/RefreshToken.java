package fr.elias.oreoessentialsweb.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Stored refresh token for server-side revocation.
 *
 * On logout or password change, all tokens for that user are revoked.
 * The raw token is never stored — only its SHA-256 hash.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_user_id",    columnList = "user_id"),
        @Index(name = "idx_rt_token_hash", columnList = "token_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hash of the raw refresh token string. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void prePersist() {
        issuedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
