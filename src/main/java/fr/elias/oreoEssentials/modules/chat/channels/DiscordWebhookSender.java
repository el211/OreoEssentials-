package fr.elias.oreoEssentials.modules.chat.channels;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for sending messages to Discord webhooks
 */
public class DiscordWebhookSender {

    /**
     * Send a message to a Discord webhook
     *
     * @param webhookUrl The Discord webhook URL
     * @param username The username to display (server name or channel name)
     * @param content The message content (plain text)
     * @param playerName The player who sent the message
     */
    public static void sendAsync(String webhookUrl, String username, String content, String playerName) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("OreoEssentials"),
                () -> send(webhookUrl, username, content, playerName)
        );
    }

    /**
     * Send a message to Discord (should be called async)
     */
    private static void send(String webhookUrl, String username, String content, String playerName) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "OreoEssentials-Discord-Bridge");
            connection.setDoOutput(true);

            // Build JSON payload
            String jsonPayload = buildJsonPayload(username, content, playerName);

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Bukkit.getLogger().warning("[Discord] Failed to send webhook: HTTP " + responseCode);
            }

            connection.disconnect();

        } catch (Exception e) {
            Bukkit.getLogger().warning("[Discord] Error sending webhook: " + e.getMessage());
        }
    }

    /**
     * Build JSON payload for Discord webhook
     */
    private static String buildJsonPayload(String username, String content, String playerName) {
        // Escape JSON special characters
        content = escapeJson(content);
        playerName = escapeJson(playerName);
        username = escapeJson(username);

        // Build simple JSON (avoiding dependencies)
        return String.format(
                "{\"username\":\"%s\",\"content\":\"**%s**: %s\"}",
                username,
                playerName,
                content
        );
    }

    /**
     * Escape JSON special characters
     */
    private static String escapeJson(String str) {
        if (str == null) return "";

        return str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Strip MiniMessage/Minecraft color codes from text for Discord
     */
    public static String stripFormatting(String text) {
        if (text == null) return "";

        // Remove MiniMessage tags
        text = text.replaceAll("<[^>]+>", "");

        // Remove legacy color codes
        text = text.replaceAll("§[0-9a-fk-or]", "");

        // Remove &color codes
        text = text.replaceAll("&[0-9a-fk-or]", "");

        return text;
    }
}