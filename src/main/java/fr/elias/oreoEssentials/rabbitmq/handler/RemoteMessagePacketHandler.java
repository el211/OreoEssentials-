package fr.elias.oreoEssentials.rabbitmq.handler;

import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RemoteMessagePacketHandler implements PacketSubscriber<SendRemoteMessagePacket> {

    @Override
    public void onReceive(PacketChannel channel, SendRemoteMessagePacket packet) {

        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("OreoEssentials"),
                () -> {
                    UUID targetId = packet.getTargetId();
                    String message = packet.getMessage();

                    if (targetId == null || message == null || message.trim().isEmpty()) {
                        Bukkit.getLogger().warning("[OreoEssentials] ⚠ Received invalid SendRemoteMessagePacket: targetId or message is null/empty.");
                        return;
                    }

                    Player player = Bukkit.getPlayer(targetId);

                    if (player != null && player.isOnline()) {
                        player.sendMessage(message);
                        Bukkit.getLogger().info("[OreoEssentials] ✓ Delivered remote message to " + player.getName() + ": " + message);
                    } else {
                        Bukkit.getLogger().info("[OreoEssentials] ⚠ Player not on this server: " + targetId
                                + " (normal if player is on different server)");
                    }
                }
        );
    }
}