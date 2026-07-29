package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.VanishService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VanishListener implements Listener {

    private final VanishService vanish;

    public VanishListener(VanishService vanish, org.bukkit.plugin.Plugin plugin) {
        this.vanish = vanish;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // V-1: Suppress join message for vanished players
        if (vanish.isVanished(player)) {
            e.setJoinMessage(null);
        }
        vanish.handleJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        // V-1: Suppress quit message for vanished players
        if (vanish.isVanished(player)) {
            e.setQuitMessage(null);
        }
        vanish.handleQuit(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player player)) return;
        if (vanish.isVanished(player)) {
            e.setCancelled(true);
            e.setTarget(null);
        }
    }

    // V-3: Prevent vanished players from being hit by other players
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player damaged)) return;
        if (!vanish.isVanished(damaged)) return;
        Entity damager = e.getDamager();
        if (damager instanceof Player attacker && !attacker.hasPermission("oreo.vanish.see")) {
            e.setCancelled(true);
        }
    }
}
