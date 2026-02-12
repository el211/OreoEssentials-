package fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.handlers;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.packets.PlayerQuitPacket;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;

import java.util.UUID;

public class PlayerQuitPacketHandler implements PacketSubscriber<PlayerQuitPacket> {

    private final OreoEssentials plugin;

    public PlayerQuitPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, PlayerQuitPacket packet) {
        UUID playerId = packet.getPlayerId();

        if (playerId == null) {
            plugin.getLogger().warning("[OreoEssentials] ⚠ Received PlayerQuitPacket with null UUID! Skipping removal.");
            return;
        }

        if (plugin.getOfflinePlayerCache().contains(playerId)) {
            plugin.getOfflinePlayerCache().remove(playerId);
            plugin.getLogger().info("📨 Received PlayerQuitPacket: " + playerId + " (removed from cache)");
        } else {
            plugin.getLogger().warning("⚠ Received PlayerQuitPacket for unknown UUID: " + playerId + " — not in cache.");
        }
    }
}

