// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/back/BackService.java
package fr.elias.oreoEssentials.commands.core.playercommands.back;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BackService {

    private final StorageApi storage; // global storage (Mongo/YAML/JSON)
    private final Map<UUID, BackLocation> cache = new ConcurrentHashMap<>();
    private final String localServer;

    public BackService(StorageApi storage) {
        this.storage = storage;
        this.localServer = OreoEssentials.get().getConfigService().serverName();
    }

    // ----------------------------------------------------------------------
    // GET RAW BACK LOCATION (always returns BackLocation, even remote-server)
    // ----------------------------------------------------------------------
    public BackLocation getLastRaw(UUID uuid) {
        // 1) check cache
        BackLocation loc = cache.get(uuid);
        if (loc != null) {
            return loc;
        }

        // 2) load from StorageApi
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

    // ----------------------------------------------------------------------
    // GET LOCAL BACK LOCATION (returns Bukkit Location only if same server)
    // ----------------------------------------------------------------------
    public Location getLastLocal(UUID uuid) {
        BackLocation raw = getLastRaw(uuid);
        if (raw == null) return null;

        // location is on another server → TeleportService will handle cross-server
        if (!raw.getServer().equalsIgnoreCase(localServer)) {
            return null;
        }

        return raw.toLocalLocation();
    }

    // ----------------------------------------------------------------------
    // SET BACK LOCATION (BackLocation)
    // ----------------------------------------------------------------------
    public void setLast(UUID uuid, BackLocation loc) {
        if (loc == null) {
            cache.remove(uuid);
            storage.setBackData(uuid, null);
        } else {
            cache.put(uuid, loc);
            storage.setBackData(uuid, loc.toMap());
        }
    }

    // ----------------------------------------------------------------------
    // SET BACK LOCATION (fallback from Bukkit Location)
    // ----------------------------------------------------------------------
    public void setLast(UUID uuid, Location loc) {
        if (loc == null) {
            setLast(uuid, (BackLocation) null);
            return;
        }

        BackLocation b = BackLocation.from(localServer, loc);
        setLast(uuid, b);
    }

    // ----------------------------------------------------------------------
    // Called on plugin disable to avoid memory leaks
    // ----------------------------------------------------------------------
    public void clearCache() {
        cache.clear();
    }
    public BackLocation getLast(UUID uuid) {
        return getLastRaw(uuid);
    }

}
