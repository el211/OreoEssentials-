package fr.elias.oreoEssentials.modules.maintenance;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Listener for maintenance mode events
 */
public class MaintenanceListener implements Listener {
    private final OreoEssentials plugin;
    private final MaintenanceService service;
    private final MaintenanceConfig config;

    public MaintenanceListener(OreoEssentials plugin, MaintenanceService service) {
        this.plugin = plugin;
        this.service = service;
        this.config = service.getConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (!service.canJoin(player)) {
            String message = ChatColor.translateAlternateColorCodes('&', config.getJoinDeniedMessage());

            // Add timer info if enabled
            if (config.isUseTimer() && config.getRemainingTime() > 0) {
                String timeRemaining = service.getFormattedTimeRemaining();
                message += "\n&7\n&eEstimated time remaining: &f" + timeRemaining;
            }

            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                    ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(ServerListPingEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        // Build MOTD
        String line1 = ChatColor.translateAlternateColorCodes('&', config.getMotdLine1());
        String line2 = ChatColor.translateAlternateColorCodes('&', config.getMotdLine2());

        // Add timer to MOTD if enabled
        if (config.isShowTimerInMotd() && config.isUseTimer() && config.getRemainingTime() > 0) {
            String timeRemaining = service.getFormattedTimeRemaining();
            String timerText = config.getTimerFormat().replace("{TIME}", timeRemaining);
            line2 = ChatColor.translateAlternateColorCodes('&', timerText);
        }

        event.setMotd(line1 + "\n" + line2);

        // Show server as full if configured
        if (config.isShowServerAsFull()) {
            event.setMaxPlayers(0);
        }

        // Optionally hide player count (shows "???" on modern clients)
        if (config.isHidePlayerCount()) {
            try {
                // This will show "???" for player count on modern clients
                event.setMaxPlayers(-1);
            } catch (Exception ignored) {
                // Fallback for older versions - show as full
                event.setMaxPlayers(0);
            }
        }
    }
}