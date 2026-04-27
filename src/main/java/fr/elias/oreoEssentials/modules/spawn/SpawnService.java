package fr.elias.oreoEssentials.modules.spawn;

import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class SpawnService {
    public static final String GLOBAL_SPAWN_KEY = "__global__";

    private final StorageApi storage;
    private final String localServer;

    public SpawnService(StorageApi storage, String localServer) {
        this.storage = storage;
        this.localServer = (localServer == null || localServer.isBlank())
                ? Bukkit.getServer().getName()
                : localServer;
    }

    public void setSpawn(Location loc) {
        setLocalSpawn(loc);
    }

    public Location getSpawn() {
        return getLocalSpawn();
    }

    public void setSpawn(String server, Location loc) {
        storage.setSpawn(server, loc);
    }

    public Location getSpawn(String server) {
        return storage.getSpawn(server);
    }

    public void setLocalSpawn(Location loc) {
        setSpawn(localServer, loc);
    }

    public Location getLocalSpawn() {
        return getSpawn(localServer);
    }

    public void setGlobalSpawn(Location loc) {
        setSpawn(GLOBAL_SPAWN_KEY, loc);
    }

    public Location getGlobalSpawn() {
        return getSpawn(GLOBAL_SPAWN_KEY);
    }
}
