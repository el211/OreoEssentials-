package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.commands.core.playercommands.back.BackLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import fr.elias.oreoEssentials.services.HomeService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageApi {

    void setSpawn(Location loc);
    Location getSpawn();


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
        // on garde une compat simple : on n'enregistre pas de "server" ici
        if (loc == null) {
            setBackData(uuid, null);
            return;
        }
        Map<String, Object> data = Map.of(
                "server", null, // inconnu avec cette API
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
        if (b.getServer().equalsIgnoreCase(current)) {
            return b.toLocalLocation();
        }

        return new Location(null, 0, 0, 0);
    }



    void flush();
    void close();
}
