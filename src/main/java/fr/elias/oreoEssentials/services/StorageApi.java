// File: src/main/java/fr/elias/oreoEssentials/services/StorageApi.java
package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.commands.core.playercommands.back.BackLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import fr.elias.oreoEssentials.services.HomeService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageApi {

    // spawn
    void setSpawn(Location loc);
    Location getSpawn();

    // warps
    void setWarp(String name, Location loc);
    boolean delWarp(String name);
    Location getWarp(String name);
    Set<String> listWarps();

    // homes
    boolean setHome(UUID uuid, String name, Location loc);
    boolean delHome(UUID uuid, String name);
    Location getHome(UUID uuid, String name);
    Set<String> homes(UUID uuid);
    Map<String, HomeService.StoredHome> listHomes(UUID owner);

    /* ------------- BACK: nouvelle API globale ------------- */

    /**
     * Stocke les infos de /back pour un joueur dans le storage global.
     * Le contenu de la map vient de BackLocation.toMap().
     *
     * Si data == null -> supprime l'entrée.
     */
    void setBackData(UUID uuid, Map<String, Object> data);

    /**
     * Récupère les infos de /back pour un joueur.
     * Doit retourner exactement ce qui a été passé à setBackData (ou null).
     */
    Map<String, Object> getBackData(UUID uuid);

    /* ------------- BACK : anciens helpers Location (optionnels) -------------
     * Tu peux les laisser si tu les utilises ailleurs.
     * BackService NE LES UTILISE PLUS.
     */

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

        // just convert to BackLocation, do not resolve world here
        BackLocation b = BackLocation.fromMap(map);

        // return ONLY if same server, otherwise null (so BackService can handle remote teleport)
        String current = Bukkit.getServer().getName();
        if (b.getServer().equalsIgnoreCase(current)) {
            return b.toLocalLocation();
        }

        // IMPORTANT: return a dummy Location so getLast() is not considered “null”
        return new Location(null, 0, 0, 0); // signals remote-server location exists
    }


    // ------------------------------------------------------------------------

    void flush();
    void close();
}
