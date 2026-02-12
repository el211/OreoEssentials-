package fr.elias.oreoEssentials.modules.deathback;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathBackService {
    private final Map<UUID, Location> lastDeath = new ConcurrentHashMap<>();

    public void setLastDeath(UUID playerId, Location loc) {
        if (playerId != null && loc != null) lastDeath.put(playerId, loc.clone());
    }

    public Location getLastDeath(UUID playerId) {
        Location loc = lastDeath.get(playerId);
        return loc == null ? null : loc.clone();
    }

    public void clear(UUID playerId) {
        lastDeath.remove(playerId);
    }
}
