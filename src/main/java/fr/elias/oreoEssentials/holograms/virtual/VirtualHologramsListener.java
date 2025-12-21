package fr.elias.oreoEssentials.holograms.virtual;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public final class VirtualHologramsListener implements Listener {

    private final VirtualHologramsManager manager;

    public VirtualHologramsListener(VirtualHologramsManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // tick() will show the ones in range automatically
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        manager.cleanupPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // tick will handle show/hide; cleanup prevents ghosts across worlds
        manager.cleanupPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        manager.cleanupPlayer(e.getPlayer());
    }
}
