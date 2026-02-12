package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public final class PlayerSyncListener implements Listener {
    private final PlayerSyncService service;
    private final boolean enabled;

    public PlayerSyncListener(PlayerSyncService service, boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!enabled) return;
        service.saveIfEnabled(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        Player p = e.getPlayer();

        try {
            OreoEssentials.get().getPlayerDirectory().saveMapping(p.getName(), p.getUniqueId());
        } catch (Exception ex) {
        }

        p.getServer().getScheduler().runTaskLater(
                p.getServer().getPluginManager().getPlugin("OreoEssentials"),
                () -> service.loadAndApply(p),
                10L
        );
    }
}