package fr.elias.oreoessentialsweb.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A Minecraft server (or network) registered by a server owner.
 *
 * Each Server has a unique {@code slug} used as the URL identifier
 * (e.g. /panel/{slug}/players).
 * The owner configures their data source via the linked {@link ServerConfig}.
 */
@Entity
@Table(name = "servers", indexes = {
        @Index(name = "idx_servers_slug", columnList = "slug", unique = true),
        @Index(name = "idx_servers_owner_id", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable server name shown on the panel. */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * URL-safe unique identifier (e.g. "hypixel", "my-survival-server").
     * Players use this to find and link to the server.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    /** URL to the server logo image (stored externally or uploaded). */
    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    /** Short public description shown on the server's panel page. */
    @Column(length = 512)
    private String description;

    /** FK to the OWNER WebUser who registered this server. */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /**
     * BCrypt-hashed API key sent by the Minecraft plugin when it pushes data.
     * The raw key is shown to the owner once and never stored in plaintext.
     */
    @Column(name = "api_key_hash")
    private String apiKeyHash;

    /** Prefix used to identify this server in the API key (for fast lookup). */
    @Column(name = "api_key_prefix", length = 16)
    private String apiKeyPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
