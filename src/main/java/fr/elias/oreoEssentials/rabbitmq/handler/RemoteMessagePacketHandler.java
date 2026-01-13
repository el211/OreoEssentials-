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
        UUID targetId = packet.getTargetId();
        String message = packet.getMessage();

        //  Check for null or empty message/UUID
        if (targetId == null || message == null || message.trim().isEmpty()) {
            Bukkit.getLogger().warning("[OreoEssentials] ⚠ Received invalid SendRemoteMessagePacket: targetId or message is null/empty.");
            return;
        }

        Player player = Bukkit.getPlayer(targetId);

        if (player != null && player.isOnline()) {
            player.sendMessage(message);
            Bukkit.getLogger().info("[OreoEssentials]  Delivered remote message to " + player.getName() + ": " + message);
        } else {
            Bukkit.getLogger().warning("[OreoEssentials] ⚠ Failed to deliver remote message to UUID " + targetId
                    + " — player not found or offline on this server.");
        }
    }
}
