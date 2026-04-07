package fr.elias.oreoEssentials.migration;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Migrates standalone feature config files into dedicated sub-folders.
 *
 *  playerwarps/config.yml:
 *   - If already exists  → skip
 *   - Else if old playerwarps.yml exists at plugin root → move it
 *   - Else               → extract default from jar
 *
 *  dailyrewards/dailyrewards.yml:
 *   - If already exists  → skip
 *   - Else if old dailyrewards.yml exists at plugin root → move it
 *   - Else               → extract default from jar
 *
 *  playervaults/config.yml:
 *   - If already exists  → skip
 *   - Else if playervaults: section exists in config.yml → copy it there, remove from config.yml
 *   - Else               → extract default from jar
 */
public final class FeatureConfigMigrator {

    private FeatureConfigMigrator() {}

    public static void migrate(OreoEssentials plugin) {
        migratePlayerWarps(plugin);
        migrateDailyRewards(plugin);
        migratePlayerVaults(plugin);
        migratePlaytimeRewards(plugin);
        migrateCommandsModule(plugin);
        migrateServerModule(plugin);
        migrateShards(plugin);
        migrateTempFly(plugin);
        migrateJumpAds(plugin);
        migrateAfk(plugin);
        migrateEvents(plugin);
        migrateCurrencyConfig(plugin);
        migrateEnderChest(plugin);
        migrateTrade(plugin);
    }

    // ── Player Warps ──────────────────────────────────────────────────────────

