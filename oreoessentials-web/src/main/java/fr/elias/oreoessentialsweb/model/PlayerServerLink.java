package fr.elias.oreoessentialsweb.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a player's web account to a specific registered Minecraft server.
 *
 * A player can be linked to multiple servers.
 * The link is verified when the player's Minecraft UUID is confirmed to exist
 * in the server's player data (either via API sync or direct Mongo lookup).
 *
 * Unique constraint: one player can be linked to a server only once.
 */
@Entity
@Table(name = "player_server_links", uniqueConstraints = {
        @UniqueConstraint(name = "uq_player_server", columnNames = {"player_id", "server_id"})
}, indexes = {
        @Index(name = "idx_psl_player_id", columnList = "player_id"),
        @Index(name = "idx_psl_server_id", columnList = "server_id"),
        @Index(name = "idx_psl_minecraft_uuid", columnList = "minecraft_uuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerServerLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → WebUser (role = PLAYER). */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** FK → Server. */
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    /**
     * The Minecraft UUID of this player on the linked server.
     * Set during link verification.
     */
    @Column(name = "minecraft_uuid", nullable = false)
    private UUID minecraftUuid;

    /**
     * Whether the link has been verified (player actually exists on that server).
     * Unverified links cannot access player data.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @PrePersist
    void prePersist() {
        linkedAt = Instant.now();
    }
}
