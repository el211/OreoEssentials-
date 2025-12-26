// File: src/main/java/fr/elias/oreoEssentials/portals/PortalsListener.java
package fr.elias.oreoEssentials.portals;

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

    /**
     * Optimized move event handler.
     * Only triggers when player actually moves to a new block (not just head movement).
     * Uses MONITOR priority to run after other plugins.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || !manager.isEnabled()) return;

        // Only check when player actually moved to a different block
        // This dramatically reduces the number of checks since MoveEvent fires very frequently
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
        // Cooldown map cleanup is handled automatically by the manager
        // This is just a hook for future cleanup if needed
    }
}