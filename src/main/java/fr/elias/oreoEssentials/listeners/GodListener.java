package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.GodService;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class GodListener implements Listener {
    private final GodService god;

    public GodListener(GodService god) {
        this.god = god;
    }

    // On join: clear any stale invulnerable flag left over from a previous god-mode session.
    // setInvulnerable() is saved to the player's NBT data, so a server restart loses the
    // in-memory GodService state while the flag stays true — making mobs unable to target the
    // player (vanilla: invulnerable entities are excluded from NearestAttackableTargetGoal).
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!god.isGod(p.getUniqueId()) && p.isInvulnerable()) {
            p.setInvulnerable(false);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!god.isGod(p.getUniqueId())) return;

        e.setCancelled(true);

        p.setFireTicks(0);
        if (p.getHealth() < p.getMaxHealth()) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 0.5));
        }
        DamageCause c = e.getCause();
        if (c == DamageCause.VOID) {
            var dest = p.getLocation().add(0, 2, 0);
            if (OreScheduler.isFolia()) {
                p.teleportAsync(dest);
            } else {
                p.teleport(dest);
            }
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!god.isGod(p.getUniqueId())) return;

        e.setCancelled(true);
        e.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
    }

    @EventHandler
    public void onAirChange(EntityAirChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!god.isGod(p.getUniqueId())) return;

        // Prevent drowning
        e.setAmount(p.getMaximumAir());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!god.isGod(p.getUniqueId())) return;

        if (p.getFireTicks() > 0) p.setFireTicks(0);
    }
}