    private static void migratePlayerWarps(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "playerwarps");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "config.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "playerwarps.yml");
        if (old.exists() && old.length() > 2) {
            // Old file had real content — move it
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] playerwarps.yml moved to playerwarps/config.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate playerwarps.yml: " + e.getMessage());
            }
        } else {
            // Old file was empty or missing — extract default
            if (old.exists()) old.delete();
            plugin.saveResource("playerwarps/config.yml", false);
            plugin.getLogger().info("[Migration] Created default playerwarps/config.yml");
        }
    }

    // ── Daily Rewards ─────────────────────────────────────────────────────────

    private static void migrateDailyRewards(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "dailyrewards");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "dailyrewards.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "dailyrewards.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] dailyrewards.yml moved to dailyrewards/dailyrewards.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate dailyrewards.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("dailyrewards/dailyrewards.yml", false);
            plugin.getLogger().info("[Migration] Created default dailyrewards/dailyrewards.yml");
        }
    }

    // ── Player Vaults ─────────────────────────────────────────────────────────

    private static void migratePlayerVaults(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "playervaults");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "config.yml");
        if (dest.exists()) return;

        FileConfiguration mainConfig = plugin.getConfig();
        ConfigurationSection pvSection = mainConfig.getConfigurationSection("playervaults");

        if (pvSection != null) {
            YamlConfiguration out = new YamlConfiguration();
            copySection(pvSection, out, "playervaults");
            try {
                out.save(dest);
                mainConfig.set("playervaults", null);
                plugin.saveConfig();
                plugin.getLogger().info("[Migration] playervaults config moved to playervaults/config.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not save playervaults/config.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("playervaults/config.yml", false);
            plugin.getLogger().info("[Migration] Created default playervaults/config.yml");
        }
    }

    // ── Playtime Rewards ──────────────────────────────────────────────────────

    private static void migratePlaytimeRewards(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "playtime-rewards");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "playtime_rewards.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "playtime_rewards.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] playtime_rewards.yml moved to playtime-rewards/playtime_rewards.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate playtime_rewards.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("playtime-rewards/playtime_rewards.yml", false);
            plugin.getLogger().info("[Migration] Created default playtime-rewards/playtime_rewards.yml");
        }
    }

    // ── Server Module ─────────────────────────────────────────────────────────

    private static void migrateServerModule(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "server");
        if (!folder.exists()) folder.mkdirs();

        moveFile(plugin, folder, "clearlag.yml",       "server/clearlag.yml");
        moveFile(plugin, folder, "craft-actions.yml",  "server/craft-actions.yml");
        moveFile(plugin, folder, "modgui.yml",         "server/modgui.yml");
        moveFile(plugin, folder, "maintenance.yml",    "server/maintenance.yml");
    }

    // ── TempFly ───────────────────────────────────────────────────────────────

    /**
     * Moves tempfly.yml from the plugin root into server/.
     *
     *  server/tempfly.yml:
     *   - If already exists  → skip (already migrated)
     *   - Else if old tempfly.yml exists at plugin root → move it, preserving all config
     *   - Else               → TempFlyConfig.createDefault() will generate it on first load
     */
    private static void migrateTempFly(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "server");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "tempfly.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "tempfly.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] tempfly.yml moved to server/tempfly.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate tempfly.yml: " + e.getMessage());
            }
        } else if (plugin.getResource("server/tempfly.yml") != null) {
            plugin.saveResource("server/tempfly.yml", false);
            plugin.getLogger().info("[Migration] Created default server/tempfly.yml");
        }
    }

    // ── JumpAds ───────────────────────────────────────────────────────────────

    /**
     * Moves jumpads.yml from the plugin root into server/.
     *
     *  server/jumpads.yml:
     *   - If already exists  → skip (already migrated)
     *   - Else if old jumpads.yml exists at plugin root → move it, preserving all jump pad data
     *   - Else               → JumpPadsManager will create an empty file on first load
     */
    private static void migrateJumpAds(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "server");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "jumpads.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "jumpads.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] jumpads.yml moved to server/jumpads.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate jumpads.yml: " + e.getMessage());
            }
        }
        // If neither exists, JumpPadsManager creates an empty file on first load.
    }

    // ── Shards ────────────────────────────────────────────────────────────────

    /**
     * Moves shards.yml from the plugin root into server/.
     *
     *  server/shards.yml:
     *   - If already exists  → skip (already migrated)
     *   - Else if old shards.yml exists at plugin root → move it, preserving all config
     *   - Else               → extract default from jar
     */
    private static void migrateShards(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "server");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "shards.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "shards.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] shards.yml moved to server/shards.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate shards.yml: " + e.getMessage());
            }
        } else {
            if (plugin.getResource("server/shards.yml") != null) {
                plugin.saveResource("server/shards.yml", false);
                plugin.getLogger().info("[Migration] Created default server/shards.yml");
            }
        }
    }

    // ── Commands Module ───────────────────────────────────────────────────────

    private static void migrateCommandsModule(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "commandsmodule");
        if (!folder.exists()) folder.mkdirs();

        moveFile(plugin, folder, "command-control.yml",      "commandsmodule/command-control.yml");
        moveFile(plugin, folder, "commands-toggle.yml",      "commandsmodule/commands-toggle.yml");
        moveFile(plugin, folder, "interactive-commands.yml", "commandsmodule/interactive-commands.yml");
        moveFile(plugin, folder, "aliases.yml",              "commandsmodule/aliases.yml");
    }

    /** Move a single root-level file into the target folder, or extract default if missing. */
    private static void moveFile(OreoEssentials plugin, File folder, String oldName, String resourcePath) {
        File dest = new File(folder, oldName);
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), oldName);
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] " + oldName + " moved to commandsmodule/" + oldName);
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate " + oldName + ": " + e.getMessage());
            }
        } else {
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
                plugin.getLogger().info("[Migration] Created default commandsmodule/" + oldName);
            }
        }
    }

    // ── AFK Module ────────────────────────────────────────────────────────────

    /**
     * Moves all AFK-related settings out of config.yml / settings.yml and into
     * the dedicated afk/config.yml file.
     *
     * Keys migrated from config.yml:
     *   afk.auto.*            → auto.*
     *   afk-pool.*            → pool.*
     *
     * Keys migrated from settings.yml:
     *   features.afk-pool.enabled  → pool.enabled  (if not already in config.yml)
     *   afk.*                      → top-level keys (new feature fields)
     *
     * After migration both source sections are nulled and their files re-saved.
     */
    private static void migrateAfk(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "afk");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "config.yml");
        if (dest.exists()) return; // already migrated

        // Extract the bundled default first so we have a complete base to overlay onto
        plugin.saveResource("afk/config.yml", false);
        YamlConfiguration newCfg = YamlConfiguration.loadConfiguration(dest);

        boolean mainChanged     = false;
        boolean settingsChanged = false;

        // ── Migrate from config.yml (plugin.getConfig()) ─────────────────────
        FileConfiguration mainCfg = plugin.getConfig();

        // auto.*
        if (mainCfg.contains("afk.auto.enabled"))
            newCfg.set("auto.enabled", mainCfg.getBoolean("afk.auto.enabled"));
        if (mainCfg.contains("afk.auto.seconds"))
            newCfg.set("auto.seconds", mainCfg.getInt("afk.auto.seconds"));
        if (mainCfg.contains("afk.auto.check-interval-seconds"))
            newCfg.set("auto.check-interval-seconds", mainCfg.getInt("afk.auto.check-interval-seconds"));

        // pool.*
        if (mainCfg.contains("afk-pool.enabled"))
            newCfg.set("pool.enabled", mainCfg.getBoolean("afk-pool.enabled"));
        if (mainCfg.contains("afk-pool.region-name"))
            newCfg.set("pool.region-name", mainCfg.getString("afk-pool.region-name"));
        if (mainCfg.contains("afk-pool.world-name"))
            newCfg.set("pool.world-name", mainCfg.getString("afk-pool.world-name"));
        if (mainCfg.contains("afk-pool.server"))
            newCfg.set("pool.server", mainCfg.getString("afk-pool.server"));
        if (mainCfg.contains("afk-pool.cross-server"))
            newCfg.set("pool.cross-server", mainCfg.getBoolean("afk-pool.cross-server"));

        // Remove from config.yml
        if (mainCfg.contains("afk") || mainCfg.contains("afk-pool")) {
            mainCfg.set("afk", null);
            mainCfg.set("afk-pool", null);
            mainChanged = true;
        }

        // ── Migrate from settings.yml ─────────────────────────────────────────
        File settingsFile = new File(plugin.getDataFolder(), "settings.yml");
        if (settingsFile.exists()) {
            YamlConfiguration settingsCfg = YamlConfiguration.loadConfiguration(settingsFile);

            // features.afk-pool.enabled → pool.enabled (only if not already set from config.yml)
            if (!mainCfg.contains("afk-pool.enabled") && settingsCfg.contains("features.afk-pool.enabled")) {
                newCfg.set("pool.enabled", settingsCfg.getBoolean("features.afk-pool.enabled"));
            }

            // New keys (added in the previous feature release — may be present in settings.yml)
            if (settingsCfg.contains("afk.auto.permission-tiers"))
                newCfg.set("auto.permission-tiers", settingsCfg.getList("afk.auto.permission-tiers"));
            if (settingsCfg.contains("afk.actionbar.enabled"))
                newCfg.set("actionbar.enabled", settingsCfg.getBoolean("afk.actionbar.enabled"));
            if (settingsCfg.contains("afk.back-message.enabled"))
                newCfg.set("back-message.enabled", settingsCfg.getBoolean("afk.back-message.enabled"));
            if (settingsCfg.contains("afk.custom-messages"))
                newCfg.set("custom-messages", settingsCfg.getList("afk.custom-messages"));
            if (settingsCfg.contains("afk.web.enabled"))
                newCfg.set("web.enabled", settingsCfg.getBoolean("afk.web.enabled"));
            if (settingsCfg.contains("afk.web.port"))
                newCfg.set("web.port", settingsCfg.getInt("afk.web.port"));

            // Remove migrated sections from settings.yml
            if (settingsCfg.contains("features.afk-pool") || settingsCfg.contains("afk")) {
                settingsCfg.set("features.afk-pool", null);
                settingsCfg.set("afk", null);
                settingsChanged = true;
            }

            if (settingsChanged) {
                try {
                    settingsCfg.save(settingsFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("[Migration] Could not clean up settings.yml after AFK migration: " + e.getMessage());
                }
            }
        }

        // Save updated afk/config.yml
        try {
            newCfg.save(dest);
        } catch (IOException e) {
            plugin.getLogger().warning("[Migration] Could not save afk/config.yml: " + e.getMessage());
            return;
        }

        // Save updated config.yml (removes old afk.* and afk-pool.* sections)
        if (mainChanged) {
            plugin.saveConfig();
        }

        plugin.getLogger().info("[Migration] AFK config migrated to afk/config.yml");
    }

    // ── Trade ─────────────────────────────────────────────────────────────────

    /**
     * Moves trade.yml from the plugin root into trades/.
     *
     *  trades/trade.yml:
     *   - If already exists  → skip (already migrated)
     *   - Else if old trade.yml exists at plugin root → move it, preserving all config
     *   - Else               → extract default from jar
     */
    private static void migrateTrade(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "trades");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "trade.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "trade.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] trade.yml moved to trades/trade.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate trade.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("trades/trade.yml", false);
            plugin.getLogger().info("[Migration] Created default trades/trade.yml");
        }
    }

    // ── Ender Chest ───────────────────────────────────────────────────────────

    /**
     * Moves enderchest.yml from the plugin root into enderchests/.
     * If the old file had content it is ported into the new sectioned format:
     *   - flat keys (default, vip, mvp …)  → permission-slots  (enabled: true)
     *   - rank-slots section (if present)  → rank-slots        (enabled: false)
     * If no old file exists the bundled default is extracted.
     */
    private static void migrateEnderChest(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "enderchests");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "enderchest.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "enderchest.yml");
        if (old.exists()) {
            YamlConfiguration oldCfg = YamlConfiguration.loadConfiguration(old);
            YamlConfiguration newCfg = new YamlConfiguration();

            // ── permission-slots (from old flat keys) ──────────────────────
            newCfg.set("permission-slots.enabled", true);
            int oldDefault = oldCfg.getInt("default", 27);
            newCfg.set("permission-slots.default", oldDefault);
            for (String k : oldCfg.getKeys(false)) {
                if ("default".equalsIgnoreCase(k) || "rank-slots".equalsIgnoreCase(k)) continue;
                if (oldCfg.isInt(k)) newCfg.set("permission-slots." + k, oldCfg.getInt(k));
            }

            // ── rank-slots (from old rank-slots section, if present) ────────
            newCfg.set("rank-slots.enabled", false);
            org.bukkit.configuration.ConfigurationSection oldRankSlots =
                    oldCfg.getConfigurationSection("rank-slots");
            if (oldRankSlots != null) {
                for (String k : oldRankSlots.getKeys(false)) {
                    if (oldRankSlots.isInt(k)) newCfg.set("rank-slots." + k, oldRankSlots.getInt(k));
                }
            } else {
                // Populate rank-slots with the same values so it's ready to use
                newCfg.set("rank-slots.default", oldDefault);
            }

            try {
                newCfg.save(dest);
                old.delete();
                plugin.getLogger().info("[Migration] enderchest.yml ported to enderchests/enderchest.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not save enderchests/enderchest.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("enderchests/enderchest.yml", false);
            plugin.getLogger().info("[Migration] Created default enderchests/enderchest.yml");
        }
    }

    // ── Custom Currencies ─────────────────────────────────────────────────────

    /**
     * Moves currency-config.yml from the plugin root into custom-currencies/.
     *
     *  custom-currencies/currency-config.yml:
     *   - If already exists  → skip (already migrated)
     *   - Else if old currency-config.yml exists at plugin root → move it, preserving all config
     *   - Else               → CurrencyConfig will generate its own default on first load
     */
    private static void migrateCurrencyConfig(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "custom-currencies");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "currency-config.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "currency-config.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] currency-config.yml moved to custom-currencies/currency-config.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate currency-config.yml: " + e.getMessage());
            }
        }
        // If neither exists, CurrencyConfig.saveDefault() will generate the file on first load.
    }

    // ── Events Module ─────────────────────────────────────────────────────────

    /**
     * Moves events.yml from the plugin root into the chat-messaging sub-folder.
     *
     *  chat-messaging/events.yml:
     *   - If already exists  → skip (already migrated)
     *   - Else if old events.yml exists at plugin root → move it, preserving all config
     *   - Else               → EventConfig will generate its own default on first load
     */
    private static void migrateEvents(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "chat-messaging");
        if (!folder.exists()) folder.mkdirs();

        File dest = new File(folder, "events.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "events.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] events.yml moved to chat-messaging/events.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate events.yml: " + e.getMessage());
            }
        }
        // If neither exists, EventConfig.saveDefault() will generate the file on first load.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void copySection(ConfigurationSection src, YamlConfiguration dest, String prefix) {
        for (String key : src.getKeys(false)) {
            String path = prefix + "." + key;
            Object value = src.get(key);
            if (value instanceof ConfigurationSection sub) {
                copySection(sub, dest, path);
            } else {
                dest.set(path, value);
            }
        }
    }
}
