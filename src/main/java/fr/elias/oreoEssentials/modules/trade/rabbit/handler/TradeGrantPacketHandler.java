package fr.elias.oreoEssentials.modules.trade.rabbit.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeGrantPacket;

public final class TradeGrantPacketHandler implements PacketSubscriber<TradeGrantPacket> {
    private final OreoEssentials plugin;
    public TradeGrantPacketHandler(OreoEssentials plugin) { this.plugin = plugin; }

    private boolean dbg(){
        try { return OreoEssentials.get().getTradeService().getConfig().debugDeep; }
        catch (Throwable t){ return false; }
    }

    @Override
    public void onReceive(PacketChannel channel, TradeGrantPacket packet) {
        if (dbg()) plugin.getLogger().info("[TRADE] RECV TradeGrantPacket ch=" + channel +
                " session=" + packet.getSessionId() + " grantTo=" + packet.getGrantTo() +
                " bytes=" + (packet.getItemsBytes()==null?0:packet.getItemsBytes().length));

        if (plugin.getTradeBroker() != null) {
            plugin.getTradeBroker().handleRemoteGrant(packet);
        } else if (dbg()) {
            plugin.getLogger().warning("[TRADE]   TradeBroker is null; cannot handle grant.");
        }
    }
}
