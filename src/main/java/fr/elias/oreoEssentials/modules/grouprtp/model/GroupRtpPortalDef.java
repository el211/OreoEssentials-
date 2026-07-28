package fr.elias.oreoEssentials.modules.grouprtp.model;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Immutable configuration for a single Group-RTP portal region.
 * All instances are loaded from group-rtp.yml at startup / reload.
 */
public final class GroupRtpPortalDef {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final String id;
    private final String displayName;   // supports &color codes
    private final String worldName;     // world portal region lives in
    private final BoundingBox box;      // axis-aligned bounding region

    // ── RTP parameters ────────────────────────────────────────────────────────
    private final String  rtpWorld;
    private final String  rtpServer;    // null / "" → same server
    private final double  rtpRadius;
    private final double  rtpMinRadius;
    private final double  rtpCenterX;
    private final double  rtpCenterZ;
    private final int     rtpMinY;
    private final int     rtpMaxY;
    private final int     rtpAttempts;
    private final Set<String>   unsafeBlocks;       // Material names
    private final Set<String>   blacklistedBiomes;  // Biome names

    // ── Group behaviour ───────────────────────────────────────────────────────
    private final int    minPlayers;
    private final int    maxPlayers;
    private final int    countdownSeconds;
    private final double clusterRadius;  // how far from base location players can scatter

    // ── Misc ──────────────────────────────────────────────────────────────────
    private final long   cooldownMs;
    private final String permission;   // null / "" = no permission required

    // ── Messages ──────────────────────────────────────────────────────────────
    private final Map<String, String> messages;

    // ── Effects ───────────────────────────────────────────────────────────────
    private final String ambientParticle;
    private final int    ambientCount;
    private final String teleportParticle;
    private final int    teleportCount;
    private final String soundEnter;
    private final String soundTeleport;

    @SuppressWarnings("java:S107")
    public GroupRtpPortalDef(
            String id, String displayName, String worldName, BoundingBox box,
            String rtpWorld, String rtpServer,
            double rtpRadius, double rtpMinRadius, double rtpCenterX, double rtpCenterZ,
            int rtpMinY, int rtpMaxY, int rtpAttempts,
            Set<String> unsafeBlocks, Set<String> blacklistedBiomes,
            int minPlayers, int maxPlayers, int countdownSeconds, double clusterRadius,
            long cooldownMs, String permission,
            Map<String, String> messages,
            String ambientParticle, int ambientCount,
            String teleportParticle, int teleportCount,
            String soundEnter, String soundTeleport) {
        this.id               = id;
        this.displayName      = displayName;
        this.worldName        = worldName;
        this.box              = box;
        this.rtpWorld         = rtpWorld;
        this.rtpServer        = rtpServer;
        this.rtpRadius        = rtpRadius;
        this.rtpMinRadius     = rtpMinRadius;
        this.rtpCenterX       = rtpCenterX;
        this.rtpCenterZ       = rtpCenterZ;
        this.rtpMinY          = rtpMinY;
        this.rtpMaxY          = rtpMaxY;
        this.rtpAttempts      = rtpAttempts;
        this.unsafeBlocks     = unsafeBlocks;
        this.blacklistedBiomes = blacklistedBiomes;
        this.minPlayers       = minPlayers;
        this.maxPlayers       = maxPlayers;
        this.countdownSeconds = countdownSeconds;
        this.clusterRadius    = clusterRadius;
        this.cooldownMs       = cooldownMs;
        this.permission       = permission;
        this.messages         = messages;
        this.ambientParticle  = ambientParticle;
        this.ambientCount     = ambientCount;
        this.teleportParticle = teleportParticle;
        this.teleportCount    = teleportCount;
        this.soundEnter       = soundEnter;
        this.soundTeleport    = soundTeleport;
    }

    public boolean contains(org.bukkit.Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        // BoundingBox.contains(Vector) is exclusive on the max boundary, so a player
        // standing exactly on the top face (e.g. Y == maxY) would never be detected.
        // Use explicit inclusive comparisons instead.
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= box.getMinX() && x <= box.getMaxX()
            && y >= box.getMinY() && y <= box.getMaxY()
            && z >= box.getMinZ() && z <= box.getMaxZ();
    }

    public boolean hasPermission(org.bukkit.entity.Player p) {
        return permission == null || permission.isBlank() || p.hasPermission(permission);
    }

    public String msg(String key) {
        return messages.getOrDefault(key, "");
    }

    public String msg(String key, String def) {
        return messages.getOrDefault(key, def);
    }

    public Map<String, String> getMessages() {
        return Collections.unmodifiableMap(messages);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String      getId()               { return id; }
    public String      getDisplayName()      { return displayName; }
    public String      getWorldName()        { return worldName; }
    public BoundingBox getBox()              { return box; }
    public String      getRtpWorld()         { return rtpWorld; }
    public String      getRtpServer()        { return rtpServer; }
    public double      getRtpRadius()        { return rtpRadius; }
    public double      getRtpMinRadius()     { return rtpMinRadius; }
    public double      getRtpCenterX()       { return rtpCenterX; }
    public double      getRtpCenterZ()       { return rtpCenterZ; }
    public int         getRtpMinY()          { return rtpMinY; }
    public int         getRtpMaxY()          { return rtpMaxY; }
    public int         getRtpAttempts()      { return rtpAttempts; }
    public Set<String> getUnsafeBlocks()     { return unsafeBlocks; }
    public Set<String> getBlacklistedBiomes(){ return blacklistedBiomes; }
    public int         getMinPlayers()       { return minPlayers; }
    public int         getMaxPlayers()       { return maxPlayers; }
    public int         getCountdownSeconds() { return countdownSeconds; }
    public double      getClusterRadius()    { return clusterRadius; }
    public long        getCooldownMs()       { return cooldownMs; }
    public String      getPermission()       { return permission; }
    public String      getAmbientParticle()  { return ambientParticle; }
    public int         getAmbientCount()     { return ambientCount; }
    public String      getTeleportParticle() { return teleportParticle; }
    public int         getTeleportCount()    { return teleportCount; }
    public String      getSoundEnter()       { return soundEnter; }
    public String      getSoundTeleport()    { return soundTeleport; }
}
