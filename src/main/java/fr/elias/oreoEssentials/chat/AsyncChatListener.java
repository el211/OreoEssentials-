// File: src/main/java/fr/elias/oreoEssentials/chat/AsyncChatListener.java
package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public final class AsyncChatListener implements Listener {

    private final FormatManager formatManager;
    private final CustomConfig chatCfg;
    private final ChatSyncManager sync;
    private final MuteService muteService;

    private final boolean discordEnabled;
    private final String discordWebhookUrl;

    // formatting toggles
    private final boolean useMiniMessage;
    private final boolean translateLegacyAmp;
    private final boolean stripNameColors;

    // PAPI toggles
    private final boolean papiApplyToFormat;
    private final boolean papiApplyToMessage;

    // moderation
    private final boolean bannedWordsEnabled;
    private final List<String> bannedWords;

    // optional glyphs (kept for <glyph:...>, independent of <head:...>)
    private final Key headFontKey;
    private final String headDefaultGlyph;
    private final Map<String,String> headGlyphs;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern AMP_OR_SECTION = Pattern.compile("(?i)(?:&|§)[0-9A-FK-ORX]");

    public AsyncChatListener(
            FormatManager formatManager,
            CustomConfig chatConfig,
            ChatSyncManager syncManager,
            boolean discordEnabled,
            String discordWebhookUrl,
            MuteService muteService
    ) {
        this.formatManager = Objects.requireNonNull(formatManager);
        this.chatCfg = Objects.requireNonNull(chatConfig);
        this.sync = syncManager;
        this.muteService = muteService;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = (discordWebhookUrl == null ? "" : discordWebhookUrl.trim());

        final FileConfiguration conf = chatCfg.getCustomConfig();

        this.useMiniMessage = conf.getBoolean("chat.use-minimessage", false);
        this.translateLegacyAmp = conf.getBoolean("chat.minimessage-translate-legacy-amp", true);
        this.stripNameColors = conf.getBoolean("chat.strip-name-colors", false);

        // NEW: PAPI toggles
        this.papiApplyToFormat = conf.getBoolean("chat.papi.apply-to-format", true);
        this.papiApplyToMessage = conf.getBoolean("chat.papi.apply-to-message", true);

        // optional glyph config (not used by <head>)
        Key font = Key.key("minecraft:default");
        String defGlyph = "\u25A0";
        Map<String,String> map = new HashMap<>();
        try {
            String fontStr = conf.getString("chat.head.font", "oreo:heads");
            font = Key.key(fontStr);
            defGlyph = conf.getString("chat.head.default-glyph", "\uE001");
            ConfigurationSection sec = conf.getConfigurationSection("chat.head.glyphs");
            if (sec != null) {
                for (String g : sec.getKeys(false)) {
                    String val = sec.getString(g, "");
                    if (val != null && !val.isBlank()) map.put(g.toLowerCase(Locale.ROOT), val);
                }
            }
        } catch (Throwable ignored) {}
        this.headFontKey = font;
        this.headDefaultGlyph = defGlyph;
        this.headGlyphs = map;

        var settings = OreoEssentials.get().getSettingsConfig();
        this.bannedWordsEnabled = settings.bannedWordsEnabled();
        this.bannedWords = settings.bannedWords();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        if (!OreoEssentials.get().getSettingsConfig().chatEnabled()) return;

        final Player player = event.getPlayer();
        final ModGuiService gui = OreoEssentials.get().getModGuiService();

        if (gui != null && gui.chatMuted()) {
            player.sendMessage("§cChat is currently muted.");
            event.setCancelled(true);
            return;
        }
        if (gui != null && gui.getSlowmodeSeconds() > 0) {
            if (!gui.canSendMessage(player.getUniqueId())) {
                long left = gui.getRemainingSlowmode(player.getUniqueId());
                player.sendMessage("§cYou must wait §e" + left + "s §cbefore chatting again.");
                event.setCancelled(true);
                return;
            }
            gui.recordMessage(player.getUniqueId());
        }
        if (gui != null && gui.isStaffChatEnabled(player.getUniqueId())) {
            event.setCancelled(true);
            final String staffMsg = safe(event.getMessage());
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("oreo.staffchat")) {
                        p.sendMessage("§b[StaffChat] §f" + player.getName() + ": §7" + staffMsg);
                    }
                }
            });
            return;
        }
        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        String msg = safe(event.getMessage()).trim();
        if (msg.isEmpty()) return;
        if (bannedWordsEnabled) msg = censor(msg, bannedWords);

        final UUID sender = player.getUniqueId();
        final String rawMsg = msg;
        final String serverName = Bukkit.getServer().getName();

        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            Player live = Bukkit.getPlayer(sender);
            if (live == null) return;

            // 1) Base format with %chat_message%
            String fmt = formatManager.formatMessage(live, rawMsg);

            // 2) PAPI on FORMAT (group, economy, custom expansions, etc.)
            if (papiApplyToFormat) {
                fmt = applyPapi(fmt, live);
            }

            // 3) LP prefix fallback
            fmt = fillLuckPermsPrefixIfNeeded(fmt, live);

            // 4) Optional legacy-& bridge for FORMAT
            if (useMiniMessage && translateLegacyAmp && looksLegacy(fmt)) {
                fmt = ampersandToMiniMessage(fmt);
            }

            // 5) Prepare player MESSAGE
            String msgForPlaceholder = rawMsg;

            // 5a) PAPI on MESSAGE (let users use %placeholders% in their message)
            if (papiApplyToMessage) {
                msgForPlaceholder = applyPapi(msgForPlaceholder, live);
            }
            // 5b) Optional legacy-& bridge for MESSAGE (so &a / &xRRGGBB works)
            if (useMiniMessage && translateLegacyAmp && looksLegacy(msgForPlaceholder)) {
                msgForPlaceholder = ampersandToMiniMessage(msgForPlaceholder);
            }

            // 6) Render & broadcast
            if (useMiniMessage) {
                try {
                    TagResolver all = TagResolver.resolver(
                            Placeholder.parsed("chat_message", msgForPlaceholder),
                            Placeholder.unparsed("player_name", live.getName()),
                            Placeholder.unparsed("player_displayname", displayName(live)),
                            playerPlaceholders(live)
                    );
                    Component out = MM.deserialize(fmt, all);

                    Bukkit.getServer().sendMessage(out);
                    maybeDiscord(live.getName(), PlainTextComponentSerializer.plainText().serialize(out));
                    maybeSync(sender, serverName, live.getName(), fmt);
                } catch (Throwable t) {
                    String plain = stripTags(fmt);
                    Bukkit.broadcast(Component.text(plain));
                    maybeDiscord(live.getName(), plain);
                    maybeSync(sender, serverName, live.getName(), fmt);
                }
            } else {
                String legacy = ChatColor.translateAlternateColorCodes('&', fmt);
                Bukkit.broadcastMessage(legacy);
                maybeDiscord(live.getName(), ChatColor.stripColor(legacy));
                maybeSync(sender, serverName, live.getName(), fmt);
            }
        });
    }

    /* ---------------- helpers ---------------- */

    private String displayName(Player p) { return stripNameColors ? p.getName() : p.getDisplayName(); }
    private String safe(String s) { return (s == null) ? "" : s; }

    // PAPI call (player-aware). Returns input unchanged if PAPI is not present.
    private String applyPapi(String input, Player p) {
        if (input == null || input.isEmpty()) return input;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, input);
            }
        } catch (Throwable ignored) {}
        return input;
    }

    private String fillLuckPermsPrefixIfNeeded(String fmt, Player p) {
        if (fmt == null || !fmt.contains("%luckperms_prefix%")) return fmt;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            CachedMetaData meta = lp.getPlayerAdapter(Player.class).getMetaData(p);
            String prefix = meta.getPrefix();
            if (prefix == null) prefix = "";
            return fmt.replace("%luckperms_prefix%", prefix);
        } catch (Throwable ignored) { return fmt; }
    }

    private String ampersandToMiniMessage(String input) {
        Component comp = LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        return MiniMessage.miniMessage().serialize(comp);
    }

    private boolean looksLegacy(String s) { return s != null && AMP_OR_SECTION.matcher(s).find(); }
    private String stripTags(String s) { return s == null ? "" : s.replaceAll("<[^>]+>", ""); }

    private String censor(String msg, List<String> words) {
        if (msg == null || msg.isEmpty() || words == null || words.isEmpty()) return msg;
        String out = msg;
        for (String w : words) {
            if (w == null || w.isBlank()) continue;
            String pat = "(?i)" + Pattern.quote(w);
            out = out.replaceAll(pat, "*".repeat(w.length()));
        }
        return out;
    }

    private void maybeSync(UUID uuid, String server, String name, String formatted) {
        try { if (sync != null) sync.publishMessage(uuid, server, name, formatted); }
        catch (Throwable ex) { Bukkit.getLogger().severe("[ChatSync] publish failed: " + ex.getMessage()); }
    }

    private void maybeDiscord(String username, String content) {
        if (!discordEnabled || discordWebhookUrl.isEmpty()) return;
        try { new DiscordWebhook(OreoEssentials.get(), discordWebhookUrl).sendAsync(username, content); }
        catch (Throwable ex) { Bukkit.getLogger().warning("[Discord] send failed: " + ex.getMessage()); }
    }

    /* ---------- extra player placeholders ---------- */

    private TagResolver playerPlaceholders(Player p) {
        World w = p.getWorld();
        String world = (w != null ? w.getName() : "world");
        String x = fmtCoord(p.getLocation().getX());
        String y = fmtCoord(p.getLocation().getY());
        String z = fmtCoord(p.getLocation().getZ());
        String ping = String.valueOf(getPingSafe(p));
        String hp = String.valueOf((int) Math.ceil(p.getHealth()));
        String maxHp = String.valueOf((int) Math.ceil(p.getMaxHealth()));
        String level = String.valueOf(p.getLevel());
        String gm = gmName(p.getGameMode());
        String uuid = p.getUniqueId().toString();

        String lpPrimary = resolvePrimaryGroup(p);
        String server = Bukkit.getServer().getName();
        String time24 = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        return TagResolver.resolver(
                Placeholder.unparsed("player_uuid", uuid),
                Placeholder.unparsed("player_world", world),
                Placeholder.unparsed("player_x", x),
                Placeholder.unparsed("player_y", y),
                Placeholder.unparsed("player_z", z),
                Placeholder.unparsed("player_ping", ping),
                Placeholder.unparsed("player_health", hp),
                Placeholder.unparsed("player_max_health", maxHp),
                Placeholder.unparsed("player_level", level),
                Placeholder.unparsed("player_gamemode", gm),
                Placeholder.unparsed("lp_primary_group", lpPrimary),
                Placeholder.unparsed("server_name", server),
                Placeholder.unparsed("time_24h", time24),
                Placeholder.unparsed("date", date)
        );
    }

    private String fmtCoord(double v) { return String.format(Locale.ENGLISH, "%.1f", v); }
    private int getPingSafe(Player p) { try { return p.getPing(); } catch (Throwable ignored) { return -1; } }
    private String gmName(GameMode gm) { return (gm == null) ? "SURVIVAL" : gm.name(); }

    private String resolvePrimaryGroup(Player p) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User u = lp.getPlayerAdapter(Player.class).getUser(p);
            if (u == null) return "default";
            String primary = u.getPrimaryGroup();
            if (primary != null) return primary;
            for (Group g : u.getInheritedGroups(u.getQueryOptions())) {
                if (g != null) return g.getName();
            }
        } catch (Throwable ignored) {}
        return "default";
    }

    /* ---- OPTIONAL: glyph support if you ever want <glyph:...> in formats ---- */

    private TagResolver glyphTagResolver(Player self) {
        String glyph = resolveGlyph(self);
        Component icon = Component.text(glyph).style(s -> s.font(headFontKey));
        return TagResolver.resolver("glyph", (args, ctx) -> net.kyori.adventure.text.minimessage.tag.Tag.inserting(icon));
    }

    private String resolveGlyph(Player self) {
        if (self == null) return headDefaultGlyph;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User u = lp.getPlayerAdapter(Player.class).getUser(self);
            if (u != null) {
                String meta = u.getCachedData().getMetaData().getMetaValue("chat-glyph");
                if (meta != null && !meta.isBlank()) return meta;
                String primary = u.getPrimaryGroup();
                if (primary != null) {
                    String v = headGlyphs.get(primary.toLowerCase(Locale.ROOT));
                    if (v != null && !v.isBlank()) return v;
                }
                for (Group g : u.getInheritedGroups(u.getQueryOptions())) {
                    if (g == null) continue;
                    String v = headGlyphs.get(g.getName().toLowerCase(Locale.ROOT));
                    if (v != null && !v.isBlank()) return v;
                }
            }
        } catch (Throwable ignored) {}
        return headDefaultGlyph;
    }
}
