package fr.elias.oreoEssentials.modules.holograms.perplayer_nms;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PerPlayerTextDisplayListener implements Listener {

    private final PerPlayerTextDisplayService service;

    public PerPlayerTextDisplayListener(PerPlayerTextDisplayService service) {
        this.service = service;
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
        service.cleanupPlayer(p); // TOprevent stale per-player overrides
    }
}
