package fr.elias.oreoEssentials.modules.holograms.perplayer_nms;

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

    public PerPlayerTextDisplayListener(PerPlayerTextDisplayService service, Plugin plugin) {
        this.service = service;
        this.plugin  = plugin;
    }

    /** On join: send per-player text overrides after a short delay (20 t) so
     *  players never see raw placeholder text from the entity's saved state. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        OreScheduler.runLaterForEntity(plugin, p, () -> service.forceRefreshForPlayer(p), 20L);
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
        service.cleanupPlayer(p); // prevent stale per-player overrides
    }
}
