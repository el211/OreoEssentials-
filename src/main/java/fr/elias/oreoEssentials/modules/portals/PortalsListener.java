package fr.elias.oreoEssentials.modules.portals;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PortalsListener implements Listener {

    private final PortalsManager manager;
    private final PortalWandListener wandListener;

    public PortalsListener(PortalsManager manager, PortalWandListener wandListener) {
        this.manager      = manager;
        this.wandListener = wandListener;
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
     * Clean up cooldowns and wand mode when player quits.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        wandListener.disableWandMode(e.getPlayer());
    }
}