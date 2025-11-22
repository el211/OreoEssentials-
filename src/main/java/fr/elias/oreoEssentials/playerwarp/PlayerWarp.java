// src/main/java/fr/elias/oreoEssentials/playerwarp/PlayerWarp.java
package fr.elias.oreoEssentials.playerwarp;

import org.bukkit.Location;

import java.util.UUID;

public class PlayerWarp {

    private final String id;          // global ID: ownerUUID:nameLower
    private final UUID owner;
    private final String name;        // case-insensitive key
    private final Location location;

    private String description;
    private String category;
    private boolean locked;
    private double cost;

    public PlayerWarp(String id, UUID owner, String name, Location location) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.location = location;
    }

    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getName() { return name; }
    public Location getLocation() { return location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }
}
