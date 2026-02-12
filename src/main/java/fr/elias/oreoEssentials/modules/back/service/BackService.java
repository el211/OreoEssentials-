// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/back/BackService.java
package fr.elias.oreoEssentials.modules.back.service;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BackService {
    private final Set<UUID> crossServerSwitching = ConcurrentHashMap.newKeySet();

    private final StorageApi storage;
    private final Map<UUID, BackLocation> cache = new ConcurrentHashMap<>();
    private final String localServer;

    public BackService(StorageApi storage) {
        this.storage = storage;
        this.localServer = OreoEssentials.get().getConfigService().serverName();
    }

    public BackLocation getLastRaw(UUID uuid) {
        BackLocation loc = cache.get(uuid);
        if (loc != null) {
            return loc;
        }

        Map<String, Object> saved = storage.getBackData(uuid);
        if (saved == null) {
            return null;
        }

        loc = BackLocation.fromMap(saved);

        if (loc != null) {
            cache.put(uuid, loc);
        }
        return loc;
    }


    public Location getLastLocal(UUID uuid) {
        BackLocation raw = getLastRaw(uuid);
        if (raw == null) return null;

        if (!raw.getServer().equalsIgnoreCase(localServer)) {
            return null;
        }

        return raw.toLocalLocation();
    }

    public void setLast(UUID uuid, BackLocation loc) {
        if (loc == null) {
            cache.remove(uuid);
            storage.setBackData(uuid, null);
        } else {
            cache.put(uuid, loc);
            storage.setBackData(uuid, loc.toMap());
        }
    }


    public void setLast(UUID uuid, Location loc) {
        if (loc == null) {
            setLast(uuid, (BackLocation) null);
            return;
        }

        BackLocation b = BackLocation.from(localServer, loc);
        setLast(uuid, b);
    }
    public void markCrossServerSwitch(UUID uuid) {
        crossServerSwitching.add(uuid);
    }

    public void unmarkCrossServerSwitch(UUID uuid) {
        crossServerSwitching.remove(uuid);
    }

    public boolean isCrossServerSwitch(UUID uuid) {
        return crossServerSwitching.contains(uuid);
    }
    public void clearCache() {
        cache.clear();
    }
    public BackLocation getLast(UUID uuid) {
        return getLastRaw(uuid);
    }

}
