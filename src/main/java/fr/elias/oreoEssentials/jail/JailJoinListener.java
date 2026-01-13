package fr.elias.oreoEssentials.jail;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public final class JailJoinListener implements Listener {

    private final JailService service;

    public JailJoinListener(JailService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        // Check if player is jailed
        if (service.isJailed(e.getPlayer().getUniqueId())) {
            // Teleport them back to jail cell
            service.teleportToCell(e.getPlayer().getUniqueId());
        }
    }
}