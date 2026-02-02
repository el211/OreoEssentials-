package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageApi {


    void setSpawn(String server, Location loc);
    Location getSpawn(String server);

    default void setSpawn(Location loc) {
        setSpawn(Bukkit.getServer().getName(), loc);
    }

    default Location getSpawn() {
        return getSpawn(Bukkit.getServer().getName());
    }


    void setWarp(String name, Location loc);
    boolean delWarp(String name);
    Location getWarp(String name);
    Set<String> listWarps();



    boolean setHome(UUID uuid, String name, Location loc);
    boolean delHome(UUID uuid, String name);
    Location getHome(UUID uuid, String name);
    Set<String> homes(UUID uuid);
    Map<String, HomeService.StoredHome> listHomes(UUID owner);


    void setBackData(UUID uuid, Map<String, Object> data);
    Map<String, Object> getBackData(UUID uuid);

    default void setLast(UUID uuid, Location loc) {
        if (loc == null) {
            setBackData(uuid, null);
            return;
        }

        String server = Bukkit.getServer().getName();

        Map<String, Object> data = Map.of(
                "server", server,
                "world", loc.getWorld() != null ? loc.getWorld().getName() : "",
                "x", loc.getX(),
                "y", loc.getY(),
                "z", loc.getZ(),
                "yaw", loc.getYaw(),
                "pitch", loc.getPitch()
        );
        setBackData(uuid, data);
    }

    default Location getLast(UUID uuid) {
        Map<String, Object> map = getBackData(uuid);
        if (map == null) return null;

        BackLocation b = BackLocation.fromMap(map);

        String current = Bukkit.getServer().getName();
        String lastServer = b.getServer();

        if (lastServer != null && lastServer.equalsIgnoreCase(current)) {
            return b.toLocalLocation();
        }

        return null;
    }

    void flush();
    void close();
}
