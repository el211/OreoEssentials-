package fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.handlers;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.packets.PlayerJoinPacket;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;

import java.util.UUID;

public class PlayerJoinPacketHandler implements PacketSubscriber<PlayerJoinPacket> {

    private final OreoEssentials plugin;

    public PlayerJoinPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, PlayerJoinPacket packet) {
        if (packet == null) return;

        UUID uuid = packet.getPlayerId();
        String name = packet.getPlayerName();

        if (uuid == null || name == null || name.isEmpty()) {
            plugin.getLogger().warning("[Rabbit] PlayerJoinPacket missing data (uuid=" + uuid + ", name=" + name + ")");
            return;
        }

        plugin.getLogger().fine("[Rabbit] Join @" + channel + " -> " + name + " (" + uuid + ")");

        OfflinePlayerCache cache = plugin.getOfflinePlayerCache();
        if (cache != null) {
            try {
                cache.add(name, uuid);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Rabbit] Failed to add player to OfflinePlayerCache: " + t.getMessage());
            }
        }
    }
}
