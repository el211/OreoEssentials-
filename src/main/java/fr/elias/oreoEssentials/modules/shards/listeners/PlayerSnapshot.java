package fr.elias.oreoEssentials.modules.shards.listeners;

import org.bukkit.potion.PotionEffect;


public class PlayerSnapshot {
    // Identity
    public String uuid;
    public String name;
    public String targetShard;

    // Location
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;

    // Velocity (to maintain momentum)
    public double velX;
    public double velY;
    public double velZ;

    // Health & Food
    public double health;
    public int foodLevel;
    public float saturation;
    public float exhaustion;

    // Experience
    public float exp;
    public int level;

    // Game state
    public String gameMode;
    public boolean flying;
    public boolean allowFlight;
    public float flySpeed;
    public float walkSpeed;

    // Effects
    public int fireTicks;
    public int remainingAir;
    public float fallDistance;
    public PotionEffect[] potionEffects;

    // Meta
    public long timestamp;

    public PlayerSnapshot() {
        // Empty constructor for GSON
    }
}
