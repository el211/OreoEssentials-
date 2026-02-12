package fr.elias.oreoEssentials.modules.playerwarp;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.warps.rabbit.packets.PlayerWarpTeleportRequestPacket;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class PlayerWarpCrossServerBroker implements Listener {

    private final OreoEssentials plugin;
    private final PlayerWarpService playerWarpService;
    private final PacketManager packetManager;
    private final ProxyMessenger proxyMessenger;
    private final String localServerName;

    private final Map<UUID, PendingWarp> pending = new ConcurrentHashMap<>();

    public PlayerWarpCrossServerBroker(OreoEssentials plugin,
                                       PlayerWarpService playerWarpService,
                                       PacketManager packetManager,
                                       ProxyMessenger proxyMessenger,
                                       String localServerName) {
        this.plugin = plugin;
        this.playerWarpService = playerWarpService;
        this.packetManager = packetManager;
        this.proxyMessenger = proxyMessenger;
        this.localServerName = localServerName;

        this.packetManager.subscribe(
                PlayerWarpTeleportRequestPacket.class,
                (channel, packet) -> handleIncoming(packet)
        );

        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getLogger().info("[PW] PlayerWarpCrossServerBroker ready on server=" + localServerName);
    }

    /**
     * Called by /pw command when the selected warp is on a different server.
     */
    public void requestCrossServerTeleport(Player player,
                                           UUID ownerId,
                                           String warpName,
                                           String targetServer) {
        String requestId = java.util.UUID.randomUUID().toString();

        PlayerWarpTeleportRequestPacket pkt = new PlayerWarpTeleportRequestPacket(
                player.getUniqueId(),
                ownerId,
                warpName,
                targetServer,
                requestId
        );

        packetManager.sendPacket(
                PacketChannel.individual(targetServer),
                pkt
        );

        proxyMessenger.connect(player, targetServer);

        plugin.getLogger().info("[PW/XSRV] Sent teleport request " + requestId
                + " player=" + player.getName()
                + " warp=" + warpName
                + " owner=" + ownerId
                + " -> server=" + targetServer);
    }

    /**
     * Handles packets on the TARGET server.
     */
    private void handleIncoming(PlayerWarpTeleportRequestPacket pkt) {
        if (pkt == null) return;

        if (pkt.getTargetServer() != null
                && !pkt.getTargetServer().isBlank()
                && !pkt.getTargetServer().equalsIgnoreCase(localServerName)) {
            return;
        }

        UUID playerId = pkt.getPlayerId();
        UUID ownerId = pkt.getOwnerId();
        String warpName = pkt.getWarpName();

        plugin.getLogger().info("[PW/XSRV@" + localServerName + "] Received teleport request "
                + pkt.getRequestId()
                + " for player=" + playerId
                + " warp=" + warpName);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                safeTeleport(p, ownerId, warpName);
            } else {
                // Store for when player actually joins
                pending.put(playerId, new PendingWarp(ownerId, warpName));
                plugin.getLogger().info("[PW/XSRV@" + localServerName + "] Player not online yet, "
                        + "queued pending warp " + warpName);
            }
        });
    }

    private void safeTeleport(Player p, UUID ownerId, String warpName) {
        try {
            // PlayerWarpService handles all user messages
            boolean ok = playerWarpService.teleportToPlayerWarp(p, ownerId, warpName);
            if (!ok) {
                plugin.getLogger().warning("[PW/XSRV@" + localServerName + "] Failed to teleport "
                        + p.getName() + " to player warp " + warpName + " (owner=" + ownerId + ")");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PW/XSRV@" + localServerName + "] Error teleporting "
                    + p.getName() + " to warp " + warpName + ": " + t.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        PendingWarp pw = pending.remove(uuid);
        if (pw == null) return;

        Bukkit.getScheduler().runTask(plugin, () ->
                safeTeleport(e.getPlayer(), pw.ownerId(), pw.warpName())
        );
    }

    private record PendingWarp(UUID ownerId, String warpName) {}
}