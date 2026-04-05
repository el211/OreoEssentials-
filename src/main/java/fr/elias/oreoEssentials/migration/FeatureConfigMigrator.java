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
