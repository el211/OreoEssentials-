package fr.elias.oreoEssentials.modules.maintenance;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Service that manages the maintenance mode state and timer
 */
public class MaintenanceService {
    private final OreoEssentials plugin;
    private final MaintenanceConfig config;
    private BukkitTask timerTask;

    public MaintenanceService(OreoEssentials plugin, MaintenanceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Enable maintenance mode
     */
    public void enable() {
        config.setEnabled(true);
        kickNonWhitelistedPlayers();
        plugin.getLogger().info("[Maintenance] Maintenance mode enabled");
    }

    /**
     * Disable maintenance mode
     */
    public void disable() {
        config.setEnabled(false);
        stopTimer();
        plugin.getLogger().info("[Maintenance] Maintenance mode disabled");
    }

    /**
     * Toggle maintenance mode
     */
    public boolean toggle() {
        if (config.isEnabled()) {
            disable();
            return false;
        } else {
            enable();
            return true;
        }
    }

    /**
     * Check if maintenance mode is active
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Check if a player can join during maintenance
     */
    public boolean canJoin(Player player) {
        if (!config.isEnabled()) {
            return true;
        }

        // Check permission
        if (player.hasPermission("oreo.maintenance.bypass")) {
            return true;
        }

        // Check whitelist by UUID
        if (config.isWhitelisted(player.getUniqueId())) {
            return true;
        }

        // Check whitelist by name
        return config.isWhitelisted(player.getName());
    }

    /**
     * Add a player to the maintenance whitelist
     */
    public void addToWhitelist(UUID uuid) {
        config.addToWhitelist(uuid.toString());
    }

    /**
     * Add a player to the maintenance whitelist by name
     */
    public void addToWhitelist(String name) {
        config.addToWhitelist(name);
    }

    /**
     * Remove a player from the maintenance whitelist
     */
    public void removeFromWhitelist(UUID uuid) {
        config.removeFromWhitelist(uuid.toString());
    }

    /**
     * Remove a player from the maintenance whitelist by name
     */
    public void removeFromWhitelist(String name) {
        config.removeFromWhitelist(name);
    }

    /**
     * Clear the entire whitelist
     */
    public void clearWhitelist() {
        config.clearWhitelist();
    }

    /**
     * Kick all non-whitelisted players
     */
    public void kickNonWhitelistedPlayers() {
        String kickMsg = ChatColor.translateAlternateColorCodes('&', config.getKickMessage());

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canJoin(player)) {
                player.kickPlayer(kickMsg);
            }
        }
    }

    /**
     * Set maintenance timer
     */
    public void setTimer(long durationMillis) {
        long endTime = System.currentTimeMillis() + durationMillis;
        config.setEndTime(endTime);
        config.setUseTimer(true);
        startTimerCheck();
    }

    /**
     * Add time to existing timer
     */
    public void addTime(long durationMillis) {
        long currentEnd = config.getEndTime();
        if (currentEnd <= 0) {
            currentEnd = System.currentTimeMillis();
        }
        config.setEndTime(currentEnd + durationMillis);
        config.setUseTimer(true);
        startTimerCheck();
    }

    /**
     * Remove timer
     */
    public void removeTimer() {
        config.setUseTimer(false);
        config.setEndTime(0);
        stopTimer();
    }

    /**
     * Start the timer check task
     */
    private void startTimerCheck() {
        stopTimer(); // Stop existing task

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (config.isTimerExpired()) {
                disable();
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&a&l✓ MAINTENANCE ENDED\n&7Maintenance mode has been automatically disabled."));
                stopTimer();
            }
        }, 20L, 20L); // Check every second
    }

    /**
     * Stop the timer task
     */
    private void stopTimer() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    /**
     * Get formatted time remaining
     */
    public String getFormattedTimeRemaining() {
        long remaining = config.getRemainingTime();
        if (remaining < 0) {
            return "No timer set";
        }

        return formatDuration(remaining);
    }

    /**
     * Format duration in milliseconds to human-readable string
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Parse duration string to milliseconds
     * Supports: 1d, 2h, 30m, 45s, or combinations like "1d 2h 30m"
     */
    public static long parseDuration(String input) throws IllegalArgumentException {
        long totalMillis = 0;
        String[] parts = input.split("\\s+");

        for (String part : parts) {
            part = part.trim().toLowerCase();
            if (part.isEmpty()) continue;

            char unit = part.charAt(part.length() - 1);
            String numberStr = part.substring(0, part.length() - 1);

            try {
                long number = Long.parseLong(numberStr);

                switch (unit) {
                    case 'd': // days
                        totalMillis += number * 24 * 60 * 60 * 1000;
                        break;
                    case 'h': // hours
                        totalMillis += number * 60 * 60 * 1000;
                        break;
                    case 'm': // minutes
                        totalMillis += number * 60 * 1000;
                        break;
                    case 's': // seconds
                        totalMillis += number * 1000;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid time unit: " + unit + ". Use d, h, m, or s");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number format: " + numberStr);
            }
        }

        if (totalMillis == 0) {
            throw new IllegalArgumentException("No valid duration found");
        }

        return totalMillis;
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        stopTimer();
    }

    /**
     * Get the config
     */
    public MaintenanceConfig getConfig() {
        return config;
    }
}