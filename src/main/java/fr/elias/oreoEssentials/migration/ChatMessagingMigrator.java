package fr.elias.oreoEssentials.migration;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * One-time migration that moves chat and messaging configs into
 * plugins/OreoEssentials/chat-messaging/.
 *
 * Migration logic (runs on every startup, but is a no-op once already done):
 *
 *  chat-format.yml:
 *   - If chat-messaging/chat-format.yml already exists  → skip
 *   - Else if old chat-format.yml exists at plugin root → move it to chat-messaging/chat-format.yml
 *   - Else                                              → extract default from jar
 *
 *  death-messages.yml:
 *   - If chat-messaging/death-messages.yml already exists → skip
 *   - Else if old death-messages.yml exists at plugin root → move it
 *   - Else                                                 → extract default from jar
 *
 *  join-quit-messages.yml:
 *   - If chat-messaging/join-quit-messages.yml already exists → skip
 *   - Else extract Join_messages, Quit_messages, Automatic_message, conversations
 *     from config.yml into chat-messaging/join-quit-messages.yml, then remove them from config.yml
 */
public final class ChatMessagingMigrator {

    private static final String[] JOIN_QUIT_SECTIONS = {
            "Join_messages", "Quit_messages", "Automatic_message", "conversations"
    };

    private ChatMessagingMigrator() {}

    public static void migrate(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "chat-messaging");
        if (!folder.exists()) folder.mkdirs();

        migrateChatFormat(plugin, folder);
        migrateDeathMessages(plugin, folder);
        migrateJoinQuitMessages(plugin, folder);
    }

    // ── Chat Format ───────────────────────────────────────────────────────────

    private static void migrateChatFormat(OreoEssentials plugin, File folder) {
        File dest = new File(folder, "chat-format.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "chat-format.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] chat-format.yml moved to chat-messaging/chat-format.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate chat-format.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("chat-messaging/chat-format.yml", false);
            plugin.getLogger().info("[Migration] Created default chat-messaging/chat-format.yml");
        }
    }

    // ── Death Messages ────────────────────────────────────────────────────────

    private static void migrateDeathMessages(OreoEssentials plugin, File folder) {
        File dest = new File(folder, "death-messages.yml");
        if (dest.exists()) return;

        File old = new File(plugin.getDataFolder(), "death-messages.yml");
        if (old.exists()) {
            try {
                Files.copy(old.toPath(), dest.toPath());
                old.delete();
                plugin.getLogger().info("[Migration] death-messages.yml moved to chat-messaging/death-messages.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not migrate death-messages.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("chat-messaging/death-messages.yml", false);
            plugin.getLogger().info("[Migration] Created default chat-messaging/death-messages.yml");
        }
    }

    // ── Join / Quit / AutoMessage / Conversations ─────────────────────────────

    private static void migrateJoinQuitMessages(OreoEssentials plugin, File folder) {
        File dest = new File(folder, "join-quit-messages.yml");
        if (dest.exists()) return;

        FileConfiguration mainConfig = plugin.getConfig();
        boolean hasAnySection = false;
        for (String section : JOIN_QUIT_SECTIONS) {
            if (mainConfig.isConfigurationSection(section)) {
                hasAnySection = true;
                break;
            }
        }

        if (hasAnySection) {
            YamlConfiguration out = new YamlConfiguration();
            for (String section : JOIN_QUIT_SECTIONS) {
                ConfigurationSection sec = mainConfig.getConfigurationSection(section);
                if (sec != null) {
                    copySection(sec, out, section);
                    mainConfig.set(section, null);
                }
            }
            try {
                out.save(dest);
                plugin.saveConfig();
                plugin.getLogger().info("[Migration] Join/quit/automessage/conversations config moved to chat-messaging/join-quit-messages.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("[Migration] Could not save join-quit-messages.yml: " + e.getMessage());
            }
        } else {
            plugin.saveResource("chat-messaging/join-quit-messages.yml", false);
            plugin.getLogger().info("[Migration] Created default chat-messaging/join-quit-messages.yml");
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
