package fr.elias.oreoEssentials.modules.homes.home;

import fr.elias.oreoEssentials.config.ConfigService;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HomeService {
    private final StorageApi storage;
    private final ConfigService config;
    private final HomeDirectory directory;
    private final String localServer;
    private final Logger logger;

    public HomeService(StorageApi storage, ConfigService config, HomeDirectory directory, Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.config = Objects.requireNonNull(config, "config");
        this.directory = directory; // optional
        this.localServer = Objects.requireNonNull(config.serverName(), "serverName");
        this.logger = Objects.requireNonNull(logger, "logger");
    }


    public HomeService(StorageApi storage, ConfigService config, HomeDirectory directory) {
        this(storage, config, directory, Logger.getLogger("OreoEssentials"));
    }

    public boolean setHome(Player player, String name, Location loc) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(loc, "loc");

        String n = normalize(name);

        Set<String> existing = safeSet(storage.homes(player.getUniqueId()));
        int max = config.getMaxHomesFor(player);

        if (!existing.contains(n) && existing.size() >= max) return false;

        boolean ok = storage.setHome(player.getUniqueId(), n, loc);
        if (ok) {
            if (directory != null) {
                try {
                    directory.setHomeServer(player.getUniqueId(), n, localServer);
                } catch (Throwable t) {
                    logger.warning("[HOME] Storage succeeded but directory.setHomeServer failed for "
                            + player.getUniqueId() + "/" + n + ": " + t.getMessage());
                }
            }
        }
        return ok;
    }

    public boolean delHome(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");

        String n = normalize(name);

        boolean ok = storage.delHome(uuid, n);
        if (ok) {
            tryDirectory(() -> directory.deleteHome(uuid, n),
                    "[HOME] Failed to delete directory entry for " + uuid + "/" + n);
        }
        return ok;
    }

    public Location getHome(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        return storage.getHome(uuid, normalize(name));
    }

    public Set<String> homes(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return safeSet(storage.homes(uuid));
    }

    public String homeServer(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");

        if (directory == null) return localServer;

        String s = null;
        try {
            s = directory.getHomeServer(uuid, normalize(name));
        } catch (Throwable t) {
            warnDebug("[HOME] Directory getHomeServer error: " + t.getMessage(), t);
        }
        return (s == null || s.isBlank()) ? localServer : s;
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

        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public String getServer() { return server; }
    }

    public Map<String, StoredHome> listHomes(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        Map<String, StoredHome> m = storage.listHomes(owner);
        return (m == null) ? Collections.emptyMap() : m;
    }


    public Set<String> allHomeNames(UUID owner) {
        Objects.requireNonNull(owner, "owner");

        Set<String> result = new HashSet<>();

        if (directory != null) {
            try {
                Set<String> directoryNames = directory.listHomes(owner);
                debug("[HOME] Directory returned: " + directoryNames + " for " + owner);
                if (directoryNames != null && !directoryNames.isEmpty()) {
                    for (String s : directoryNames) {
                        if (s != null && !s.isBlank()) result.add(normalize(s));
                    }
                }
            } catch (Throwable t) {
                warnDebug("[HOME] Directory listHomes error: " + t.getMessage(), t);
            }
        } else {
            debug("[HOME] Directory is NULL");
        }

        try {
            Set<String> local = storage.homes(owner);
            if (local != null && !local.isEmpty()) {
                for (String s : local) {
                    if (s != null && !s.isBlank()) result.add(normalize(s));
                }
            }
        } catch (Throwable t) {
            warnDebug("[HOME] Local homes() error: " + t.getMessage(), t);
        }

        debug("[HOME] Final result: " + result);
        return result;
    }

    public Map<String, String> homeServers(UUID owner) {
        Objects.requireNonNull(owner, "owner");

        Map<String, StoredHome> m = listHomes(owner);
        if (m.isEmpty()) return Collections.emptyMap();

        Map<String, String> out = new HashMap<>();
        for (var e : m.entrySet()) {
            String key = (e.getKey() == null) ? null : normalize(e.getKey());
            if (key == null || key.isBlank()) continue;

            StoredHome h = e.getValue();
            String srv = (h == null || h.getServer() == null || h.getServer().isBlank())
                    ? localServer
                    : h.getServer();

            out.put(key, srv);
        }
        return out;
    }

    public String localServer() {
        return localServer;
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> safeSet(Set<String> in) {
        return (in == null) ? Collections.emptySet() : in;
    }

    private void tryDirectory(Runnable op, String onErrorMsg) {
        if (directory == null) return;
        try {
            op.run();
        } catch (Throwable t) {
            warnDebug(onErrorMsg + ": " + t.getMessage(), t);
        }
    }

    private void debug(String msg) {
        if (isDebugEnabled()) {
            logger.info(msg);
        }
    }

    private void warnDebug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            logger.log(Level.WARNING, msg, t);
        }
    }

    private boolean isDebugEnabled() {
        return config.isDebugEnabled();
    }
}
