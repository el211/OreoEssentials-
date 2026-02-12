package fr.elias.oreoEssentials.modules.integration;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.CustomConfig;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DiscordModerationNotifier {

    public enum EventType { KICK, BAN, UNBAN, MUTE, UNMUTE, JAIL, UNJAIL }

    private final Plugin plugin;
    private final CustomConfig cfg; // wraps discord-integration.yml
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
        this.fileEnabled       = c.getBoolean("enabled", true); // default true so you can remove it from the file
        this.defaultWebhook    = safe(c.getString("default_webhook", ""));
        this.includeServerName = c.getBoolean("include_server_name", true);

        setDefaultIfMissing("events.kick.enabled", true);
        setDefaultIfMissing("events.kick.username", "Oreo Moderation");
        setDefaultIfMissing("events.kick.prefix", "**[KICK]**");
        setDefaultIfMissing("events.kick.message",
                "{server} **{by}** kicked **{player}** ({uuid}) — Reason: {reason}");

        setDefaultIfMissing("events.ban.enabled", true);
        setDefaultIfMissing("events.ban.username", "Oreo Moderation");
        setDefaultIfMissing("events.ban.prefix", "**[BAN]**");
        setDefaultIfMissing("events.ban.message",
                "{server} **{by}** banned **{player}** ({uuid}) — Reason: {reason} — {until_desc}");

        setDefaultIfMissing("events.unban.enabled", true);
        setDefaultIfMissing("events.unban.username", "Oreo Moderation");
        setDefaultIfMissing("events.unban.prefix", "**[UNBAN]**");
        setDefaultIfMissing("events.unban.message",
                "{server} **{by}** unbanned **{player}** ({uuid})");

        setDefaultIfMissing("events.mute.enabled", true);
        setDefaultIfMissing("events.mute.username", "Oreo Moderation");
        setDefaultIfMissing("events.mute.prefix", "**[MUTE]**");
        setDefaultIfMissing("events.mute.message",
                "{server} **{by}** muted **{player}** ({uuid}) — Reason: {reason} — {until_desc}");

        setDefaultIfMissing("events.unmute.enabled", true);
        setDefaultIfMissing("events.unmute.username", "Oreo Moderation");
        setDefaultIfMissing("events.unmute.prefix", "**[UNMUTE]**");
        setDefaultIfMissing("events.unmute.message",
                "{server} **{by}** unmuted **{player}** ({uuid})");

        // ---- ADDED: jail/unjail defaults ----
        setDefaultIfMissing("events.jail.enabled", true);
        setDefaultIfMissing("events.jail.username", "Oreo Moderation");
        setDefaultIfMissing("events.jail.prefix", "**[JAIL]**");
        setDefaultIfMissing("events.jail.message",
                "{server} **{by}** jailed **{player}** ({uuid}) — Jail: **{jail}** Cell: **{cell}** — Reason: {reason} — {until_desc}");

        setDefaultIfMissing("events.unjail.enabled", true);
        setDefaultIfMissing("events.unjail.username", "Oreo Moderation");
        setDefaultIfMissing("events.unjail.prefix", "**[UNJAIL]**");
        setDefaultIfMissing("events.unjail.message",
                "{server} **{by}** released **{player}** ({uuid}) from jail");
    }

    private void setDefaultIfMissing(String path, Object value) {
        var c = cfg.getCustomConfig();
        if (!c.isSet(path)) {
            c.set(path, value);
            cfg.saveCustomConfig();
        }
    }

    public boolean isEnabled() {
        // master toggle + file toggle + at least one webhook present
        return OreoEssentials.get().getSettingsConfig().discordModerationEnabled()
                && fileEnabled
                && hasAnyWebhook();
    }

    /* ------------------ Public helpers (existing) ------------------ */

    public void notifyKick(String targetName, UUID targetId, String reason, String by) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, reason, by, null);
        ph.put("until_desc", ""); // not used
        send(EventType.KICK, ph);
    }

    public void notifyBan(String targetName, UUID targetId, String reason, String by, Long untilEpochMillis) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, reason, by, untilEpochMillis);
        ph.put("until_desc", untilDesc(untilEpochMillis));
        send(EventType.BAN, ph);
    }

    public void notifyUnban(String targetName, UUID targetId, String by) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, "", by, null);
        ph.put("until_desc", "");
        send(EventType.UNBAN, ph);
    }

    public void notifyMute(String targetName, UUID targetId, String reason, String by, long untilEpochMillis) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, reason, by, untilEpochMillis);
        ph.put("until_desc", untilDesc(untilEpochMillis));
        send(EventType.MUTE, ph);
    }

    public void notifyUnmute(String targetName, UUID targetId, String by) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, "", by, null);
        ph.put("until_desc", "");
        send(EventType.UNMUTE, ph);
    }

    /* ------------------ ADDED: jail helpers ------------------ */

    /** Send a Discord message for a jail sentence (perm or timed). */
    public void notifyJail(String targetName,
                           UUID targetId,
                           String jailName,
                           String cellId,
                           String reason,
                           String by,
                           Long untilEpochMillis) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, reason, by, untilEpochMillis);
        ph.put("until_desc", untilDesc(untilEpochMillis));
        ph.put("jail", safe(jailName));
        ph.put("cell", safe(cellId));
        send(EventType.JAIL, ph);
    }

    /** Send a Discord message for a release from jail. */
    public void notifyUnjail(String targetName, UUID targetId, String by) {
        Map<String,String> ph = basePlaceholders(targetName, targetId, "", by, null);
        ph.put("until_desc", "");
        send(EventType.UNJAIL, ph);
    }

    /* ------------------ Core sending (ASYNC) ------------------ */

    private void send(EventType type, Map<String,String> placeholders) {
        if (!isEnabled()) return;
        var c = cfg.getCustomConfig();
        String node = "events." + type.name().toLowerCase();
        boolean evEnabled = c.getBoolean(node + ".enabled", true);
        if (!evEnabled) return;

        String webhook  = safe(c.getString(node + ".webhook", defaultWebhook));
        String username = safe(c.getString(node + ".username", "Oreo Moderation"));
        String prefix   = safe(c.getString(node + ".prefix", "**[" + type.name() + "]**"));
        String message  = safe(c.getString(node + ".message",
                "{server} " + type.name() + " {player} ({uuid})"));

        if (webhook.isEmpty()) return;

        // Offload EVERYTHING to async (including template rendering).
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Server name placeholder (optional)
                if (includeServerName) {
                    placeholders.putIfAbsent("server", "**(" + Bukkit.getServer().getName() + ")**");
                } else {
                    placeholders.putIfAbsent("server", "");
                }

                String body = renderTemplate(message, placeholders);
                String finalContent = prefix.isEmpty() ? body : (prefix + " " + body);

                // Use synchronous send inside our async task
                DiscordWebhook wh = new DiscordWebhook(plugin, webhook);
                wh.send(username, finalContent);
            } catch (Throwable t) {
                plugin.getLogger().warning("[DiscordModerationNotifier] Failed to send " + type + " webhook: " + t.getMessage());
            }
        });
    }


    private String renderTemplate(String template, Map<String,String> placeholders) {
        if (template == null) template = "";
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String key = "{" + e.getKey() + "}";
            String val = Objects.toString(e.getValue(), "");
            out = out.replace(key, val);
        }
        return out;
    }

    private Map<String,String> basePlaceholders(String playerName, UUID playerId, String reason, String by, Long untilMillis) {
        Map<String,String> ph = new HashMap<>();
        ph.put("player",  safe(playerName));
        ph.put("uuid",    playerId == null ? "" : playerId.toString());
        ph.put("reason",  safe(reason));
        ph.put("by",      safe(by));
        if (untilMillis == null || untilMillis <= 0) {
            ph.put("until_abs", "Permanent");
            ph.put("until_rel", "Permanent");
        } else {
            long secs = untilMillis / 1000L;
            ph.put("until_abs", "<t:" + secs + ":F>"); // absolute time
            ph.put("until_rel", "<t:" + secs + ":R>"); // relative time
        }
        // until_desc is set by caller
        ph.putIfAbsent("until_desc", "");
        // server is set in send()
        ph.putIfAbsent("server", "");
        return ph;
    }

    private String untilDesc(Long untilMillis) {
        if (untilMillis == null || untilMillis <= 0) return "Permanent";
        long secs = untilMillis / 1000L;
        return "Until: <t:" + secs + ":F> ( <t:" + secs + ":R> )";
    }

    /* ------------------ Utils ------------------ */

    private boolean hasAnyWebhook() {
        var c = cfg.getCustomConfig();
        if (!safe(c.getString("default_webhook", "")).isEmpty()) return true;
        for (EventType t : EventType.values()) {
            String node = "events." + t.name().toLowerCase() + ".webhook";
            if (!safe(c.getString(node, "")).isEmpty()) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}
