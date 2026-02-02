package fr.elias.oreoEssentials.modules.playerwarp;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.PlayerWarpTeleportRequestPacket;
import fr.elias.oreoEssentials.util.Lang;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.stream.Collectors;


public class PlayerWarpService {

    private final PlayerWarpStorage storage;
    private final PlayerWarpDirectory directory; // optional

    public PlayerWarpService(PlayerWarpStorage storage, PlayerWarpDirectory directory) {
        this.storage = storage;
        this.directory = directory;

        OreoEssentials.get().getLogger().info(
                "[PlayerWarps] Service init. storage="
                        + storage.getClass().getSimpleName()
                        + " directory="
                        + (directory == null ? "null" : directory.getClass().getSimpleName())
        );
    }

    private static String buildId(UUID owner, String name) {
        return owner.toString() + ":" + name.trim().toLowerCase(Locale.ROOT);
    }


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
            } catch (Throwable ignored) {
            }
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


    public String requiredPermission(PlayerWarp warp) {
        return (directory == null ? null : directory.getWarpPermission(warp.getId()));
    }

    public boolean canUse(Player player, PlayerWarp warp) {
        String perm = requiredPermission(warp);
        if (perm != null && !perm.isBlank() && !player.hasPermission(perm)) {
            return false;
        }

        UUID uuid = player.getUniqueId();

        if (warp.getOwner().equals(uuid)) {
            return true;
        }

        if (warp.getManagers() != null && warp.getManagers().contains(uuid)) {
            return true;
        }

        if (player.hasPermission("oe.pw.bypass.lock")) {
            return true;
        }

        if (warp.isLocked()) {
            return false;
        }

        if (warp.isWhitelistEnabled()) {
            Set<UUID> wl = warp.getWhitelist();
            return wl != null && wl.contains(uuid);
        }

        return true;
    }


    public int getWarpCount(Player owner) {
        return listByOwner(owner.getUniqueId()).size();
    }

    public void saveWarp(PlayerWarp warp) {
        storage.save(warp);
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
            return localServer;
        }

        String s = null;
        try {
            s = directory.getWarpServer(warp.getId());
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "[PlayerWarps] getWarpServer failed for id=" + warp.getId() + ": " + t.getMessage()
            );
        }

        return (s == null || s.isBlank()) ? localServer : s;
    }

    public List<String> listNames() {
        return listAll().stream()
                .map(PlayerWarp::getName)
                .sorted()
                .collect(Collectors.toList());
    }


    public boolean teleportToPlayerWarp(Player player, UUID ownerId, String warpName) {
        OreoEssentials plugin = OreoEssentials.get();
        String trimmed = warpName.trim();
        String lower   = trimmed.toLowerCase(Locale.ROOT);

        PlayerWarp warp = findByOwnerAndName(ownerId, lower);
        if (warp == null) {
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] teleportToPlayerWarp: warp not found for owner="
                    + ownerId + " name=" + lower);
            Lang.send(player, "playerwarps.teleport.not-found",
                    "<red>Warp <yellow>%name%</yellow> was not found.</red>",
                    Map.of("name", trimmed));
            return false;
        }

        if (!warp.getOwner().equals(ownerId)) {
            plugin.getLogger().warning("[OreoEssentials] [PW/SECURITY] Mismatch: storage returned warp id="
                    + warp.getId() + " owner=" + warp.getOwner()
                    + " for requested ownerId=" + ownerId + ". Blocking teleport.");
            Lang.send(player, "playerwarps.teleport.not-found",
                    "<red>Warp <yellow>%name%</yellow> was not found.</red>",
                    Map.of("name", trimmed));
            return false;
        }

        if (!canUse(player, warp)) {
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] teleportToPlayerWarp: player "
                    + player.getName() + " lacks permission for warp id=" + warp.getId());
            Lang.send(player, "playerwarps.teleport.no-permission",
                    "<red>You don't have permission to use <yellow>%name%</yellow>.</red>",
                    Map.of("name", warp.getName()));
            return false;
        }

        double cost = warp.getCost();
        if (cost > 0
                && !player.getUniqueId().equals(warp.getOwner())           // owner = free
                && !player.hasPermission("oe.pw.bypass.cost")) {           // bypass = free

            Economy eco = plugin.getVaultEconomy();

            if (eco == null) {
                plugin.getLogger().warning("[OreoEssentials] [PW] Economy not available; can't charge cost for warp " + warp.getId());
            } else {
                if (!eco.has(player, cost)) {
                    Lang.send(player, "pw.cost-not-enough",
                            "<red>Not enough balance (<white>$%amount%</white> required).</red>",
                            Map.of("amount", String.valueOf(cost)));
                    return false;
                }

                eco.withdrawPlayer(player, cost);

                OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(warp.getOwner());
                if (owner != null) {
                    eco.depositPlayer(owner, cost);
                }

                String ownerName = (owner != null && owner.getName() != null) ? owner.getName() : "Unknown";
                Lang.send(player, "pw.cost-charged",
                        "<gray>Charged</gray> <yellow>$%amount%</yellow> <gray>(paid to</gray> <white>%owner%</white><gray>).</gray>",
                        Map.of("amount", String.valueOf(cost), "owner", ownerName));
            }
        }

        String localServer   = plugin.getConfigService().serverName();
        String targetServer  = getWarpServer(warp, localServer);
        boolean crossEnabled = plugin.getCrossServerSettings().warps();
        boolean messaging    = plugin.isMessagingAvailable();
        boolean sameServer   = localServer.equalsIgnoreCase(targetServer);

        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] Player " + player.getName()
                + " requested warp '" + trimmed + "'. Resolved warp: id=" + warp.getId()
                + " owner=" + warp.getOwner()
                + " name=" + warp.getName()
                + " loc=" + formatLocation(warp.getLocation()));
        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] localServer=" + localServer
                + " targetServer=" + targetServer
                + " crossEnabled=" + crossEnabled
                + " messagingAvailable=" + messaging
                + " sameServer=" + sameServer);

        if (!crossEnabled || !messaging || sameServer) {
            if (!crossEnabled) {
                plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] Cross-server playerwarps disabled in config.");
                Lang.send(player, "playerwarps.teleport.cross-disabled",
                        "<gray>Cross-server warps are disabled. Teleporting locally to <white>%name%</white>.</gray>",
                        Map.of("name", warp.getName()));
            } else if (!messaging) {
                plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] Messaging unavailable; falling back to local teleport.");
                Lang.send(player, "playerwarps.teleport.messaging-disabled",
                        "<gray>Messaging offline. Teleporting locally to <white>%name%</white>.</gray>",
                        Map.of("name", warp.getName()));
            } else {
                plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] Target warp server equals local server. Using LOCAL teleport.");
            }

            Location loc = warp.getLocation().clone();
            plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] LOCAL teleport for player "
                    + player.getName() + " to warp id=" + warp.getId()
                    + " world=" + loc.getWorld().getName()
                    + " xyz=" + formatLocation(loc));

            boolean ok = player.teleport(loc);
            if (ok) {
                Lang.send(player, "playerwarps.teleport.local",
                        "<green>Teleported to warp</green> <yellow>%name%</yellow>.",
                        Map.of("name", warp.getName()));
            } else {
                plugin.getLogger().warning("[OreoEssentials] [PW] Teleport failed for player "
                        + player.getName() + " to warp id=" + warp.getId());
            }
            return ok;
        }

        plugin.getLogger().info("[OreoEssentials] [PW/DEBUG] Using cross-server teleport. from="
                + localServer + " to=" + targetServer);

        Lang.send(player, "playerwarps.teleport.cross-send",
                "<gray>Sending you to server</gray> <yellow>%server%</yellow> <gray>for warp</gray> <white>%name%</white>…",
                Map.of("name", warp.getName(), "server", targetServer));

        var pm = plugin.getPacketManager();
        if (pm == null || !pm.isInitialized()) {
            plugin.getLogger().warning("[OreoEssentials] [PW] PacketManager unavailable during cross-server playerwarp teleport.");
            Lang.send(player, "playerwarps.teleport.messaging-disabled",
                    "<gray>Messaging offline. Teleporting locally to <white>%name%</white>.</gray>",
                    Map.of("name", warp.getName()));
            return false;
        }

        String requestId = UUID.randomUUID().toString();

        PlayerWarpTeleportRequestPacket pkt = new PlayerWarpTeleportRequestPacket(
                player.getUniqueId(),
                ownerId,
                warp.getName(),
                targetServer,
                requestId
        );

        pm.sendPacket(PacketChannels.GLOBAL, pkt);
        plugin.getProxyMessenger().sendToServer(player, targetServer);

        return true;
    }

    private static String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName()
                + "@" + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ();
    }


    public boolean addToWhitelist(Player owner, String warpName, OfflinePlayer target) {
        String trimmed = warpName.trim();
        String lower   = trimmed.toLowerCase(Locale.ROOT);

        PlayerWarp warp = findByOwnerAndName(owner.getUniqueId(), lower);
        if (warp == null) {
            Lang.send(owner, "playerwarps.whitelist.not-found",
                    "<red>Warp <yellow>%name%</yellow> not found.</red>",
                    Map.of("name", trimmed));
            return false;
        }

        if (!warp.isWhitelistEnabled()) {
            Lang.send(owner, "playerwarps.whitelist.not-enabled",
                    "<red>Whitelist is not enabled for <yellow>%name%</yellow>.</red>",
                    Map.of("name", warp.getName()));
            return false;
        }

        if (target == null || target.getUniqueId() == null) {
            Lang.send(owner, "playerwarps.whitelist.player-not-found",
                    "<red>Target player not found.</red>",
                    Map.of("target", "null"));
            return false;
        }

        if (warp.getWhitelist().contains(target.getUniqueId())) {
            Lang.send(owner, "playerwarps.whitelist.already",
                    "<yellow>%target%</yellow> is already whitelisted on <white>%name%</white>.",
                    Map.of("name", warp.getName(), "target", target.getName()));
            return false;
        }

        warp.getWhitelist().add(target.getUniqueId());
        saveWarp(warp);

        Lang.send(owner, "playerwarps.whitelist.added",
                "<green>Added</green> <yellow>%target%</yellow> <green>to whitelist of</green> <white>%name%</white>.",
                Map.of("name", warp.getName(), "target", target.getName()));
        return true;
    }


    public boolean removeFromWhitelist(Player owner, String warpName, OfflinePlayer target) {
        String trimmed = warpName.trim();
        String lower   = trimmed.toLowerCase(Locale.ROOT);

        PlayerWarp warp = findByOwnerAndName(owner.getUniqueId(), lower);
        if (warp == null) {
            Lang.send(owner, "playerwarps.whitelist.not-found",
                    "<red>Warp <yellow>%name%</yellow> not found.</red>",
                    Map.of("name", trimmed));
            return false;
        }

        if (!warp.isWhitelistEnabled()) {
            Lang.send(owner, "playerwarps.whitelist.not-enabled",
                    "<red>Whitelist is not enabled for <yellow>%name%</yellow>.</red>",
                    Map.of("name", warp.getName()));
            return false;
        }

        if (target == null || target.getUniqueId() == null
                || !warp.getWhitelist().contains(target.getUniqueId())) {
            Lang.send(owner, "playerwarps.whitelist.not-in",
                    "<red><yellow>%target%</yellow> is not on the whitelist of <white>%name%</white>.</red>",
                    Map.of("name", warp.getName(), "target", target != null ? target.getName() : "null"));
            return false;
        }

        warp.getWhitelist().remove(target.getUniqueId());
        saveWarp(warp);

        Lang.send(owner, "playerwarps.whitelist.removed",
                "<green>Removed</green> <yellow>%target%</yellow> <green>from whitelist of</green> <white>%name%</white>.",
                Map.of("name", warp.getName(), "target", target.getName()));
        return true;
    }
}