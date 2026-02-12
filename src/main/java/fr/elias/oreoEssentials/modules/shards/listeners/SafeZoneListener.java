package fr.elias.oreoEssentials.modules.shards.listeners;

import fr.elias.oreoEssentials.modules.shards.ShardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SafeZoneListener implements Listener {

    private final ShardManager shardManager;

    public SafeZoneListener(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    @EventHandler
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();

            if (shardManager.isInSafeZone(
                    victim.getWorld().getName(),
                    victim.getLocation().getX(),
                    victim.getLocation().getZ()
            )) {
                event.setCancelled(true);
                ((Player) event.getDamager()).sendMessage("§cPvP is disabled near shard borders!");
            }
        }
    }
}