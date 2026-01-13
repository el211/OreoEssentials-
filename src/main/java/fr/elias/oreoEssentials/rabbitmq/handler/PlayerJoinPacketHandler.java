package fr.elias.oreoEssentials.rabbitmq.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket;

import java.util.UUID;

public class PlayerJoinPacketHandler implements PacketSubscriber<PlayerJoinPacket> {

    private final OreoEssentials plugin;

    public PlayerJoinPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, PlayerJoinPacket packet) {
        // Defensive null checks in case upstream sent malformed data
        if (packet == null) return;

        UUID uuid = packet.getPlayerId();
        String name = packet.getPlayerName();

        // Optional: small sanity check to avoid polluting cache
        if (uuid == null || name == null || name.isEmpty()) {
            plugin.getLogger().warning("[Rabbit] PlayerJoinPacket missing data (uuid=" + uuid + ", name=" + name + ")");
            return;
        }

        plugin.getLogger().fine("[Rabbit] Join @" + channel + " -> " + name + " (" + uuid + ")");

        //  Guarded cache access (getter is null-safe but we still guard)
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
