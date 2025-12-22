// File: src/main/java/fr/elias/oreoEssentials/rtp/listeners/RtpJoinListener.java
package fr.elias.oreoEssentials.rtp.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.core.playercommands.RtpCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class RtpJoinListener implements Listener {

    private final OreoEssentials plugin;

    public RtpJoinListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // Delay so player is fully spawned/world loaded (avoid failed teleports).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String worldName = plugin.getRtpPendingService().consume(p.getUniqueId());
            if (worldName == null || worldName.isBlank()) return;

            plugin.getLogger().info("[RTP] Executing pending RTP for " + p.getName() + " in world=" + worldName);

            boolean ok = RtpCommand.doLocalRtp(plugin, p, worldName);
            if (!ok) {
                plugin.getLogger().warning("[RTP] Failed to perform pending RTP for " + p.getName() + " in world=" + worldName);
                // why: cooldown is NOT applied on failure; user can try again
                return;
            }

            // ✅ Apply cooldown only after a successful cross-server RTP
            RtpCommand.applyCooldownNow(plugin, p);
        }, 1L);
    }
}
