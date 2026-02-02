package fr.elias.oreoEssentials.modules.rtp;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class RtpCrossServerBridge {

    private final OreoEssentials plugin;
    private final PacketManager packets;
    private final String thisServer;

    public RtpCrossServerBridge(OreoEssentials plugin, PacketManager packets, String thisServer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.packets = packets;
        this.thisServer = thisServer == null ? "" : thisServer;

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[RTP-BRIDGE] PacketManager not available; cross-server RTP disabled.");
            return;
        }

        plugin.getLogger().info("[RTP-BRIDGE] Subscribing RtpTeleportRequestPacket on server=" + this.thisServer);

        packets.subscribe(RtpTeleportRequestPacket.class, (channel, pkt) -> {
            if (pkt == null) return;

            plugin.getLogger().info("[RTP-BRIDGE] Received RtpTeleportRequestPacket req="
                    + pkt.getRequestId() + " player=" + pkt.getPlayerId()
                    + " world=" + pkt.getWorldName() + " via " + channel);

            Bukkit.getScheduler().runTask(plugin, () -> handleIncoming(pkt));
        });
    }

    private void handleIncoming(RtpTeleportRequestPacket pkt) {
        UUID playerId = pkt.getPlayerId();
        String worldName = pkt.getWorldName();

        if (playerId == null || worldName == null || worldName.isBlank()) {
            plugin.getLogger().warning("[RTP-BRIDGE] Ignoring invalid RTP packet (missing player/world).");
            return;
        }

        plugin.getRtpPendingService().add(playerId, worldName);
        plugin.getLogger().info("[RTP-BRIDGE] Stored pending RTP for " + playerId + " in world=" + worldName);

        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player pp = Bukkit.getPlayer(playerId);
                if (pp == null || !pp.isOnline()) return;

                String w = plugin.getRtpPendingService().consume(pp.getUniqueId());
                if (w != null && !w.isBlank()) {
                    plugin.getLogger().info("[RTP-BRIDGE] Player already online, executing pending RTP for "
                            + pp.getName() + " in world=" + w);
                    RtpCommand.doLocalRtp(plugin, pp, w);
                }
            }, 1L);
        }
    }

    public void requestCrossServerRtp(Player p, String targetWorld, String targetServer) {
        if (p == null) return;

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[RTP-BRIDGE] requestCrossServerRtp but PacketManager unavailable.");
            p.performCommand("server " + targetServer);
            return;
        }

        if (targetServer == null || targetServer.isBlank()) {
            plugin.getLogger().warning("[RTP-BRIDGE] requestCrossServerRtp called with empty targetServer.");
            return;
        }
        if (targetWorld == null || targetWorld.isBlank()) {
            plugin.getLogger().warning("[RTP-BRIDGE] requestCrossServerRtp called with empty targetWorld.");
            return;
        }

        UUID uuid = p.getUniqueId();
        String req = UUID.randomUUID().toString();

        RtpTeleportRequestPacket pkt = new RtpTeleportRequestPacket(uuid, targetWorld, req);

        packets.sendPacket(PacketChannel.individual(targetServer), pkt);

        plugin.getLogger().info("[RTP-BRIDGE] Sent RtpTeleportRequestPacket req=" + req
                + " player=" + p.getName()
                + " → server=" + targetServer
                + " world=" + targetWorld);

    }
}
