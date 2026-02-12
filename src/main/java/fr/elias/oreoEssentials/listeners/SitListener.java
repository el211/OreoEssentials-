package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.commands.core.playercommands.SitCommand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class SitListener implements Listener {

    private void cleanupSeat(Player p) {
        Entity vehicle = p.getVehicle();
        if (vehicle instanceof ArmorStand seat &&
                seat.getScoreboardTags().contains(SitCommand.SEAT_TAG)) {
            p.eject();
            seat.remove();
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        cleanupSeat(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupSeat(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        cleanupSeat(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanupSeat(event.getEntity());
    }
}
