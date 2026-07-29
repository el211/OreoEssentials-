package fr.elias.oreoEssentials.modules.jumpads;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class JumpPadsListener implements Listener {
    private final JumpPadsManager mgr;

    public JumpPadsListener(JumpPadsManager mgr) {
        this.mgr = mgr;
    }

    // J-4: add ignoreCancelled=true to skip unnecessary processing when move is cancelled
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        // Delegate to manager
        mgr.tryLaunch(e.getPlayer());
    }

    // J-1: clean up cooldown entry on quit to prevent memory leak
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mgr.removeCooldown(e.getPlayer().getUniqueId());
    }
}