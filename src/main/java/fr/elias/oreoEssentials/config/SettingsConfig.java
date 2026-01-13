package fr.elias.oreoEssentials.config;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

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

    public boolean mobsHealthbarEnabled() {
        return mobsEnabled() && featureOption("mobs", "healthbar", true);
    }

    public boolean tabEnabled() {
        return isEnabled("tab");
    }

    public boolean tempFlyEnabled() {
        return isEnabled("tempfly");
    }

    public FileConfiguration getRoot() {
        return cfg;
    }
}