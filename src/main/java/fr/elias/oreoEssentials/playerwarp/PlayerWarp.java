// File: src/main/java/fr/elias/oreoEssentials/playerwarp/PlayerWarp.java
package fr.elias.oreoEssentials.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * PlayerWarp data model.
 *
 * VERIFIED: No user-facing messages - pure data class.
 * All messaging handled by PlayerWarpService and PlayerWarpCommand.
 */
public class PlayerWarp {

    private final String id;
    private UUID owner;
    private String name;
    private Location location;

    private boolean whitelistEnabled;
    private Set<UUID> whitelist;

    // Optional/legacy fields (Mongo compatibility)
    private String description;
    private String category;
    private boolean locked;
    private double cost;

    /**
     * Icon used in GUI/list.
     * Must be serialized by storage layer (YAML/Mongo/etc.)
     */
    private ItemStack icon;

    /**
     * Players who can manage this warp (edit fields, etc.)
     */
    private Set<UUID> managers;

    /**
     * Optional password for access (null or "" = no password)
     */
    private String password;

    // ============================================================
    // CONSTRUCTORS
    // ============================================================

    /** Minimal constructor */
    public PlayerWarp(String id, UUID owner, String name, Location loc) {
        this(id, owner, name, loc, false, new HashSet<>());
    }

    /** Full constructor (recommended) */
    public PlayerWarp(String id,
                      UUID owner,
                      String name,
                      Location loc,
                      boolean whitelistEnabled,
                      Set<UUID> whitelist) {
        this.id = id;
        this.owner = owner;
        this.name = name.toLowerCase();
        this.location = loc;

        this.whitelistEnabled = whitelistEnabled;
        this.whitelist = (whitelist == null) ? new HashSet<>() : whitelist;

        // Default values for optional fields
        this.description = "";
        this.category = "";
        this.locked = false;
        this.cost = 0.0;

        // New fields default
        this.icon = null;
        this.managers = new HashSet<>();
        this.password = null;
    }

    // ============================================================
    // GETTERS / SETTERS
    // ============================================================

    public String getId() { return id; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name.toLowerCase(); }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    // ---- Description ----
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ---- Category ----
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    // ---- Locked ----
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    // ---- Cost ----
    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    // ---- Whitelist ----
    public boolean isWhitelistEnabled() { return whitelistEnabled; }
    public void setWhitelistEnabled(boolean enabled) { this.whitelistEnabled = enabled; }

    public Set<UUID> getWhitelist() { return whitelist; }
    public void setWhitelist(Set<UUID> whitelist) {
        this.whitelist = (whitelist == null) ? new HashSet<>() : whitelist;
    }

    public void addToWhitelist(UUID uuid) {
        if (this.whitelist == null) this.whitelist = new HashSet<>();
        this.whitelist.add(uuid);
    }

    public void removeFromWhitelist(UUID uuid) {
        if (this.whitelist == null) return;
        this.whitelist.remove(uuid);
    }

    // ---- Icon ----
    public ItemStack getIcon() { return icon; }
    public void setIcon(ItemStack icon) { this.icon = icon; }

    // ---- Managers ----
    public Set<UUID> getManagers() {
        if (this.managers == null) {
            this.managers = new HashSet<>();
        }
        return managers;
    }

    public void setManagers(Set<UUID> managers) {
        this.managers = (managers == null) ? new HashSet<>() : managers;
    }

    public void addManager(UUID uuid) {
        if (this.managers == null) this.managers = new HashSet<>();
        this.managers.add(uuid);
    }

    public void removeManager(UUID uuid) {
        if (this.managers == null) return;
        this.managers.remove(uuid);
    }

    // ---- Password ----
    public String getPassword() { return password; }
    public void setPassword(String password) {
        this.password = (password == null || password.isEmpty()) ? null : password;
    }

    // ============================================================
    // STATIC FACTORY — safe location creation
    // ============================================================

    public static Location fromData(String world,
                                    double x,
                                    double y,
                                    double z,
                                    float yaw,
                                    float pitch) {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}