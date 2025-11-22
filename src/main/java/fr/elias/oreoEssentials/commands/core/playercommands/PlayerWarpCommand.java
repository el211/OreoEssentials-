// src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/PlayerWarpCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpCrossServerBroker;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlayerWarpCommand implements OreoCommand {

    private final PlayerWarpService service;

    public PlayerWarpCommand(PlayerWarpService service) {
        this.service = service;
    }

    @Override public String name() { return "pw"; }
    @Override public List<String> aliases() { return List.of("playerwarp", "pwarp"); }
    @Override public String permission() { return "oe.pw.base"; }
    @Override public String usage() { return "help|set|remove|list|<warp>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();

        Player actor = (sender instanceof Player p) ? p : null;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "set" -> {
                if (actor == null) {
                    sender.sendMessage("§cOnly players can set player warps.");
                    return true;
                }
                if (!actor.hasPermission("oe.pw.set")) {
                    Lang.send(actor, "pw.no-permission-set", Map.of(), actor);
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /pw set <name>");
                    return true;
                }
                String name = args[1];

                int limit = plugin.getConfig().getInt("playerwarps.limit.default", 3);
                if (!service.isUnderLimit(actor, limit) &&
                        !actor.hasPermission("oe.pw.limit.bypass")) {
                    Lang.send(actor, "pw.limit-reached",
                            Map.of("limit", String.valueOf(limit)),
                            actor);
                    return true;
                }

                Location loc = actor.getLocation();
                PlayerWarp created = service.createWarp(actor, name, loc);
                if (created == null) {
                    Lang.send(actor, "pw.already-exists",
                            Map.of("name", name.toLowerCase(Locale.ROOT)),
                            actor);
                } else {
                    Lang.send(actor, "pw.set-success",
                            Map.of("name", created.getName()),
                            actor);
                }
                return true;
            }

            case "remove" -> {
                if (actor == null) {
                    sender.sendMessage("§cOnly players can remove their own warps for now.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /pw remove <warp>");
                    return true;
                }
                String name = args[1];
                PlayerWarp warp = service.findByOwnerAndName(actor.getUniqueId(), name);
                if (warp == null) {
                    Lang.send(actor, "pw.not-found-owner",
                            Map.of("name", name.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }
                if (!actor.hasPermission("oe.pw.remove") &&
                        !actor.hasPermission("oe.pw.admin.remove")) {
                    Lang.send(actor, "pw.no-permission-remove", Map.of(), actor);
                    return true;
                }
                if (service.deleteWarp(warp)) {
                    Lang.send(actor, "pw.remove-success",
                            Map.of("name", warp.getName()),
                            actor);
                } else {
                    Lang.send(actor, "pw.remove-failed",
                            Map.of("name", warp.getName()),
                            actor);
                }
                return true;
            }

            case "list" -> {
                if (args.length >= 2) {
                    // /pw list <player>
                    String targetName = args[1];
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        sender.sendMessage("§cPlayer '" + targetName + "' is not online (for now only online lookups).");
                        return true;
                    }
                    var warps = service.listByOwner(target.getUniqueId());
                    sendList(sender, target.getName(), warps);
                } else {
                    // self list or global
                    if (actor == null) {
                        // console -> global list
                        var warps = service.listAll();
                        sendList(sender, "ALL", warps);
                    } else {
                        var warps = service.listByOwner(actor.getUniqueId());
                        sendList(sender, actor.getName(), warps);
                    }
                }
                return true;
            }

            default -> {
                // /pw <warp> -> teleport to player warp by name
                if (actor == null) {
                    sender.sendMessage("§cOnly players can use /pw <warp>.");
                    return true;
                }
                String name = args[0];

                // very simple: search globally by name (first found)
                PlayerWarp targetWarp = service.listAll().stream()
                        .filter(w -> w.getName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null);

                if (targetWarp == null) {
                    Lang.send(actor, "pw.not-found",
                            Map.of("name", name.toLowerCase(Locale.ROOT)),
                            actor);
                    return true;
                }

                // permission check
                if (!service.canUse(actor, targetWarp)) {
                    Lang.send(actor, "pw.no-permission-warp",
                            Map.of("name", targetWarp.getName()),
                            actor);
                    return true;
                }

                // ---- DEBUG: warp info ----
                plugin.getLogger().info("[PW/DEBUG] Player " + actor.getName()
                        + " requested warp '" + name + "'. Resolved warp: id=" + targetWarp.getId()
                        + " owner=" + targetWarp.getOwner()
                        + " name=" + targetWarp.getName()
                        + " loc=" + safeLocString(targetWarp.getLocation()));

                // Determine local vs remote server
                final String localServer = plugin.getConfigService().serverName();
                String targetServer = service.getWarpServer(targetWarp, localServer);

                var cs = plugin.getCrossServerSettings();
                boolean crossEnabled = cs != null && cs.warps();
                boolean sameServer = (targetServer == null)
                        || targetServer.equalsIgnoreCase(localServer);
                boolean messagingAvailable = plugin.isMessagingAvailable();

                // ---- DEBUG: routing decision ----
                plugin.getLogger().info("[PW/DEBUG] localServer=" + localServer
                        + " targetServer=" + targetServer
                        + " crossEnabled=" + crossEnabled
                        + " messagingAvailable=" + messagingAvailable
                        + " sameServer=" + sameServer);

                if (!crossEnabled) {
                    plugin.getLogger().info("[PW/DEBUG] Cross-server warps disabled in CrossServerSettings.warps(). Using LOCAL teleport.");
                }
                if (!messagingAvailable) {
                    plugin.getLogger().info("[PW/DEBUG] Messaging not available (PacketManager null or not initialized). Using LOCAL teleport.");
                }
                if (sameServer) {
                    plugin.getLogger().info("[PW/DEBUG] Target warp server equals local server (or null). Using LOCAL teleport.");
                }

                // If cross-server disabled, or no target server, or same server, or no messaging → local teleport
                if (!crossEnabled || sameServer || !messagingAvailable) {
                    teleportLocal(plugin, actor, targetWarp);
                    return true;
                }

                // CROSS-SERVER path via PlayerWarpCrossServerBroker
                try {
                    plugin.getLogger().info("[PW/DEBUG] Using PlayerWarpCrossServerBroker for cross-server teleport. from="
                            + localServer + " to=" + targetServer);

                    PlayerWarpCrossServerBroker broker = new PlayerWarpCrossServerBroker(
                            plugin,
                            service,
                            plugin.getPacketManager(),
                            plugin.getProxyMessenger(),
                            localServer
                    );

                    UUID ownerId = targetWarp.getOwner();
                    String warpName = targetWarp.getName();

                    plugin.getLogger().info("[PW/DEBUG] Broker.requestCrossServerTeleport("
                            + "player=" + actor.getUniqueId()
                            + ", owner=" + ownerId
                            + ", warp='" + warpName + "'"
                            + ", targetServer=" + targetServer + ")");

                    broker.requestCrossServerTeleport(
                            actor,
                            ownerId,
                            warpName,
                            targetServer
                    );

                    Lang.send(actor, "pw.sending",
                            Map.of("server", targetServer, "warp", warpName),
                            actor);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[PW] Cross-server teleport failed: " + ex.getMessage());
                    ex.printStackTrace();
                    Lang.send(actor, "pw.messaging-disabled",
                            Map.of("server", targetServer, "warp", targetWarp.getName()),
                            actor);
                }

                return true;
            }
        }
    }

    // ----------------- Helpers -----------------

    private void teleportLocal(OreoEssentials plugin, Player actor, PlayerWarp targetWarp) {
        try {
            Location loc = targetWarp.getLocation();
            plugin.getLogger().info("[PW/DEBUG] LOCAL teleport for player " + actor.getName()
                    + " to warp id=" + targetWarp.getId()
                    + " world=" + (loc == null || loc.getWorld() == null ? "null" : loc.getWorld().getName())
                    + " xyz=" + safeLocString(loc));

            // If you have a helper:
            // service.teleportToPlayerWarp(actor, targetWarp.getOwner(), targetWarp.getName());
            actor.teleport(loc);

            Lang.send(actor, "pw.teleported",
                    Map.of("name", targetWarp.getName()),
                    actor);
        } catch (Exception ex) {
            plugin.getLogger().warning("[PW/DEBUG] LOCAL teleport failed: " + ex.getMessage());
            ex.printStackTrace();
            Lang.send(actor, "pw.teleport-failed",
                    Map.of("error", ex.getMessage() == null ? "unknown" : ex.getMessage()),
                    actor);
        }
    }

    private String safeLocString(Location loc) {
        if (loc == null) return "null";
        String world = (loc.getWorld() == null ? "null" : loc.getWorld().getName());
        return world + "@" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§8§m------------------------");
        s.sendMessage("§b/pw help §7- Show this help.");
        s.sendMessage("§b/pw set <name> §7- Set a warp at your location.");
        s.sendMessage("§b/pw <warp> §7- Teleport to a player warp.");
        s.sendMessage("§b/pw list [player] §7- List player warps.");
        s.sendMessage("§b/pw remove <warp> §7- Remove your warp.");
        s.sendMessage("§8§m------------------------");
    }

    private void sendList(CommandSender sender, String ownerName, List<PlayerWarp> warps) {
        sender.sendMessage("§8§m------------------------");
        sender.sendMessage("§bPlayer warps for §e" + ownerName + "§7:");
        if (warps.isEmpty()) {
            sender.sendMessage("§7  (none)");
        } else {
            for (PlayerWarp w : warps) {
                sender.sendMessage("§7- §a" + w.getName());
            }
        }
        sender.sendMessage("§8§m------------------------");
    }
}
