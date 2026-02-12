package fr.elias.oreoEssentials.modules.shards.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class CombatTracker implements Listener {

    private final BorderDetectionListener borderListener;

    public CombatTracker(BorderDetectionListener borderListener) {
        this.borderListener = borderListener;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            borderListener.onPlayerDamage(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up damage tracking
        borderListener.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}