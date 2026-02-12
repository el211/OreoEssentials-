package fr.elias.oreoEssentials.modules.trade.rabbit.handler;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.modules.trade.rabbit.packet.TradeInvitePacket;
import fr.elias.oreoEssentials.modules.trade.service.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TradeInvitePacketHandler implements PacketSubscriber<TradeInvitePacket> {
    private final OreoEssentials plugin;

    public TradeInvitePacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    private boolean dbg() {
        try { return OreoEssentials.get().getTradeService().getConfig().debugDeep; }
        catch (Throwable t) { return false; }
    }

    @Override
    public void onReceive(PacketChannel channel, TradeInvitePacket p) {
        // Always bounce to main thread for any Bukkit API usage
        Bukkit.getScheduler().runTask(plugin, () -> handleOnMain(channel, p));
    }

    /* ------------------------------------------------------------------ */
    /* Main-thread logic                                                   */
    /* ------------------------------------------------------------------ */
    private void handleOnMain(PacketChannel channel, TradeInvitePacket p) {
        final TradeService svc = plugin.getTradeService();
        if (svc == null) {
            if (dbg()) plugin.getLogger().warning("[TRADE] RECV TradeInvitePacket but TradeService is null; dropping.");
            return;
        }

        if (dbg()) {
            plugin.getLogger().info("[TRADE] RECV TradeInvitePacket ch=" + channel
                    + " targetId=" + p.getTargetId()
                    + " from=" + p.getRequesterName()
                    + "/" + p.getRequesterId());
        }

        final UUID targetId = p.getTargetId();
        final UUID fromId   = p.getRequesterId();
        final String fromName = p.getRequesterName();

        // 1) If target is ONLINE right now, deliver immediately
        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            if (plugin.getTradeBroker() != null) {
                plugin.getTradeBroker().handleRemoteInvite(p, target);
                if (dbg()) plugin.getLogger().info("[TRADE] delivered invite to online target " + target.getName());
            } else {
                // No broker: store directly into the service so /trade <name> will accept
                svc.addIncomingInvite(target, fromId, fromName);
                if (dbg()) plugin.getLogger().warning("[TRADE] broker null; stored invite via TradeService for " + target.getName());
            }
            return;
        }

        // 2) Target not currently resolvable — try to store the invite by UUID
        //    Prefer the UUID-based API if present (recommended).
        if (tryStoreInviteByUuid(svc, targetId, fromId, fromName)) {
            if (dbg()) plugin.getLogger().info("[TRADE] stored invite for offline/transitioning targetId=" + targetId);
            return;
        }

        // 3) Fallback: retry a few times to catch login race, then store via Player overload
        retryDeliverWhenOnline(p, svc, /*attempts=*/10, /*ticksBetween=*/10L);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Attempts to call TradeService#addIncomingInvite(UUID, UUID, String) if it exists.
     * Returns true if the call was made successfully.
     */
    private boolean tryStoreInviteByUuid(TradeService svc, UUID receiverId, UUID fromId, String fromName) {
        try {
            Method m = TradeService.class.getMethod("addIncomingInvite", UUID.class, UUID.class, String.class);
            m.invoke(svc, receiverId, fromId, fromName);
            return true;
        } catch (NoSuchMethodException e) {
            return false; // older TradeService without the UUID overload
        } catch (Throwable t) {
            if (dbg()) plugin.getLogger().warning("[TRADE] addIncomingInvite(UUID,UUID,String) reflect error: " + t.getMessage());
            return false;
        }
    }

    /**
     * Retry a few times to resolve the Player (handles login/channel timing),
     * then deliver or store using the Player-based API.
     */
    private void retryDeliverWhenOnline(TradeInvitePacket p, TradeService svc, int attempts, long ticksBetween) {
        if (attempts <= 0) {
            if (dbg()) plugin.getLogger().warning("[TRADE] invite retry attempts exhausted for targetId=" + p.getTargetId());
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player target = Bukkit.getPlayer(p.getTargetId());
            if (target != null && target.isOnline()) {
                if (plugin.getTradeBroker() != null) {
                    plugin.getTradeBroker().handleRemoteInvite(p, target);
                    if (dbg()) plugin.getLogger().info("[TRADE] delivered invite after retry to " + target.getName());
                } else {
                    svc.addIncomingInvite(target, p.getRequesterId(), p.getRequesterName());
                    if (dbg()) plugin.getLogger().warning("[TRADE] broker null; stored invite via TradeService after retry for " + target.getName());
                }
            } else {
                // Try again
                retryDeliverWhenOnline(p, svc, attempts - 1, ticksBetween);
            }
        }, ticksBetween);
    }
}
