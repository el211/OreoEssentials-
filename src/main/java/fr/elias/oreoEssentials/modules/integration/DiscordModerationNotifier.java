package fr.elias.oreoEssentials.modules.integration;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.CustomConfig;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DiscordModerationNotifier {

    public enum EventType { KICK, BAN, UNBAN, MUTE, UNMUTE, JAIL, UNJAIL }

    private final Plugin plugin;
    private final CustomConfig cfg;
    private boolean fileEnabled;
    private String defaultWebhook;
    private boolean includeServerName;

    public DiscordModerationNotifier(Plugin plugin) {
        this.plugin = plugin;
        this.cfg = new CustomConfig(OreoEssentials.get(), "discord-integration.yml");
        reload();
    }

    public void reload() {
        var c = cfg.getCustomConfig();
        this.fileEnabled       = c.getBoolean("enabled", true);
        this.defaultWebhook    = safe(c.getString("default_webhook", ""));
        this.includeServerName = c.getBoolean("include_server_name", true);

        boolean dirty = false;
        dirty |= setDefault(c, "events.kick.enabled", true);
        dirty |= setDefault(c, "events.kick.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.kick.prefix", "**[KICK]**");
        dirty |= setDefault(c, "events.kick.message", "{server} **{by}** kicked **{player}** ({uuid}) — Reason: {reason}");

        dirty |= setDefault(c, "events.ban.enabled", true);
        dirty |= setDefault(c, "events.ban.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.ban.prefix", "**[BAN]**");
        dirty |= setDefault(c, "events.ban.message", "{server} **{by}** banned **{player}** ({uuid}) — Reason: {reason} — {until_desc}");

        dirty |= setDefault(c, "events.unban.enabled", true);
        dirty |= setDefault(c, "events.unban.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.unban.prefix", "**[UNBAN]**");
        dirty |= setDefault(c, "events.unban.message", "{server} **{by}** unbanned **{player}** ({uuid})");

        dirty |= setDefault(c, "events.mute.enabled", true);
        dirty |= setDefault(c, "events.mute.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.mute.prefix", "**[MUTE]**");
        dirty |= setDefault(c, "events.mute.message", "{server} **{by}** muted **{player}** ({uuid}) — Reason: {reason} — {until_desc}");

        dirty |= setDefault(c, "events.unmute.enabled", true);
        dirty |= setDefault(c, "events.unmute.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.unmute.prefix", "**[UNMUTE]**");
        dirty |= setDefault(c, "events.unmute.message", "{server} **{by}** unmuted **{player}** ({uuid})");

        dirty |= setDefault(c, "events.jail.enabled", true);
        dirty |= setDefault(c, "events.jail.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.jail.prefix", "**[JAIL]**");
        dirty |= setDefault(c, "events.jail.message", "{server} **{by}** jailed **{player}** ({uuid}) — Jail: **{jail}** Cell: **{cell}** — Reason: {reason} — {until_desc}");

        dirty |= setDefault(c, "events.unjail.enabled", true);
        dirty |= setDefault(c, "events.unjail.username", "Oreo Moderation");
        dirty |= setDefault(c, "events.unjail.prefix", "**[UNJAIL]**");
        dirty |= setDefault(c, "events.unjail.message", "{server} **{by}** released **{player}** ({uuid}) from jail");

        if (dirty) cfg.saveCustomConfig();
    }

    private boolean setDefault(FileConfiguration c, String path, Object value) {
        if (!c.isSet(path)) {
            c.set(path, value);
            return true;
        }
        return false;
    }

    public boolean isEnabled() {
        return OreoEssentials.get().getSettingsConfig().discordModerationEnabled()
                && fileEnabled
                && hasAnyWebhook();
    }

    public void notifyKick(String targetName, UUID targetId, String reason, String by) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, reason, by, null);
        ph.put("until_desc", "");
        send(EventType.KICK, ph);
    }

    public void notifyBan(String targetName, UUID targetId, String reason, String by, Long untilEpochMillis) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, reason, by, untilEpochMillis);
        ph.put("until_desc", untilDesc(untilEpochMillis));
        send(EventType.BAN, ph);
    }

    public void notifyUnban(String targetName, UUID targetId, String by) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, "", by, null);
        ph.put("until_desc", "");
        send(EventType.UNBAN, ph);
    }

    public void notifyMute(String targetName, UUID targetId, String reason, String by, Long untilEpochMillis) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, reason, by, untilEpochMillis);
        ph.put("until_desc", untilDesc(untilEpochMillis));
        send(EventType.MUTE, ph);
    }

    public void notifyUnmute(String targetName, UUID targetId, String by) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, "", by, null);
        ph.put("until_desc", "");
        send(EventType.UNMUTE, ph);
    }

    public void notifyJail(String targetName, UUID targetId, String jailName, String cellId, String reason, String by, Long untilEpochMillis) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, reason, by, untilEpochMillis);
        ph.put("until_desc", untilDesc(untilEpochMillis));
        ph.put("jail", safe(jailName));
        ph.put("cell", safe(cellId));
        send(EventType.JAIL, ph);
    }

    public void notifyUnjail(String targetName, UUID targetId, String by) {
        Map<String, String> ph = basePlaceholders(targetName, targetId, "", by, null);
        ph.put("until_desc", "");
        send(EventType.UNJAIL, ph);
    }

    private void send(EventType type, Map<String, String> placeholders) {
        if (!isEnabled()) return;
        var c = cfg.getCustomConfig();
        String node = "events." + type.name().toLowerCase();
        if (!c.getBoolean(node + ".enabled", true)) return;

        String webhook  = safe(c.getString(node + ".webhook", defaultWebhook));
        String username = safe(c.getString(node + ".username", "Oreo Moderation"));
        String prefix   = safe(c.getString(node + ".prefix", "**[" + type.name() + "]**"));
        String message  = safe(c.getString(node + ".message", "{server} " + type.name() + " {player} ({uuid})"));

        if (webhook.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                placeholders.putIfAbsent("server", includeServerName ? "**(" + Bukkit.getServer().getName() + ")**" : "");
                String body = renderTemplate(message, placeholders);
                String finalContent = prefix.isEmpty() ? body : (prefix + " " + body);
                new DiscordWebhook(plugin, webhook).send(username, finalContent);
            } catch (Throwable t) {
                plugin.getLogger().warning("[DiscordModerationNotifier] Failed to send " + type + " webhook: " + t.getMessage());
            }
        });
    }

    private String renderTemplate(String template, Map<String, String> placeholders) {
        if (template == null) template = "";
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", Objects.toString(e.getValue(), ""));
        }
        return out;
    }

    private Map<String, String> basePlaceholders(String playerName, UUID playerId, String reason, String by, Long untilMillis) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", safe(playerName));
        ph.put("uuid",   playerId == null ? "" : playerId.toString());
        ph.put("reason", safe(reason));
        ph.put("by",     safe(by));
        if (untilMillis == null || untilMillis <= 0) {
            ph.put("until_abs", "Permanent");
            ph.put("until_rel", "Permanent");
        } else {
            long secs = untilMillis / 1000L;
            ph.put("until_abs", "<t:" + secs + ":F>");
            ph.put("until_rel", "<t:" + secs + ":R>");
        }
        ph.putIfAbsent("until_desc", "");
        ph.putIfAbsent("server", "");
        return ph;
    }

    private String untilDesc(Long untilMillis) {
        if (untilMillis == null || untilMillis <= 0) return "Permanent";
        long secs = untilMillis / 1000L;
        return "Until: <t:" + secs + ":F> ( <t:" + secs + ":R> )";
    }

    private boolean hasAnyWebhook() {
        var c = cfg.getCustomConfig();
        if (!safe(c.getString("default_webhook", "")).isEmpty()) return true;
        for (EventType t : EventType.values()) {
            if (!safe(c.getString("events." + t.name().toLowerCase() + ".webhook", "")).isEmpty()) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}