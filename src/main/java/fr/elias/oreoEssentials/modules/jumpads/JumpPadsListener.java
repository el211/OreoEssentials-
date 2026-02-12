// File: src/main/java/fr/elias/oreoEssentials/jumpads/JumpPadsListener.java
package fr.elias.oreoEssentials.modules.jumpads;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;


public class JumpPadsListener implements Listener {
    private final JumpPadsManager mgr;

    public JumpPadsListener(JumpPadsManager mgr) {
        this.mgr = mgr;
    }

    @EventHandler
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
}