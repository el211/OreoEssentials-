// File: src/main/java/fr/elias/oreoEssentials/chat/AsyncChatListener.java
package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Formats chat using FormatManager, optionally syncs to RabbitMQ,
 * and optionally forwards to Discord via webhook if enabled in config.
 * Also supports banned-words censoring from settings.yml (chat.banned-words).
 */
public class AsyncChatListener implements Listener {

    private final FormatManager formatManager;
    private final CustomConfig chatConfig;
    private final ChatSyncManager syncManager;
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final MuteService muteService; // used to prevent publishing when muted

    // NEW: banned words config (from SettingsConfig)
    private final boolean bannedWordsEnabled;
    private final List<String> bannedWords;

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

        // Load banned-words settings from SettingsConfig
        var settings = OreoEssentials.get().getSettingsConfig();
        this.bannedWordsEnabled = settings.bannedWordsEnabled();
        this.bannedWords = settings.bannedWords();

        // Optional: debug to verify it loaded correctly
        Bukkit.getLogger().info("[OreoEssentials] Chat banned-words enabled=" +
                bannedWordsEnabled + " list=" + this.bannedWords);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // If something else (e.g., MuteListener) cancelled already, we won't run (ignoreCancelled=true)

        // Respect global master toggle from settings.yml
        if (!OreoEssentials.get().getSettingsConfig().chatEnabled()) {
            // Let vanilla/bukkit handle chat if Oreo chat formatting is disabled
            return;
        }

        // Optionally still ensure config exists (for formats), but don't use it as toggle
        final FileConfiguration conf = chatConfig == null ? null : chatConfig.getCustomConfig();
        if (conf == null) {
            // No chat-format.yml found; let vanilla handle it, or you can still continue with hardcoded fallback.
            return;
        }

        final Player player = event.getPlayer();

        // ---------------- ModGUI chat controls ----------------
        ModGuiService svc = OreoEssentials.get().getModGuiService();

        // GLOBAL MUTE
        if (svc != null && svc.chatMuted()) {
            player.sendMessage("§cChat is currently muted.");
            event.setCancelled(true);
            return;
        }

        // SLOWMODE PER-PLAYER
        if (svc != null && svc.getSlowmodeSeconds() > 0) {
            if (!svc.canSendMessage(player.getUniqueId())) {
                long left = svc.getRemainingSlowmode(player.getUniqueId());
                player.sendMessage("§cYou must wait §e" + left + "s §cbefore chatting again.");
                event.setCancelled(true);
                return;
            }
            svc.recordMessage(player.getUniqueId());
        }

        // STAFF CHAT (bypass normal public chat; only staff see it)
        if (svc != null && svc.isStaffChatEnabled(player.getUniqueId())) {
            for (Player p2 : Bukkit.getOnlinePlayers()) {
                if (p2.hasPermission("oreo.staffchat")) {
                    p2.sendMessage("§b[StaffChat] §f" + player.getName() + ": §7" + event.getMessage());
                }
            }
            event.setCancelled(true);
            return;
        }
        // -----------------------------------------------------

        // Extra guard: if muted, do not format/relay/broadcast here either
        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // We take over the chat pipeline
        event.setCancelled(true);

        String message = event.getMessage();
        if (message == null) message = "";
        message = message.trim();
        if (message.isEmpty()) return; // ignore empty messages

        // NEW: censor banned words BEFORE formatting & forwarding
        message = censorBannedWords(message);

        // Format (plugin-defined) -> may include color codes (&)
        String formatted = formatManager.formatMessage(player, message);

        // PlaceholderAPI expansion (best-effort)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatted);
            }
        } catch (Throwable ignored) {
            // Keep formatted as-is on any PAPI failure
        }

        // Translate & broadcast to Minecraft players on main thread
        final String mcOut = ChatColor.translateAlternateColorCodes('&', formatted);
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(mcOut));

        // Optional cross-server sync (RabbitMQ) — include sender UUID
        try {
            if (syncManager != null) {
                // send full formatted message INCLUDING gradients/hex
                syncManager.publishMessage(
                        player.getUniqueId(),
                        Bukkit.getServer().getName(),
                        player.getName(),
                        formatted
                );
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().severe("[OreoEssentials] ChatSync publish failed: " + ex.getMessage());
        }

        // Optional Discord webhook (guarded by flag + URL)
        maybeSendToDiscord(player.getName(), stripColors(formatted));
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

    /** Basic color/formatting strip for cleaner external outputs (sync/discord). */
    private String stripColors(String s) {
        if (s == null) return "";
        // Remove legacy ampersand codes first, then strip Bukkit color section (§) if present
        String noAmp = s.replaceAll("(?i)&[0-9A-FK-OR]", "");
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', noAmp));
    }

    /**
     * Censor banned words from the message using chat.banned-words.list.
     * Example:
     *   "fuck" -> "****"
     */
    private String censorBannedWords(String message) {
        if (!bannedWordsEnabled || bannedWords == null || bannedWords.isEmpty()) return message;
        if (message == null || message.isEmpty()) return message;

        String result = message;
        for (String word : bannedWords) {
            if (word == null || word.isBlank()) continue;

            // (?i) = case-insensitive; Pattern.quote = literal match
            String pattern = "(?i)" + Pattern.quote(word);
            String replacement = "*".repeat(word.length());

            result = result.replaceAll(pattern, replacement);
        }

        return result;
    }
}
