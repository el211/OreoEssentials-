package fr.elias.oreoessentialsweb.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered account on the web panel.
 *
 * Both player accounts and server-owner accounts share this table.
 * The {@code role} field controls what each account can access.
 *
 * Player accounts optionally carry a {@code minecraftUuid} that is
 * verified against the linked server's MongoDB / plugin data.
 */
@Entity
@Table(name = "web_users", indexes = {
        @Index(name = "idx_web_users_email", columnList = "email", unique = true),
        @Index(name = "idx_web_users_username", columnList = "username", unique = true),
        @Index(name = "idx_web_users_minecraft_uuid", columnList = "minecraft_uuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login username, unique across all accounts. */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** Email address, used for login and notifications. */
    @Column(nullable = false, unique = true, length = 256)
    private String email;

    /** BCrypt-hashed password. Never store plaintext. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    /**
     * For PLAYER accounts: the Minecraft UUID this web account represents.
     * Null for OWNER and ADMIN accounts.
     */
    @Column(name = "minecraft_uuid")
    private UUID minecraftUuid;

    /** Display name fetched from Minecraft (cached, not authoritative). */
    @Column(name = "minecraft_username", length = 64)
    private String minecraftUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
