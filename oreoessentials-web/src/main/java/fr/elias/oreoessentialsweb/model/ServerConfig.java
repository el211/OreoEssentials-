package fr.elias.oreoessentialsweb.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Sensitive configuration for a registered server.
 *
 * Stored in a separate table (1:1 with Server) so that config data can be
 * restricted to tighter DB-level permissions if desired.
 *
 * The MongoDB URI is AES-256-GCM encrypted before persistence.
 * The encryption key comes from the app environment (never the DB).
 *
 * {@code dataSourceMode} controls how the panel fetches player data:
 *   DIRECT_MONGO — panel reads directly from the owner's MongoDB (requires
 *                  the URI to be accessible from the web server; less safe).
 *   API_SYNC     — the OreoEssentials plugin pushes data to this panel via
 *                  REST API (recommended, no DB exposure).
 *   HYBRID       — tries cached/synced data first, falls back to direct Mongo.
 */
@Entity
@Table(name = "server_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServerConfig {

    /** Same PK as Server (shared primary key — 1:1). */
    @Id
    private Long serverId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "server_id")
    private Server server;

    // ─── Data source ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source_mode", nullable = false, length = 16)
    @Builder.Default
    private DataSourceMode dataSourceMode = DataSourceMode.API_SYNC;

    // ─── MongoDB direct connection (used when mode = DIRECT_MONGO / HYBRID) ─

    /**
     * AES-256-GCM encrypted MongoDB connection URI.
     * Decrypted in-memory only when needed, never logged or returned via API.
     */
    @Column(name = "encrypted_mongo_uri", length = 1024)
    private String encryptedMongoUri;

    /** Name of the MongoDB database OreoEssentials uses on this server. */
    @Column(name = "mongo_database_name", length = 128)
    private String mongoDatabaseName;

    // ─── API sync (used when mode = API_SYNC / HYBRID) ──────────────────────

    /** Timestamp of the last successful plugin sync. */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum DataSourceMode {
        DIRECT_MONGO,
        API_SYNC,
        HYBRID
    }
}
