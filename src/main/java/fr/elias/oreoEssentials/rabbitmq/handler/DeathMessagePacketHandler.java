package fr.elias.oreoEssentials.rabbitmq.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.DeathMessagePacket;
import org.bukkit.Bukkit;

public class DeathMessagePacketHandler implements PacketSubscriber<DeathMessagePacket> {

    private final OreoEssentials plugin;

    public DeathMessagePacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, DeathMessagePacket packet) {
        // Defensive null checks in case upstream sent malformed data
        if (packet == null) return;

        String message = packet.getMessage();
        String sourceServer = packet.getSourceServer();
        String deadPlayerName = packet.getDeadPlayerName();

        // Sanity check
        if (message == null || message.isEmpty()) {
            plugin.getLogger().warning("[Rabbit] DeathMessagePacket missing message data");
            return;
        }

        String localServer = plugin.getConfigService().serverName();

        // Don't broadcast on the server where death occurred (already shown locally via event)
        if (sourceServer != null && sourceServer.equalsIgnoreCase(localServer)) {
            plugin.getLogger().fine("[Rabbit] Death @" + channel + " -> " + deadPlayerName + " (local server, skipping broadcast)");
            return;
        }

        plugin.getLogger().fine("[Rabbit] Death @" + channel + " from=" + sourceServer + " -> " + deadPlayerName);

        // Broadcast message to all players on this server
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(message);
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[Rabbit] Failed to broadcast death message: " + t.getMessage());
        }
    }
}