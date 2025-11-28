// File: src/main/java/fr/elias/oreoEssentials/rtp/RtpCrossServerBridge.java
package fr.elias.oreoEssentials.rtp;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.core.playercommands.RtpCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class RtpCrossServerBridge {

    private final OreoEssentials plugin;
    private final PacketManager packets;
    private final String thisServer;

    public RtpCrossServerBridge(OreoEssentials plugin,
                                PacketManager packets,
                                String thisServer) {
        this.plugin = plugin;
        this.packets = packets;
        this.thisServer = thisServer;

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[RTP-BRIDGE] PacketManager not available; cross-server RTP disabled.");
            return;
        }

        plugin.getLogger().info("[RTP-BRIDGE] Subscribing RtpTeleportRequestPacket on server=" + thisServer);

        // Listen for teleport requests coming *to this server*
        packets.subscribe(RtpTeleportRequestPacket.class, (channel, pkt) -> {
            plugin.getLogger().info("[RTP-BRIDGE] Received RtpTeleportRequestPacket req="
                    + pkt.getRequestId() + " player=" + pkt.getPlayerId()
                    + " world=" + pkt.getWorldName() + " via " + channel);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(pkt.getPlayerId());
                if (p != null && p.isOnline()) {
                    // Player already online on this server → RTP immediately
                    plugin.getLogger().info("[RTP-BRIDGE] Player online, doing immediate RTP for "
                            + p.getName() + " in world=" + pkt.getWorldName());
                    RtpCommand.doLocalRtp(plugin, p, pkt.getWorldName());
                } else {
                    // Store for PlayerJoinEvent
                    plugin.getRtpPendingService().add(pkt.getPlayerId(), pkt.getWorldName());
                    plugin.getLogger().info("[RTP-BRIDGE] Stored pending RTP for "
                            + pkt.getPlayerId() + " in world=" + pkt.getWorldName());
                }
            });
        });
    }

    /**
     * Called by /rtp on Server A when it decides the RTP must happen on Server B.
     */
    public void requestCrossServerRtp(Player p,
                                      String targetWorld,
                                      String targetServer) {
        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[RTP-BRIDGE] requestCrossServerRtp but PacketManager unavailable.");
            // At least move the player to that server
            p.performCommand("server " + targetServer);
            return;
        }

        UUID uuid  = p.getUniqueId();
        String req = UUID.randomUUID().toString();

        RtpTeleportRequestPacket pkt = new RtpTeleportRequestPacket(uuid, targetWorld, req);

        // 🔥 Send directly to the *individual channel* of the destination server
        packets.sendPacket(
                PacketChannel.individual(targetServer),
                pkt
        );

        plugin.getLogger().info("[RTP-BRIDGE] Sent RtpTeleportRequestPacket req=" + req
                + " player=" + p.getName()
                + " → server=" + targetServer
                + " world=" + targetWorld);

        // Then actually move the player via proxy
        p.performCommand("server " + targetServer);
    }
}
