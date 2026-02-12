// src/main/java/fr/elias/oreoEssentials/commands/core/moderation/freeze/FreezeListener.java
package fr.elias.oreoEssentials.modules.freeze;

import fr.elias.oreoEssentials.services.FreezeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class FreezeListener implements Listener {

    private final FreezeService freezeService;

    public FreezeListener(FreezeService freezeService) {
        this.freezeService = freezeService;
    }

    private boolean isFrozen(Player p) {
        UUID id = p.getUniqueId();
        return freezeService.isFrozen(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!isFrozen(p)) return;

        var from = event.getFrom();
        var to   = event.getTo();
        if (to == null) return;

        // Only allow head rotation, block position change
        if (from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ()) {
            event.setTo(from.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!isFrozen(p)) return;

        event.setCancelled(true);
    }
}
