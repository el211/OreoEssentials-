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
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishListener implements Listener {

    private final VanishService vanish;
    private final Plugin plugin;

    /** Throttle: last time (ms) we logged a mob-block warning per player UUID. */
    private final Map<UUID, Long> lastMobBlockLog = new ConcurrentHashMap<>();
    private static final long MOB_BLOCK_LOG_THROTTLE_MS = 10_000L; // once per 10 s per player

    public VanishListener(VanishService vanish, Plugin plugin) {
        this.vanish = vanish;
        this.plugin = plugin;
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
        lastMobBlockLog.remove(player.getUniqueId());
        vanish.handleQuit(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player player)) return;
        if (!vanish.isVanished(player)) return;

        e.setCancelled(true);
        e.setTarget(null);

        // Always log — but throttle to once per 10 s per player to avoid console spam.
        long now = System.currentTimeMillis();
        Long last = lastMobBlockLog.get(player.getUniqueId());
        if (last == null || now - last >= MOB_BLOCK_LOG_THROTTLE_MS) {
            lastMobBlockLog.put(player.getUniqueId(), now);
            plugin.getLogger().warning(
                "[VANISH] OreoEssentials blocked a " + e.getEntity().getType()
                + " from targeting " + player.getName()
                + " — they are in the vanished set (possibly a stuck/persisted vanish state)."
                + " Run: /vanish off " + player.getName() + "  to fix.");
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
