package fr.elias.oreoEssentials.modules.trade.rabbit.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeStatePacket;
import org.bukkit.Bukkit;

public final class TradeStatePacketHandler implements PacketSubscriber<TradeStatePacket> {
    private final OreoEssentials plugin;

    public TradeStatePacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    private boolean dbg() {
        try {
            return plugin.getTradeService() != null
                    && plugin.getTradeService().getConfig() != null
                    && plugin.getTradeService().getConfig().debugDeep;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onReceive(PacketChannel channel, TradeStatePacket packet) {
        // Always hop to the main thread for any Bukkit/SmartInvs work
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (packet == null) return;

            if (dbg()) {
                plugin.getLogger().info("[TRADE] RECV TradeState"
                        + " ch=" + channel
                        + " sid=" + packet.getSessionId()
                        + " from=" + packet.getFromPlayerId()
                        + " ready=" + packet.isReady()
                        + " bytes=" + (packet.getOfferBytes() == null ? 0 : packet.getOfferBytes().length));
            }

            // 1) Apply to the real session to update GUI/live view
            if (plugin.getTradeService() != null) {
                try {
                    plugin.getTradeService().applyRemoteState(packet);
                } catch (Throwable t) {
                    if (dbg()) plugin.getLogger().warning("[TRADE] applyRemoteState error: " + t.getMessage());
                }
            }

            // 2) Update the broker’s lightweight mirror (optional chat pings / auto-confirm)
            if (plugin.getTradeBroker() != null) {
                try {
                    plugin.getTradeBroker().handleRemoteState(packet);
                } catch (Throwable t) {
                    if (dbg()) plugin.getLogger().warning("[TRADE] broker.handleRemoteState error: " + t.getMessage());
                }
            }
        });
    }
}
