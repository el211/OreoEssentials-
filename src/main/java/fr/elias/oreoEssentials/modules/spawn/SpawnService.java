package fr.elias.oreoEssentials.modules.spawn;

import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class SpawnService {
    private final StorageApi storage;

    public SpawnService(StorageApi storage) {
        this.storage = storage;
    }

    public void setSpawn(Location loc) {
        storage.setSpawn(loc);
    }

    public Location getSpawn() {
        return storage.getSpawn();
    }

    public void setSpawn(String server, Location loc) {
        storage.setSpawn(server, loc);
    }

    public Location getSpawn(String server) {
        return storage.getSpawn(server);
    }

    public void setLocalSpawn(Location loc) {
        setSpawn(Bukkit.getServer().getName(), loc);
    }

    public Location getLocalSpawn() {
        return getSpawn(Bukkit.getServer().getName());
    }
}
