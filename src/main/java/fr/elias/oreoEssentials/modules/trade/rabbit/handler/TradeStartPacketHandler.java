package fr.elias.oreoEssentials.modules.trade.rabbit.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeStartPacket;


public final class TradeStartPacketHandler implements PacketSubscriber<TradeStartPacket> {

    private final OreoEssentials plugin;

    public TradeStartPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, TradeStartPacket packet) {
        if (packet == null) {
            return;
        }

        var broker = plugin.getTradeBroker();
        if (broker == null) {
            plugin.getLogger().warning("[TRADE] <START> received but TradeCrossServerBroker is null.");
            return;
        }

        try {
            var svc = plugin.getTradeService();
            if (svc != null && svc.getConfig() != null && svc.getConfig().debugDeep) {
                plugin.getLogger().info("[TRADE] <START> recv"
                        + " sid=" + packet.getSessionId()
                        + " req=" + packet.getRequesterName() + "/" + packet.getRequesterId()
                        + " acc=" + packet.getAcceptorName() + "/" + packet.getAcceptorId()
                        + " ch=" + String.valueOf(channel));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[TRADE] <START> debug log failed: " + t.getMessage());
        }

        try {

            broker.handleRemoteStart(packet);
        } catch (Throwable t) {
            plugin.getLogger().severe("[TRADE] <START> handler failed: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
