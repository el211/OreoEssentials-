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

        // Delay 1 tick so the player is fully spawned and world is ready
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Check if this player has a pending cross-server RTP
            String worldName = plugin.getRtpPendingService().consume(p.getUniqueId());
            if (worldName == null) {
                return; // no pending RTP for this player
            }

            plugin.getLogger().info("[RTP] Executing pending RTP for "
                    + p.getName() + " in world=" + worldName);

            // Use the shared local RTP helper from RtpCommand
            boolean ok = RtpCommand.doLocalRtp(plugin, p, worldName);
            if (!ok) {
                plugin.getLogger().warning("[RTP] Failed to perform pending RTP for "
                        + p.getName() + " in world=" + worldName);
            }
        }, 1L);
    }
}
