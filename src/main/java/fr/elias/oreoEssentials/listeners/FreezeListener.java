package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.FreezeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class FreezeListener implements Listener {
    private final FreezeService service;
    public FreezeListener(FreezeService service) { this.service = service; }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!service.isFrozen(p.getUniqueId())) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ() || e.getFrom().getY() != e.getTo().getY()) {
            e.setTo(e.getFrom().clone().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (service.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (service.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (service.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!service.isFrozen(e.getPlayer().getUniqueId())) return;
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/msg") || msg.startsWith("/tell") || msg.startsWith("/r") || msg.startsWith("/reply")) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage("§cYou are frozen and cannot run commands.");
    }
}
