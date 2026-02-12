package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class DailyConfig {

    public static final class Mongo {
        public boolean enabled;
        public String uri;
        public String database;
        public String collection;
    }

    public final Mongo mongo = new Mongo();

    public boolean enabled = true;

    public boolean printErrors = true;
    public String prefix = "&8[&3&lDaily&b&lRewards+&8]";
    public String guiTitle = "&3&lDaily&b&lRewards+&8";
    public boolean checkUpdates = true;

    public boolean resetWhenStreakCompleted = true;
    public boolean availableOnNewDay = false;
    public boolean pauseStreakWhenMissed = false;
    public boolean skipMissedDays = false;

    public boolean rewardAutoClaim = false;
    public boolean rewardInstantPopup = false;
    public boolean reminderEnabled = true;
    public int reminderSeconds = 1200;
    public boolean reminderClickable = true;

    public int inventoryRows = 6;
    public boolean showDayQuantity = true;
    public boolean closeOnClaim = false;
    public boolean liveUpdateTimer = true;
    public boolean hideAttributes = true;
    public boolean buttonsDisabled = false;

    private final OreoEssentials plugin;
    private FileConfiguration cfg;
    private File file;

    public DailyConfig(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "dailyrewards.yml");
        if (!file.exists()) plugin.saveResource("dailyrewards.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);

        mongo.enabled    = cfg.getBoolean("Storage.Mongo.Enabled", true);
        mongo.uri        = cfg.getString("Storage.Mongo.Uri", "mongodb://localhost:27017");
        mongo.database   = cfg.getString("Storage.Mongo.Database", "oreo");
        mongo.collection = cfg.getString("Storage.Mongo.Collection", "daily_rewards");

        enabled       = cfg.getBoolean("General.Enabled", true); // NEW
        printErrors   = cfg.getBoolean("General.PrintErrorsToConsole", true);
        prefix        = cfg.getString("General.PluginPrefix", "&8[&3&lDaily&b&lRewards+&8]");
        guiTitle      = cfg.getString("General.PluginGuiTitle", "&3&lDaily&b&lRewards+&8");
        checkUpdates  = cfg.getBoolean("General.CheckForUpdates", true);

        resetWhenStreakCompleted = cfg.getBoolean("Claiming.ResetWhenStreakCompleted", true);
        availableOnNewDay        = cfg.getBoolean("Claiming.AvailableOnNewDay", false);
        pauseStreakWhenMissed    = cfg.getBoolean("Claiming.PauseStreakWhenMissed", false);
        skipMissedDays           = cfg.getBoolean("Claiming.SkipMissedDays", false);

        rewardAutoClaim    = cfg.getBoolean("Join.RewardAutoClaim", false);
        rewardInstantPopup = cfg.getBoolean("Join.RewardInstantPopup", false);
        reminderEnabled    = cfg.getBoolean("Join.DailyRewardReminderEnabled", true);
        reminderSeconds    = cfg.getInt("Join.DailyRewardClaimReminder", 1200);
        reminderClickable  = cfg.getBoolean("Join.DailyRewardReminderClickable", true);

        inventoryRows   = Math.max(1, Math.min(6, cfg.getInt("GUI.InventoryRows", 6)));
        showDayQuantity = cfg.getBoolean("GUI.ShowDayQuantity", true);
        closeOnClaim    = cfg.getBoolean("GUI.CloseOnClaim", false);
        liveUpdateTimer = cfg.getBoolean("GUI.LiveUpdateTimer", true);
        hideAttributes  = cfg.getBoolean("GUI.HideAttributes", true);
        buttonsDisabled = cfg.getBoolean("GUI.ButtonsDisabled", false);
    }

    public void save() {
        if (cfg == null || file == null) return;
        cfg.set("General.Enabled", enabled);
        cfg.set("General.PrintErrorsToConsole", printErrors);
        cfg.set("General.PluginPrefix", prefix);
        cfg.set("General.PluginGuiTitle", guiTitle);
        cfg.set("General.CheckForUpdates", checkUpdates);
        try { cfg.save(file); } catch (Exception ignored) {}
    }

    public FileConfiguration raw() { return cfg; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
}
