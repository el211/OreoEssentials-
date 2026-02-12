package fr.elias.oreoEssentials.modules.maintenance;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Configuration handler for the maintenance system
 */
public class MaintenanceConfig {
    private final OreoEssentials plugin;
    private File configFile;
    private FileConfiguration config;

    // Default values
    private boolean enabled;
    private String motdLine1;
    private String motdLine2;
    private String kickMessage;
    private String joinDeniedMessage;
    private List<String> whitelist;
    private long endTime; // Unix timestamp in milliseconds
    private boolean useTimer;
    private boolean showTimerInMotd;
    private String timerFormat;
    private boolean showServerAsFull;
    private boolean hidePlayerCount;

    public MaintenanceConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        this.whitelist = new ArrayList<>();
        loadConfig();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "maintenance.yml");

        if (!configFile.exists()) {
            plugin.saveResource("maintenance.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("maintenance.enabled", false);
        motdLine1 = config.getString("maintenance.motd.line1", "&c&l⚠ MAINTENANCE ⚠");
        motdLine2 = config.getString("maintenance.motd.line2", "&7Server is currently under maintenance");
        kickMessage = config.getString("maintenance.kick-message",
                "&c&l⚠ MAINTENANCE ⚠\n&7\n&7The server is currently under maintenance.\n&7Please try again later.");
        joinDeniedMessage = config.getString("maintenance.join-denied-message",
                "&c&l⚠ MAINTENANCE ⚠\n&7\n&7The server is currently under maintenance.\n&7Please try again later.");

        whitelist = config.getStringList("maintenance.whitelist");

        endTime = config.getLong("maintenance.timer.end-time", 0);
        useTimer = config.getBoolean("maintenance.timer.enabled", false);
        showTimerInMotd = config.getBoolean("maintenance.timer.show-in-motd", true);
        timerFormat = config.getString("maintenance.timer.format", "&eTime remaining: &f{TIME}");

        showServerAsFull = config.getBoolean("maintenance.server-list.show-as-full", true);
        hidePlayerCount = config.getBoolean("maintenance.server-list.hide-player-count", false);
    }

    public void save() {
        config.set("maintenance.enabled", enabled);
        config.set("maintenance.motd.line1", motdLine1);
        config.set("maintenance.motd.line2", motdLine2);
        config.set("maintenance.kick-message", kickMessage);
        config.set("maintenance.join-denied-message", joinDeniedMessage);
        config.set("maintenance.whitelist", whitelist);
        config.set("maintenance.timer.end-time", endTime);
        config.set("maintenance.timer.enabled", useTimer);
        config.set("maintenance.timer.show-in-motd", showTimerInMotd);
        config.set("maintenance.timer.format", timerFormat);
        config.set("maintenance.server-list.show-as-full", showServerAsFull);
        config.set("maintenance.server-list.hide-player-count", hidePlayerCount);

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Maintenance] Failed to save config: " + e.getMessage());
        }
    }

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public String getMotdLine1() {
        return motdLine1;
    }

    public void setMotdLine1(String motdLine1) {
        this.motdLine1 = motdLine1;
        save();
    }

    public String getMotdLine2() {
        return motdLine2;
    }

    public void setMotdLine2(String motdLine2) {
        this.motdLine2 = motdLine2;
        save();
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void setKickMessage(String kickMessage) {
        this.kickMessage = kickMessage;
        save();
    }

    public String getJoinDeniedMessage() {
        return joinDeniedMessage;
    }

    public void setJoinDeniedMessage(String joinDeniedMessage) {
        this.joinDeniedMessage = joinDeniedMessage;
        save();
    }

    public List<String> getWhitelist() {
        return new ArrayList<>(whitelist);
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelist.contains(uuid.toString());
    }

    public boolean isWhitelisted(String name) {
        return whitelist.contains(name);
    }

    public void addToWhitelist(String identifier) {
        if (!whitelist.contains(identifier)) {
            whitelist.add(identifier);
            save();
        }
    }

    public void removeFromWhitelist(String identifier) {
        whitelist.remove(identifier);
        save();
    }

    public void clearWhitelist() {
        whitelist.clear();
        save();
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
        save();
    }

    public boolean isUseTimer() {
        return useTimer;
    }

    public void setUseTimer(boolean useTimer) {
        this.useTimer = useTimer;
        save();
    }

    public boolean isShowTimerInMotd() {
        return showTimerInMotd;
    }

    public void setShowTimerInMotd(boolean showTimerInMotd) {
        this.showTimerInMotd = showTimerInMotd;
        save();
    }

    public String getTimerFormat() {
        return timerFormat;
    }

    public void setTimerFormat(String timerFormat) {
        this.timerFormat = timerFormat;
        save();
    }

    public long getRemainingTime() {
        if (!useTimer || endTime <= 0) {
            return -1;
        }
        long remaining = endTime - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    public boolean isTimerExpired() {
        return useTimer && endTime > 0 && System.currentTimeMillis() >= endTime;
    }

    public boolean isShowServerAsFull() {
        return showServerAsFull;
    }

    public void setShowServerAsFull(boolean showServerAsFull) {
        this.showServerAsFull = showServerAsFull;
        save();
    }

    public boolean isHidePlayerCount() {
        return hidePlayerCount;
    }

    public void setHidePlayerCount(boolean hidePlayerCount) {
        this.hidePlayerCount = hidePlayerCount;
        save();
    }
}