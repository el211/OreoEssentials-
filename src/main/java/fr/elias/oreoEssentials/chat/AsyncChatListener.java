// File: src/main/java/fr/elias/oreoEssentials/chat/AsyncChatListener.java
package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Formats chat using FormatManager, optionally syncs to RabbitMQ,
 * and optionally forwards to Discord via webhook if enabled in config.
 *
 * Supports two output modes:
 *  - Legacy (& codes) (default, backward compatible)
 *  - MiniMessage (chat.use-minimessage: true) with <head>, <gradient>, <hover>, <click>, etc.
 *
 * NOTE: When MiniMessage mode is enabled, your chat format strings should use MiniMessage tags
 * (e.g. <gradient:...>, <click:...>, <hover:...>, <head:...>) instead of & codes.
 */
public class AsyncChatListener implements Listener {

    private final FormatManager formatManager;
    private final CustomConfig chatConfig;
    private final ChatSyncManager syncManager;
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final MuteService muteService;

    // banned words config (from SettingsConfig)
    private final boolean bannedWordsEnabled;
    private final List<String> bannedWords;

    // MiniMessage toggle
    private final boolean useMiniMessage;

    // MiniMessage instance (includes standard tags like <head>, <hover>, <click>, <gradient> if your Adventure is new enough)
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public AsyncChatListener(
            FormatManager fm,
            CustomConfig cfg,
            ChatSyncManager sync,
            boolean discordEnabled,
            String discordWebhookUrl,
            MuteService muteService
    ) {
        this.formatManager = fm;
        this.chatConfig = cfg;
        this.syncManager = sync;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = (discordWebhookUrl == null) ? "" : discordWebhookUrl.trim();
        this.muteService = muteService;

        var settings = OreoEssentials.get().getSettingsConfig();
        this.bannedWordsEnabled = settings.bannedWordsEnabled();
        this.bannedWords = settings.bannedWords();

        final FileConfiguration conf = chatConfig == null ? null : chatConfig.getCustomConfig();
        this.useMiniMessage = conf != null && conf.getBoolean("chat.use-minimessage", false);

        Bukkit.getLogger().info("[OreoEssentials] Chat mode=" + (useMiniMessage ? "MiniMessage" : "Legacy")
                + " bannedWords=" + bannedWordsEnabled + " list=" + this.bannedWords);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!OreoEssentials.get().getSettingsConfig().chatEnabled()) {
            return; // let vanilla handle
        }

        final FileConfiguration conf = chatConfig == null ? null : chatConfig.getCustomConfig();
        if (conf == null) {
            return; // no formats found => let vanilla handle
        }

        final Player player = event.getPlayer();

        // ---------------- ModGUI chat controls (SAFE enough to check here, but we still broadcast on main) ----------------
        ModGuiService svc = OreoEssentials.get().getModGuiService();

        if (svc != null && svc.chatMuted()) {
            player.sendMessage("§cChat is currently muted.");
            event.setCancelled(true);
            return;
        }

        if (svc != null && svc.getSlowmodeSeconds() > 0) {
            if (!svc.canSendMessage(player.getUniqueId())) {
                long left = svc.getRemainingSlowmode(player.getUniqueId());
                player.sendMessage("§cYou must wait §e" + left + "s §cbefore chatting again.");
                event.setCancelled(true);
                return;
            }
            svc.recordMessage(player.getUniqueId());
        }

