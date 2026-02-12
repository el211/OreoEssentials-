package fr.elias.oreoEssentials.modules.rtp.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.rtp.RtpCommand;
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String worldName = plugin.getRtpPendingService().consume(p.getUniqueId());
            if (worldName == null || worldName.isBlank()) return;

            plugin.getLogger().info("[RTP] Executing pending RTP for " + p.getName() + " in world=" + worldName);

            boolean ok = RtpCommand.doLocalRtp(plugin, p, worldName);
            if (!ok) {
                plugin.getLogger().warning("[RTP] Failed to perform pending RTP for " + p.getName() + " in world=" + worldName);
                return;
            }

            RtpCommand.applyCooldownNow(plugin, p);
        }, 1L);
    }
}
