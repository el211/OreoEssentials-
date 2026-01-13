package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {
    private final Plugin plugin;      // for async scheduler
    private final String webhookUrl;

    public DiscordWebhook(Plugin plugin, String webhookUrl) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    }

    public boolean isConfigured() {
        return !webhookUrl.isEmpty();
    }

    public void sendAsync(String username, String content) {
        if (!isConfigured()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> send(username, content));
    }

    public void send(String username, String content) {
        if (!isConfigured()) return;

        String plain = stripColors(content);
        if (plain.length() > 1900) plain = plain.substring(0, 1900) + "…";

        HttpURLConnection con = null;
        try {
            URL url = new URL(webhookUrl);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("User-Agent", "OreoEssentials/DiscordWebhook");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setDoOutput(true);

            String json = "{\"username\":\"" + escapeJson(nullSafe(username)) +
                    "\",\"content\":\"" + escapeJson(plain) + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = con.getOutputStream()) {
                os.write(body);
            }

            int code = con.getResponseCode();
            // Discord typically returns 204 No Content, but allow any 2xx just in case.
            if (code < 200 || code >= 300) {
                Bukkit.getLogger().warning("[OreoEssentials] Discord webhook HTTP " + code);
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("[OreoEssentials] Discord webhook error: " + e.getMessage());
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private String stripColors(String s) {
        if (s == null) return "";
        String noAmp = ChatColor.translateAlternateColorCodes('&', s);
        return ChatColor.stripColor(noAmp);
    }

    private String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "Minecraft" : s;
    }

    private String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