        if (svc != null && svc.isStaffChatEnabled(player.getUniqueId())) {
            // Staff chat: broadcast ONLY to staff, still main-thread message sending below for safety
            event.setCancelled(true);
            final String staffMsg = event.getMessage() == null ? "" : event.getMessage();

            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                for (Player p2 : Bukkit.getOnlinePlayers()) {
                    if (p2.hasPermission("oreo.staffchat")) {
                        p2.sendMessage("§b[StaffChat] §f" + player.getName() + ": §7" + staffMsg);
                    }
                }
            });
            return;
        }
        // ---------------------------------------------------------------------------------------------------------------

        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Take over the pipeline
        event.setCancelled(true);

        String message = event.getMessage();
        if (message == null) message = "";
        message = message.trim();
        if (message.isEmpty()) return;

        // Censor banned words (pure string ops; safe async)
        message = censorBannedWords(message);

        // Capture primitives for main-thread work
        final UUID senderUuid = player.getUniqueId();
        final String senderName = player.getName();
        final String serverName = Bukkit.getServer().getName();
        final String finalMessage = message;

        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            Player live = Bukkit.getPlayer(senderUuid);
            if (live == null) return; // player left

            // Format on main thread (safer because it touches displayName, LuckPerms user, etc.)
            String formatted = formatManager.formatMessage(live, finalMessage);

            // PlaceholderAPI expansion (main thread)
            formatted = applyPapiBestEffort(live, formatted);

            // Broadcast: legacy string OR Adventure component
            if (!useMiniMessage) {
                // Backward compatible legacy mode
                final String mcOut = ChatColor.translateAlternateColorCodes('&', formatted);
                Bukkit.broadcastMessage(mcOut);
            } else {
                // MiniMessage mode: parse tags like <head>, <gradient>, <hover>, <click>
                // IMPORTANT: your format should be written in MiniMessage when this mode is enabled.
                Component out;
                try {
                    out = MINI.deserialize(formatted);
                } catch (Throwable parseFail) {
                    // Fallback: show raw text (prevents total chat break on bad tags)
                    out = Component.text(formatted);
                }
                Bukkit.getServer().sendMessage(out);
            }

            // Cross-server sync (send the raw formatted string so receivers can parse in their own mode)
            try {
                if (syncManager != null) {
                    syncManager.publishMessage(senderUuid, serverName, senderName, formatted);
                }
            } catch (Throwable ex) {
                Bukkit.getLogger().severe("[OreoEssentials] ChatSync publish failed: " + ex.getMessage());
            }

            // Discord webhook (plain text)
            maybeSendToDiscord(senderName, toPlainText(formatted));
        });
    }

    private String applyPapiBestEffort(Player player, String input) {
        if (input == null) return "";
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, input);
            }
        } catch (Throwable ignored) { }
        return input;
    }

    /** Send to Discord only if enabled and URL is present. */
    private void maybeSendToDiscord(String username, String content) {
        if (!discordEnabled) return;
        if (discordWebhookUrl.isEmpty()) return;
        try {
            DiscordWebhook webhook = new DiscordWebhook(OreoEssentials.get(), discordWebhookUrl);
            webhook.sendAsync(username, content);
        } catch (Throwable ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Discord webhook send failed: " + ex.getMessage());
        }
    }

    /**
     * Convert to plain text for external outputs.
     * - In legacy mode: strip legacy colors
     * - In MiniMessage mode: best-effort parse to Component then plain text, fallback to tag-stripping regex
     */
    private String toPlainText(String formatted) {
        if (formatted == null) return "";

        if (!useMiniMessage) {
            String noAmp = formatted.replaceAll("(?i)&[0-9A-FK-ORX]", "");
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', noAmp));
        }

        try {
            Component c = MINI.deserialize(formatted);
            return PlainTextComponentSerializer.plainText().serialize(c);
        } catch (Throwable ignored) {
            // fallback: strip tags like <...>
            return formatted.replaceAll("<[^>]+>", "");
        }
    }

    /**
     * Censor banned words from the message using chat.banned-words.list.
     * Example: "fuck" -> "****"
     */
    private String censorBannedWords(String message) {
        if (!bannedWordsEnabled || bannedWords == null || bannedWords.isEmpty()) return message;
        if (message == null || message.isEmpty()) return message;

        String result = message;
        for (String word : bannedWords) {
            if (word == null || word.isBlank()) continue;

            String pattern = "(?i)" + Pattern.quote(word);
            String replacement = "*".repeat(word.length());
            result = result.replaceAll(pattern, replacement);
        }
        return result;
    }
}
