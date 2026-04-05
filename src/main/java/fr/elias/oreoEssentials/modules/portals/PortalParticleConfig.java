package fr.elias.oreoEssentials.modules.portals;

/**
 * Per-portal particle settings, stored in portals.yml under each portal's key.
 *
 * teleport  — particles spawned when a player passes through the portal
 * ambient   — particles that continuously float inside the portal region
 */
public final class PortalParticleConfig {

    // ── Teleport particles ────────────────────────────────────────────────────
    /** Particle type name (e.g. "PORTAL", "FLAME"). Empty = use global default. */
    public String teleportType;
    public int teleportCount;

    // ── Ambient particles ─────────────────────────────────────────────────────
    /** Whether ambient particles are enabled for this portal specifically. */
    public boolean ambientEnabled;
    public String ambientType;
    public int ambientCount;

    public PortalParticleConfig() {
        this.teleportType    = "";
        this.teleportCount   = -1;   // -1 = use global default
        this.ambientEnabled  = false;
        this.ambientType     = "";
        this.ambientCount    = 3;
    }

    public PortalParticleConfig(String teleportType, int teleportCount,
                                boolean ambientEnabled, String ambientType, int ambientCount) {
        this.teleportType   = teleportType;
        this.teleportCount  = teleportCount;
        this.ambientEnabled = ambientEnabled;
        this.ambientType    = ambientType;
        this.ambientCount   = ambientCount;
    }

    /** Returns true if this config overrides the global teleport particle. */
    public boolean hasTeleportOverride() {
        return teleportType != null && !teleportType.isEmpty();
    }
}
