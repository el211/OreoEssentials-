// package: fr.elias.oreoEssentials.modgui.freeze
package fr.elias.oreoEssentials.modgui.freeze;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

public class FreezeListener implements Listener {

    private final FreezeManager manager;

    public FreezeListener(FreezeManager manager) {
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this,
                Bukkit.getPluginManager().getPlugins()[0]);
    }

    private boolean check(Player p) {
        UUID id = p.getUniqueId();
        return manager.isFrozen(id);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!check(e.getPlayer())) return;

        // getTo() can be null → avoid NPE
        if (e.getTo() == null) return;

        if (!e.getFrom().toVector().equals(e.getTo().toVector())) {
            e.setTo(e.getFrom()); // hard freeze
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (check(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        if (check(e.getPlayer())) e.setCancelled(true);
    }

    // Use EntityPickupItemEvent instead of PlayerAttemptPickupItemEvent
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (check(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && check(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && check(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && check(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {

    }
}
