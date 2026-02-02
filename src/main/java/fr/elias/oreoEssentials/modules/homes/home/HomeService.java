package fr.elias.oreoEssentials.modules.homes.home;

import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class HomeService {
    private final StorageApi storage;
    private final ConfigService config;
    private final HomeDirectory directory;
    private final String localServer;

    public HomeService(StorageApi storage, ConfigService config, HomeDirectory directory) {
        this.storage = storage;
        this.config = config;
        this.directory = directory;
        this.localServer = config.serverName();
    }

    public boolean setHome(Player player, String name, Location loc) {
        String n = name.toLowerCase();
        Set<String> existing = homes(player.getUniqueId());
        int max = config.getMaxHomesFor(player);
        if (!existing.contains(n) && existing.size() >= max) return false;

        boolean ok = storage.setHome(player.getUniqueId(), n, loc);
        if (ok && directory != null) {
            directory.setHomeServer(player.getUniqueId(), n, localServer);
        }
        return ok;
    }

    public boolean delHome(UUID uuid, String name) {
        boolean ok = storage.delHome(uuid, name);
        if (ok && directory != null) directory.deleteHome(uuid, name);
        return ok;
    }

    public Location getHome(UUID uuid, String name) {
        return storage.getHome(uuid, name);
    }

    public Set<String> homes(UUID uuid) {
        return storage.homes(uuid);
    }

    public String homeServer(UUID uuid, String name) {
        if (directory == null) return localServer;
        String s = directory.getHomeServer(uuid, name);
        return s == null ? localServer : s;
    }

    public static final class StoredHome {
        private final String world;
        private final double x, y, z;
        private final String server;

        public StoredHome(String world, double x, double y, double z, String server) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.server = server;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public String getServer() {
            return server;
        }
    }

    public Map<String, StoredHome> listHomes(UUID owner) {
        return storage.listHomes(owner);
    }

    public Set<String> allHomeNames(UUID owner) {
        Set<String> result = new HashSet<>();

        if (directory != null) {
            try {
                Set<String> directoryNames = directory.listHomes(owner);
                Bukkit.getLogger().info("[HOME/DEBUG] Directory returned: " + directoryNames + " for " + owner);
                if (directoryNames != null && !directoryNames.isEmpty()) {
                    result.addAll(directoryNames);
                }
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[HOME/DEBUG] Directory error: " + t.getMessage());
                t.printStackTrace();
            }
        } else {
            Bukkit.getLogger().warning("[HOME/DEBUG] Directory is NULL!");
        }

        try {
            Set<String> local = homes(owner);
            if (local != null) {
                result.addAll(local);
            }
        } catch (Throwable ignored) {
        }

        Bukkit.getLogger().info("[HOME/DEBUG] Final result: " + result);
        return result;
    }

    public Map<String, String> homeServers(UUID owner) {
        Map<String, StoredHome> m = listHomes(owner);
        if (m == null) return Collections.emptyMap();
        Map<String, String> out = new HashMap<>();
        for (var e : m.entrySet()) {
            String srv = (e.getValue() == null || e.getValue().getServer() == null)
                    ? localServer : e.getValue().getServer();
            out.put(e.getKey(), srv);
        }
        return out;
    }

    public String localServer() {
        return localServer;
    }
}