package fr.elias.oreoEssentials.modules.trade.rabbit.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeConfirmPacket;

public final class TradeConfirmPacketHandler implements PacketSubscriber<TradeConfirmPacket> {
    private final OreoEssentials plugin;
    public TradeConfirmPacketHandler(OreoEssentials plugin) { this.plugin = plugin; }

    private boolean dbg(){
        try { return OreoEssentials.get().getTradeService().getConfig().debugDeep; }
        catch (Throwable t){ return false; }
    }

    @Override
    public void onReceive(PacketChannel channel, TradeConfirmPacket packet) {
        if (dbg()) plugin.getLogger().info("[TRADE] RECV TradeConfirmPacket ch=" + channel +
                " session=" + packet.getSessionId() + " confirmer=" + packet.getConfirmerId());

        if (plugin.getTradeBroker() != null) {
            plugin.getTradeBroker().handleRemoteConfirm(packet);
        } else if (dbg()) {
            plugin.getLogger().warning("[TRADE]   TradeBroker is null; cannot handle confirm.");
        }
    }
}
