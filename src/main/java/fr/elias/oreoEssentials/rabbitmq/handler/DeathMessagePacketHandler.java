package fr.elias.oreoEssentials.rabbitmq.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.DeathMessagePacket;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;

public class DeathMessagePacketHandler implements PacketSubscriber<DeathMessagePacket> {

    private final OreoEssentials plugin;

    public DeathMessagePacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, DeathMessagePacket packet) {
        if (packet == null) return;

        String message = packet.getMessage();
        String sourceServer = packet.getSourceServer();
        String deadPlayerName = packet.getDeadPlayerName();

        if (message == null || message.isEmpty()) {
            plugin.getLogger().warning("[Rabbit] DeathMessagePacket missing message data");
            return;
        }

        String localServer = plugin.getConfigService().serverName();

        if (sourceServer != null && sourceServer.equalsIgnoreCase(localServer)) {
            plugin.getLogger().fine("[Rabbit] Death @" + channel + " -> " + deadPlayerName + " (local server, skipping broadcast)");
            return;
        }

        plugin.getLogger().fine("[Rabbit] Death @" + channel + " from=" + sourceServer + " -> " + deadPlayerName);

        try {
            OreScheduler.run(plugin, () -> {
                Bukkit.broadcastMessage(message);
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[Rabbit] Failed to broadcast death message: " + t.getMessage());
        }
    }
}