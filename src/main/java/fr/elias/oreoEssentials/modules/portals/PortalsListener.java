package fr.elias.oreoEssentials.modules.portals;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PortalsListener implements Listener {

    private final PortalsManager manager;

    public PortalsListener(PortalsManager manager) {
        this.manager = manager;
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || !manager.isEnabled()) return;


        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        manager.tickMove(p, e.getTo());
    }

    /**
     * Clean up cooldowns when player quits to prevent memory leaks.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {

    }
}