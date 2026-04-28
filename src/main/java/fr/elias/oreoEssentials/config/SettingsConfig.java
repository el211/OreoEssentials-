package fr.elias.oreoEssentials.config;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsConfig {

    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;

    public SettingsConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "settings.yml");
        if (!file.exists()) {
            plugin.saveResource("settings.yml", false);
        }

        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration raw() {
        return cfg;
    }

    public boolean isEnabled(String featureKey) {
        return cfg.getBoolean("features." + featureKey + ".enabled", true);
    }

    public boolean featureOption(String featureKey, String subKey, boolean def) {
        return cfg.getBoolean("features." + featureKey + "." + subKey, def);
    }

    public boolean currencyEnabled() {
        return isEnabled("currency");
    }

    public boolean kitsEnabled() { return isEnabled("kits"); }

    public boolean kitsCommandsEnabled() {
        return featureOption("kits", "register-commands", true);
    }

    public boolean tradeEnabled() { return isEnabled("trade"); }

    public boolean tradeCrossServerEnabled() {
        return featureOption("trade", "cross-server", true);
    }

    public boolean chatEnabled() {
        return isEnabled("chat");
    }

    public boolean chatDiscordBridgeEnabled() {
        return featureOption("chat", "discord-bridge", false);
    }

    public boolean bannedWordsEnabled() {
        String baseKey;
        if (cfg.isConfigurationSection("chat.banned-words")) {
            baseKey = "chat.banned-words";
        } else {
            baseKey = "features.chat.banned-words";
        }
        return cfg.getBoolean(baseKey + ".enabled", false);
    }

    public List<String> bannedWords() {
        String baseKey;
        if (cfg.isConfigurationSection("chat.banned-words")) {
            baseKey = "chat.banned-words";
        } else {
            baseKey = "features.chat.banned-words";
        }

        List<String> rawList = cfg.getStringList(baseKey + ".list");
        if (rawList == null) {
            return List.of();
        }
        return rawList.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
    }

    public boolean playerVaultsEnabled() { return isEnabled("playervaults"); }

    public boolean economyEnabled() { return isEnabled("economy"); }

    public boolean portalsEnabled() { return isEnabled("portals"); }

    public boolean jumpPadsEnabled() { return isEnabled("jumppads"); }

    public boolean rtpEnabled() { return isEnabled("rtp"); }

    public boolean rtpWarmupEnabled() {
        if (cfg.isSet("features.rtp.warmup")) {
            return cfg.getBoolean("features.rtp.warmup", false);
        }
        return cfg.getBoolean("rtp.warmup", false);
    }

    public int rtpWarmupSeconds() {
        int v;
        if (cfg.isSet("features.rtp.warmup-amount")) {
            v = cfg.getInt("features.rtp.warmup-amount", 0);
        } else {
            v = cfg.getInt("rtp.warmup-amount", 0);
        }
        return Math.max(0, v);
    }

    public boolean bossbarEnabled() { return isEnabled("bossbar"); }

    public boolean scoreboardEnabled() { return isEnabled("scoreboard"); }

    public boolean sitEnabled() { return isEnabled("sit"); }

    public boolean getBoolean(String path, boolean def) {
        return cfg.getBoolean(path, def);
    }

    public boolean playtimeRewardsEnabled() { return isEnabled("playtime-rewards"); }

    public boolean discordModerationEnabled() { return isEnabled("discord-moderation"); }

    public boolean oreoHologramsEnabled() { return isEnabled("oreoholograms"); }

    public boolean clearLagEnabled() { return isEnabled("clearlag"); }

    public boolean mobsEnabled() { return isEnabled("mobs"); }

    public boolean worldShardingEnabled() {
        return getRoot().getBoolean("features.world-sharding.enabled", false);
    }
    public boolean maintenanceEnabled() {
        return raw().getBoolean("features.maintenance.enabled", true);
    }
    public boolean mobsHealthbarEnabled() {
        return mobsEnabled() && featureOption("mobs", "healthbar", true);
    }

    public boolean tabEnabled() {
        return isEnabled("tab");
    }

    public boolean uiSafeguardsEnabled() {
        return cfg.getBoolean("performance.ui-safeguards.enabled", false);
    }

    public boolean uiModuleDisabledForServer(String serverName, String moduleKey) {
        if (!uiSafeguardsEnabled() || serverName == null || serverName.isBlank() || moduleKey == null || moduleKey.isBlank()) {
            return false;
        }
        Set<String> guardedServers = new HashSet<>();
        for (String entry : cfg.getStringList("performance.ui-safeguards.server-names")) {
            if (entry != null && !entry.isBlank()) {
                guardedServers.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        if (!guardedServers.contains(serverName.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return cfg.getBoolean("performance.ui-safeguards.disable." + moduleKey, false);
    }

    public boolean joinFloodProtectionEnabled() {
        return cfg.getBoolean("performance.join-flood-protection.enabled", true);
    }

    public int joinFloodWindowMillis() {
        return Math.max(1000, cfg.getInt("performance.join-flood-protection.window-ms", 10000));
    }

    public int joinFloodUiSoftJoinsPerWindow() {
        return Math.max(1, cfg.getInt("performance.join-flood-protection.ui-soft-joins-per-window", 8));
    }

    public long joinFloodBaseUiDelayTicks() {
        return Math.max(0L, cfg.getLong("performance.join-flood-protection.base-ui-delay-ticks", 10L));
    }

    public long joinFloodExtraUiDelayTicks() {
        return Math.max(1L, cfg.getLong("performance.join-flood-protection.extra-ui-delay-ticks", 10L));
    }

    public long joinFloodMaxUiDelayTicks() {
        long configured = cfg.getLong("performance.join-flood-protection.max-ui-delay-ticks", 100L);
        return Math.max(joinFloodBaseUiDelayTicks(), configured);
    }

    public boolean joinFloodLoginRateLimitEnabled() {
        return cfg.getBoolean("performance.join-flood-protection.login-rate-limit.enabled", true);
    }

    public int joinFloodMaxLoginsPerWindow() {
        return Math.max(1, cfg.getInt("performance.join-flood-protection.login-rate-limit.max-logins-per-window", 20));
    }

    public String joinFloodKickMessage() {
        return cfg.getString(
                "performance.join-flood-protection.login-rate-limit.kick-message",
                "<red>Join queue is active. Please reconnect in a moment.</red>"
        );
    }

    public boolean containerSpamGuardEnabled() {
        return cfg.getBoolean("performance.container-spam-guard.enabled", true);
    }

    public int containerSpamGuardWindowMillis() {
        return Math.max(250, cfg.getInt("performance.container-spam-guard.window-ms", 1250));
    }

    public int containerSpamGuardMaxScore() {
        return Math.max(5, cfg.getInt("performance.container-spam-guard.max-score-per-window", 40));
    }

    public int containerSpamGuardShiftWeight() {
        return Math.max(1, cfg.getInt("performance.container-spam-guard.shift-click-weight", 6));
    }

    public int containerSpamGuardTransferWeight() {
        return Math.max(1, cfg.getInt("performance.container-spam-guard.transfer-weight", 4));
    }

    public int containerSpamGuardDragSlotWeight() {
        return Math.max(1, cfg.getInt("performance.container-spam-guard.drag-slot-weight", 2));
    }

    public boolean containerSpamGuardCloseInventory() {
        return cfg.getBoolean("performance.container-spam-guard.close-inventory-on-trip", true);
    }

    public int containerSpamGuardKickAfterStrikes() {
        return Math.max(0, cfg.getInt("performance.container-spam-guard.kick-after-strikes", 3));
    }

    public int containerSpamGuardWarnCooldownMillis() {
        return Math.max(0, cfg.getInt("performance.container-spam-guard.warn-cooldown-ms", 2000));
    }

    public String containerSpamGuardWarnMessage() {
        return cfg.getString(
                "performance.container-spam-guard.warn-message",
                "<red>Container actions are being rate-limited. Slow down.</red>"
        );
    }

    public String containerSpamGuardKickMessage() {
        return cfg.getString(
                "performance.container-spam-guard.kick-message",
                "<red>You are sending too many container actions.</red>"
        );
    }

    public boolean tempFlyEnabled() {
        return isEnabled("tempfly");
    }


    public boolean autoRebootEnabled() {
        return cfg.getBoolean("auto-reboot.enabled", false);
    }

    public String autoRebootMode() {
        return cfg.getString("auto-reboot.mode", "TIME");
    }

    public String autoRebootTimezone() {
        return cfg.getString("auto-reboot.timezone", "Europe/Paris");
    }

    public String autoRebootTime() {
        return cfg.getString("auto-reboot.time", "00:00");
    }

    public int autoRebootIntervalMinutes() {
        return Math.max(1, cfg.getInt("auto-reboot.interval-minutes", 360));
    }

    public boolean autoRebootBroadcast() {
        return cfg.getBoolean("auto-reboot.broadcast", true);
    }

    public String autoRebootKickMessage() {
        return cfg.getString("auto-reboot.kick-message",
                "<red>Server is restarting. Please reconnect in a moment.</red>");
    }

    public List<String> autoRebootPreCommands() {
        List<String> list = cfg.getStringList("auto-reboot.pre-commands");
        return list == null ? List.of() : list;
    }


    public String autoRebootAction() {
        return cfg.getString("auto-reboot.action", "KICK");
    }

    public boolean autoRebootSafeZoneEnabled() {
        return cfg.getBoolean("auto-reboot.safe-zone.enabled", false);
    }

    public String autoRebootSafeZoneRegionName() {
        return cfg.getString("auto-reboot.safe-zone.region-name", "reboot_safe");
    }

    public String autoRebootSafeZoneWorldName() {
        return cfg.getString("auto-reboot.safe-zone.world-name", "world");
    }

    public String autoRebootSafeZoneServer() {
        return cfg.getString("auto-reboot.safe-zone.server", "");
    }

    public String autoRebootSafeZoneMessage() {
        return cfg.getString("auto-reboot.safe-zone.message",
                "<yellow>Server is rebooting. You have been moved to a safe area.</yellow>");
    }

    public List<Integer> autoRebootWarningsSeconds() {
        List<?> raw = cfg.getList("auto-reboot.warnings");
        if (raw == null || raw.isEmpty()) {
            return List.of(300, 60, 30, 10, 5, 4, 3, 2, 1);
        }

        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Number n) out.add(Math.max(0, n.intValue()));
            else {
                try {
                    out.add(Math.max(0, Integer.parseInt(String.valueOf(o))));
                } catch (Throwable ignored) {}
            }
        }
        out.sort(java.util.Comparator.reverseOrder());
        return out.isEmpty() ? List.of(300, 60, 30, 10, 5, 4, 3, 2, 1) : List.copyOf(out);
    }
    public String autoRebootSafeZoneWorld() {
        return autoRebootSafeZoneWorldName();
    }

    public String autoRebootSafeZoneRegion() {
        return autoRebootSafeZoneRegionName();
    }

    public int autoRebootSafeZoneDelaySeconds() {
        return Math.max(0, cfg.getInt("auto-reboot.safe-zone.delay-seconds", 3));
    }

    public FileConfiguration getRoot() {
        return cfg;
    }
}
