package fr.elias.oreoEssentials.modules.deathback;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathBackListener implements Listener {
    private final DeathBackService service;

    public DeathBackListener(DeathBackService service) {
        this.service = service;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        service.setLastDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation());
    }
}
