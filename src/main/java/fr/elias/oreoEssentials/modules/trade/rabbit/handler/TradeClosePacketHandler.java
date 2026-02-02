package fr.elias.oreoEssentials.modules.trade.rabbit.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeClosePacket;
import org.bukkit.Bukkit;

public final class TradeClosePacketHandler implements PacketSubscriber<TradeClosePacket> {
    private final OreoEssentials plugin;

    public TradeClosePacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    private boolean dbg() {
        try { return OreoEssentials.get().getTradeService().getConfig().debugDeep; }
        catch (Throwable t) { return false; }
    }

    @Override
    public void onReceive(PacketChannel channel, TradeClosePacket packet) {
        if (dbg()) plugin.getLogger().info("[TRADE] RECV TradeClosePacket ch=" + channel +
                " session=" + packet.getSessionId() + " target=" + packet.getGrantTo());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getTradeService() != null) {
                plugin.getTradeService().handleRemoteClose(packet);
            } else if (dbg()) {
                plugin.getLogger().warning("[TRADE]   TradeService is null; cannot handle close.");
            }
        });
    }
}
