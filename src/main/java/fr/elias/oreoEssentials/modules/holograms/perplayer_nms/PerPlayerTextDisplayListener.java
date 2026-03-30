package fr.elias.oreoEssentials.modules.holograms.perplayer_nms;

import fr.elias.oreoEssentials.modules.holograms.ProtocolLibHoloInterceptor;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

public final class PerPlayerTextDisplayListener implements Listener {

    private final PerPlayerTextDisplayService service;
    private final Plugin plugin;

    /** May be null when ProtocolLib is not installed. */
    private final ProtocolLibHoloInterceptor interceptor;

    public PerPlayerTextDisplayListener(PerPlayerTextDisplayService service,
                                        Plugin plugin,
                                        ProtocolLibHoloInterceptor interceptor) {
        this.service     = service;
        this.plugin      = plugin;
        this.interceptor = interceptor;
    }

    /**
     * On join we fire two independent refresh paths so that the player
     * sees correct placeholder text regardless of which mechanism works:
     *
     * 1. NMS-bridge path (PerPlayerTextDisplayService.forceRefreshForPlayer)
     *    — works on most servers, may fail silently if reflection breaks.
     *
     * 2. ProtocolLib push path (ProtocolLibHoloInterceptor.pushToPlayer)
     *    — does not use NMS reflection; sends a real ProtocolLib packet.
     *    This is the primary fix for the "raw placeholder after restart" bug
     *    where the NMS path was the only fallback and was silently failing.
     *
     * The SPAWN_ENTITY listener in ProtocolLibHoloInterceptor already handles
     * the "player enters view range after join" case automatically.  The explicit
     * push here covers holograms that are within view range at the moment of join
     * and whose SPAWN_ENTITY packet was sent before our listener was ready.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // Use global scheduler (not entity scheduler) — entity may not be region-owned yet at join time,
        // causing runLaterForEntity to silently drop the task (returns null).

        // Path 1 — NMS bridge (kept for compatibility, fires at 20 t)
        OreScheduler.runLater(plugin, () -> {
            if (p.isOnline()) service.forceRefreshForPlayer(p);
        }, 20L);

        // Path 2 — ProtocolLib direct push (fires at 5 t and again at 40 t)
        // Two attempts: the 5-tick push covers entities already loaded at join time;
        // the 40-tick push covers entities in chunks that take longer to arrive.
        if (interceptor != null) {
            final ProtocolLibHoloInterceptor inter = interceptor;
            OreScheduler.runLater(plugin, () -> {
                if (p.isOnline()) service.pushViaInterceptor(p, inter);
            }, 5L);
            OreScheduler.runLater(plugin, () -> {
                if (p.isOnline()) service.pushViaInterceptor(p, inter);
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        cleanup(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        cleanup(e.getPlayer());
    }

    private void cleanup(Player p) {
        service.cleanupPlayer(p);
    }
}