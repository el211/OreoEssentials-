// src/main/java/fr/elias/oreoEssentials/playerwarp/PlayerWarpService.java
package fr.elias.oreoEssentials.playerwarp;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerWarpService {

    private final PlayerWarpStorage storage;
    private final PlayerWarpDirectory directory; // optional

    public PlayerWarpService(PlayerWarpStorage storage, PlayerWarpDirectory directory) {
        this.storage = storage;
        this.directory = directory;

        OreoEssentials.get().getLogger().info(
                "[PlayerWarps/DEBUG] PlayerWarpService init. storage="
                        + storage.getClass().getSimpleName()
                        + " directory="
                        + (directory == null ? "null" : directory.getClass().getSimpleName())
        );
    }


    private static String buildId(UUID owner, String name) {
        return owner.toString() + ":" + name.trim().toLowerCase(Locale.ROOT);
    }

    /* -------- basic ops -------- */

    public PlayerWarp createWarp(Player owner, String name, Location loc) {
        String trimmedName = name.trim().toLowerCase(Locale.ROOT);
        String id = buildId(owner.getUniqueId(), trimmedName);

        if (storage.getById(id) != null) return null; // already exists

        PlayerWarp warp = new PlayerWarp(
                id,
                owner.getUniqueId(),
                trimmedName,
                loc.clone()
        );
        storage.save(warp);

        // NEW: register owning server in directory
        if (directory != null) {
            try {
                String serverName = OreoEssentials.get()
                        .getConfigService()
                        .serverName();
                directory.setWarpServer(id, serverName);
                OreoEssentials.get().getLogger().info(
                        "[PlayerWarps] Registered warp '" + trimmedName
                                + "' on server '" + serverName + "' (id=" + id + ")"
                );
            } catch (Throwable t) {
                OreoEssentials.get().getLogger().warning(
                        "[PlayerWarps] Failed to setWarpServer for id=" + id + ": " + t.getMessage()
                );
            }
        }

        return warp;
    }


    public boolean deleteWarp(PlayerWarp warp) {
        boolean ok = storage.delete(warp.getId());
        if (ok && directory != null) {
            try {
                directory.deleteWarp(warp.getId());
            } catch (Throwable ignored) {}
        }
        return ok;
    }

    public PlayerWarp findByOwnerAndName(UUID owner, String name) {
        return storage.getByOwnerAndName(owner, name.trim().toLowerCase(Locale.ROOT));
    }

    public List<PlayerWarp> listByOwner(UUID owner) {
        return storage.listByOwner(owner);
    }

    public List<PlayerWarp> listAll() {
        return storage.listAll();
    }

    /* -------- permission helpers (directory-backed, optional) -------- */

    public String requiredPermission(PlayerWarp warp) {
        return (directory == null ? null : directory.getWarpPermission(warp.getId()));
    }

    public boolean canUse(Player player, PlayerWarp warp) {
        String perm = requiredPermission(warp);
        return (perm == null || perm.isBlank()) || player.hasPermission(perm);
    }

    /* -------- limit helpers -------- */

    public int getWarpCount(Player owner) {
        return listByOwner(owner.getUniqueId()).size();
    }

    public boolean isUnderLimit(Player owner, int limit) {
        return getWarpCount(owner) < limit;
    }

    public int getLimit(Player player) {
        OreoEssentials plugin = OreoEssentials.get();
        ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("playerwarps.limit");

        if (sec == null) {
            return 3;
        }

        int limit = sec.getInt("default", 3);

        for (String key : sec.getKeys(false)) {
            if (key.equalsIgnoreCase("default")) continue;

            String groupPerm = "group." + key.toLowerCase(Locale.ROOT);
            if (player.hasPermission(groupPerm)) {
                int candidate = sec.getInt(key, limit);
                if (candidate > limit) {
                    limit = candidate;
                }
            }
        }

        return limit;
    }

    public String getWarpServer(PlayerWarp warp, String localServer) {
        OreoEssentials plugin = OreoEssentials.get();

        if (directory == null) {
            plugin.getLogger().info("[PW/DEBUG] getWarpServer: directory=null, using localServer=" + localServer);
            return localServer;
        }

        String s = null;
        try {
            s = directory.getWarpServer(warp.getId());
        } catch (Throwable t) {
            plugin.getLogger().warning("[PW/DEBUG] getWarpServer: directory.getWarpServer threw: " + t.getMessage());
        }

        plugin.getLogger().info("[PW/DEBUG] getWarpServer: warpId=" + warp.getId()
                + " rawDirectoryServer=" + s
                + " localServer=" + localServer);

        return (s == null || s.isBlank()) ? localServer : s;
    }

    /* convenience: list names for GUI/list cmd */
    public List<String> listNames() {
        return listAll().stream()
                .map(PlayerWarp::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public boolean teleportToPlayerWarp(Player player, UUID ownerId, String warpName) {
        // Simple helper if you want to use it elsewhere
        PlayerWarp warp = findByOwnerAndName(ownerId, warpName);
        if (warp == null) {
            player.sendMessage("§cPlayer warp not found.");
            return false;
        }
        try {
            player.teleport(warp.getLocation());
            return true;
        } catch (Exception ex) {
            OreoEssentials.get().getLogger().warning("[PW] teleportToPlayerWarp failed: " + ex.getMessage());
            return false;
        }
    }
}
