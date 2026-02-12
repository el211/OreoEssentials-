package fr.elias.oreoEssentials.offline;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Local cache for offline player name <-> UUID mappings.
 * Supports both case-sensitive and case-insensitive lookups, and can be kept in sync with Redis if desired.
 */
public class OfflinePlayerCache {

    private final Map<String, UUID> nameToId = new ConcurrentHashMap<>();
    private final Map<UUID, String> idToName = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToIdCaseInsensitive = new ConcurrentHashMap<>();

    /**
     * Returns the UUID of a player by name, preferring online players first,
     * then using local cache (case-sensitive, then case-insensitive).
     */
    public UUID getId(String name) {
        if (name == null) return null;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        UUID found = nameToId.get(name);
        if (found != null) return found;

        return nameToIdCaseInsensitive.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the name of a player by UUID, preferring online players first,
     * then using local cache.
     */
    public String getName(UUID id) {
        if (id == null) return null;

        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();

        return idToName.get(id);
    }

    /**
     * Adds a name-UUID mapping to the cache.
     */
    public void add(String name, UUID id) {
        if (name == null || id == null) return;
        nameToId.put(name, id);
        nameToIdCaseInsensitive.put(name.toLowerCase(Locale.ROOT), id);
        idToName.put(id, name);
    }

    /**
     * Removes a mapping by name.
     */
    public void remove(String name) {
        if (name == null) return;
        UUID id = nameToId.remove(name);
        nameToIdCaseInsensitive.remove(name.toLowerCase(Locale.ROOT));
        if (id != null) idToName.remove(id);
    }

    /**
     * Returns true if the cache contains a mapping for the given UUID.
     */
    public boolean contains(UUID id) {
        return id != null && idToName.containsKey(id);
    }

    /**
     * Returns true if the cache contains a mapping for the given name.
     */
    public boolean contains(String name) {
        if (name == null) return false;
        return nameToId.containsKey(name) || nameToIdCaseInsensitive.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Removes a mapping by UUID.
     */
    public void remove(UUID id) {
        if (id == null) return;
        String name = idToName.remove(id);
        if (name != null) {
            nameToId.remove(name);
            nameToIdCaseInsensitive.remove(name.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Returns all known player names in the cache.
     */
    public List<String> getNames() {
        return new ArrayList<>(nameToId.keySet());
    }

    /**
     * Resolves a player name to UUID (case-insensitive, local only).
     * This method is compatible with Redis signature for merging cache behaviors.
     * @param name Player name to resolve.
     * @return UUID or null if not cached.
     */
    public UUID resolveNameToUuid(String name) {
        return getId(name);
    }
}