package fr.elias.oreoessentialsweb.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Player data synced from the Minecraft plugin (or fetched from direct Mongo
 * and cached here).
 *
 * The {@code dataJson} column holds a JSON blob of all OreoEssentials player
 * data: stats, balance, playtime, inventory (Base64), jobs, potion effects, etc.
 * The schema is intentionally flexible so that plugin updates don't require DB
 * migrations for every new field.
 *
 * One row per (PlayerServerLink).
 */
@Entity
@Table(name = "cached_player_data", indexes = {
        @Index(name = "idx_cpd_link_id", columnList = "link_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CachedPlayerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → PlayerServerLink. */
    @Column(name = "link_id", nullable = false, unique = true)
    private Long linkId;

    /**
     * JSON blob containing all player data for this server.
     * Structure is controlled by the plugin sync payload.
     *
     * Example fields (from OreoEssentials):
     *   balance, playtime, stats{kills,deaths,joinCount},
     *   inventoryBase64, armorBase64, potionEffects[],
     *   jobs{name, level, xp}[], orders[]
     */
    @Column(name = "data_json", columnDefinition = "TEXT", nullable = false)
    private String dataJson;

    /** When the plugin last pushed a sync for this player. */
    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @PrePersist
    void prePersist() {
        if (lastUpdatedAt == null) lastUpdatedAt = Instant.now();
    }
}
