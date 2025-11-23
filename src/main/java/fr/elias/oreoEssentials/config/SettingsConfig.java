// File: src/main/java/fr/elias/oreoEssentials/config/SettingsConfig.java
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

    /** Direct access if needed. */
    public FileConfiguration raw() {
        return cfg;
    }

    // ----------------------------------------------------------------
    // Generic system
    // ----------------------------------------------------------------

    /** Checks whether a feature is enabled in settings.yml. */
    public boolean isEnabled(String featureKey) {
        return cfg.getBoolean("features." + featureKey + ".enabled", true);
    }

    /** Reads a nested boolean option inside a feature (example: chat.discord-bridge) */
    public boolean featureOption(String featureKey, String subKey, boolean def) {
        return cfg.getBoolean("features." + featureKey + "." + subKey, def);
    }

    // ----------------------------------------------------------------
    // Typed helpers (explicit API for main systems)
    // ----------------------------------------------------------------

    // Kits
    public boolean kitsEnabled() { return isEnabled("kits"); }

    public boolean kitsCommandsEnabled() {
        return featureOption("kits", "register-commands", true);
    }

    // Trade
    public boolean tradeEnabled() { return isEnabled("trade"); }

    public boolean tradeCrossServerEnabled() {
        return featureOption("trade", "cross-server", true);
    }

    // Chat + Discord bridge
    /** Master toggle for Oreo chat handling based on features.chat.enabled. */
    public boolean chatEnabled() {
        return isEnabled("chat");
    }

    public boolean chatDiscordBridgeEnabled() {
        return featureOption("chat", "discord-bridge", false);
    }

    // 🔴 Banned words (chat)
    public boolean bannedWordsEnabled() {
        // Prefer root "chat.banned-words" if present, else "features.chat.banned-words"
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

    // Player Vaults
    public boolean playerVaultsEnabled() { return isEnabled("playervaults"); }

    // Economy
    public boolean economyEnabled() { return isEnabled("economy"); }

    // Portals
    public boolean portalsEnabled() { return isEnabled("portals"); }

    // JumpPads
    public boolean jumpPadsEnabled() { return isEnabled("jumppads"); }

    // RTP
    public boolean rtpEnabled() { return isEnabled("rtp"); }

    // Bossbar
    public boolean bossbarEnabled() { return isEnabled("bossbar"); }

    // Scoreboard
    public boolean scoreboardEnabled() { return isEnabled("scoreboard"); }

    // Sit
    public boolean sitEnabled() { return isEnabled("sit"); }

    public boolean getBoolean(String path, boolean def) {
        return cfg.getBoolean(path, def);
    }

    // Playtime Rewards
    public boolean playtimeRewardsEnabled() { return isEnabled("playtime-rewards"); }

    // Discord Moderation
    public boolean discordModerationEnabled() { return isEnabled("discord-moderation"); }

    // OreoHolograms
    public boolean oreoHologramsEnabled() { return isEnabled("oreoholograms"); }

    // ClearLag
    public boolean clearLagEnabled() { return isEnabled("clearlag"); }

    // Mobs healthbar
    public boolean mobsEnabled() { return isEnabled("mobs"); }

    public boolean mobsHealthbarEnabled() {
        return mobsEnabled() && featureOption("mobs", "healthbar", true);
    }

    // TAB
    public boolean tabEnabled() {
        return isEnabled("tab");
    }

    // Alias used by CrossServerSettings
    public FileConfiguration getRoot() {
        return cfg;
    }
}
