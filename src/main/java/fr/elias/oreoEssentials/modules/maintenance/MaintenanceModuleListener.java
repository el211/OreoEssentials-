package fr.elias.oreoEssentials.modules.maintenance;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.ArrayList;
import java.util.List;


public class MaintenanceModuleListener implements Listener {

    private final OreoEssentials plugin;
    private final MaintenanceService maintenanceService;

    public MaintenanceModuleListener(OreoEssentials plugin, MaintenanceService maintenanceService) {
        this.plugin = plugin;
        this.maintenanceService = maintenanceService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!maintenanceService.isEnabled()) return;

        if (maintenanceService.canJoin(event.getPlayer())) return;

        String msg = color(maintenanceService.getConfig().getJoinDeniedMessage());
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, msg);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(ServerListPingEvent event) {
        if (!maintenanceService.isEnabled()) return;

        MaintenanceConfig cfg = maintenanceService.getConfig();

        String line1 = cfg.getMotdLine1();
        String line2 = cfg.getMotdLine2();

        if (cfg.isUseTimer() && cfg.isShowTimerInMotd()) {
            long remaining = cfg.getRemainingTime();
            if (remaining >= 0) {
                String time = MaintenanceService.formatDuration(remaining);

                String format = cfg.getTimerFormat();
                if (format == null || format.isBlank()) {
                    format = "&eTime remaining: &f{TIME}";
                }

                String timerLine = format.replace("{TIME}", time);

                line2 = (line2 == null ? "" : line2) + "\n" + timerLine;
            }
        }

        event.setMotd(color((line1 == null ? "" : line1) + "\n" + (line2 == null ? "" : line2)));

        if (cfg.isShowServerAsFull()) {
            event.setMaxPlayers(event.getNumPlayers());
        }

        if (cfg.isHidePlayerCount()) {

            event.setMaxPlayers(0);
        }
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
