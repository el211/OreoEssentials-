// File: src/main/java/fr/elias/oreoEssentials/jumpads/JumpPadsListener.java
package fr.elias.oreoEssentials.jumpads;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * JumpPads event listener.
 *
 * ✅ VERIFIED PERFECT - No user messages (pure event handling)
 *
 * Listens for player movement and triggers jump pad launches.
 * All logic is delegated to JumpPadsManager.
 */
public class JumpPadsListener implements Listener {
    private final JumpPadsManager mgr;

    public JumpPadsListener(JumpPadsManager mgr) {
        this.mgr = mgr;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        // Only check if player moved to a different block
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        // Delegate to manager
        mgr.tryLaunch(e.getPlayer());
    }
}