package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.VanishService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        vanish.handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        vanish.handleQuit(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player player)) return;
        if (vanish.isVanished(player)) {
            e.setCancelled(true);
            e.setTarget(null);
        }
    }
}
