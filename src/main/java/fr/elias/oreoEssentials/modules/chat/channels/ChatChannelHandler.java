package fr.elias.oreoEssentials.modules.chat.channels;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.ChatHoverProvider;
import fr.elias.oreoEssentials.modules.chat.ChatItemHandler;
import fr.elias.oreoEssentials.modules.chat.CustomConfig;
import fr.elias.oreoEssentials.modules.chat.ChatSyncManager;
import fr.elias.oreoEssentials.modules.chat.FormatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class ChatChannelHandler {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SEC =
            LegacyComponentSerializer.legacySection();

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private final ChatSyncManager syncManager;
    private final ChatItemHandler chatItemHandler;
    private final ChatHoverProvider hoverProvider;

    private final boolean defaultDiscordEnabled;
    private final String defaultDiscordWebhookUrl;

    public ChatChannelHandler(
            OreoEssentials plugin,
            ChatChannelManager channelManager,
            ChatSyncManager syncManager,
            FormatManager formatManager,
            CustomConfig chatConfig
    ) {
        this(plugin, channelManager, syncManager, formatManager, chatConfig, false, "");
    }

    public ChatChannelHandler(
            OreoEssentials plugin,
            ChatChannelManager channelManager,
            ChatSyncManager syncManager,
            FormatManager formatManager,
            CustomConfig chatConfig,
            boolean defaultDiscordEnabled,
            String defaultDiscordWebhookUrl
    ) {
        this.plugin = plugin;
        this.channelManager = channelManager;
        this.syncManager = syncManager;
        this.defaultDiscordEnabled = defaultDiscordEnabled;
        this.defaultDiscordWebhookUrl = defaultDiscordWebhookUrl == null ? "" : defaultDiscordWebhookUrl.trim();

        this.chatItemHandler = new ChatItemHandler(chatConfig);

        FileConfiguration cfg = chatConfig.getCustomConfig();
        this.hoverProvider = new ChatHoverProvider(
                cfg.getBoolean("chat.hover.enabled", true),
                cfg.getStringList("chat.hover.lines")
        );
    }


    public void sendChannelMessage(Player sender, String rawMessage, ChatChannel channel) {
        if (channel == null || !channel.isEnabled()) {
            channel = channelManager.getDefaultChannel();
            if (channel == null) {
                sender.sendMessage(MM.deserialize("<red>No chat channels are configured. Contact an admin."));
                return;
            }
            channelManager.setPlayerChannel(sender, channel);
        }

        if (!channel.canTalk(sender)) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission to talk in <gray>"
                    + plainDisplayName(channel) + "</gray><red>."));
            return;
        }

        List<Player> recipients = getRecipients(sender, channel);
        if (recipients.isEmpty()) {
            sender.sendMessage(MM.deserialize("<gray>No one can hear you in " + channel.getDisplayName() + "<gray>."));
            return;
        }
        Component component = buildChannelMessage(sender, rawMessage, channel);

        // [item] placeholder
        if (chatItemHandler.containsItemPlaceholder(rawMessage)) {
            component = chatItemHandler.processItemPlaceholder(component, sender);
        }

        // Hover – attach to sender's name and any mentioned player names
        if (hoverProvider.isEnabled()) {
            Component hoverComp = hoverProvider.createHoverComponent(sender);
            component = hoverProvider.addHoverToNameSection(component, hoverComp, sender.getName());
            component = hoverProvider.addHoverToPlayerNamesInMessage(component, sender);
        }


        // Send to local recipients
        for (Player recipient : recipients) {
            recipient.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);

        // Cross-server sync via RabbitMQ
        if (syncManager != null) {
            try {
                String json = GsonComponentSerializer.gson().serialize(component);
                syncManager.publishChannelMessage(
                        sender.getUniqueId(),
                        safeServerName(),
                        sender.getName(),
                        channel.getId(),
                        json
                );
            } catch (Exception e) {
                Bukkit.getLogger().severe("[ChatChannelHandler] Cross-server sync failed: " + e.getMessage());
            }
        }

        // Discord webhook (channel-specific)
        if (channel.hasDiscordWebhook()) {
            sendDiscord(channel.getDiscordWebhook(), sender, channel, rawMessage);
        } else if (defaultDiscordEnabled && !defaultDiscordWebhookUrl.isEmpty()) {
            sendDiscord(defaultDiscordWebhookUrl, sender, channel, rawMessage);
        }
    }


    private Component buildChannelMessage(Player sender, String rawMessage, ChatChannel channel) {
        // Get the format for this player's LuckPerms group
        String format = channel.getFormatForPlayer(sender);

        format = applyPapi(format, sender);

        format = normalizePlaceholders(format);

        TagResolver resolver = buildResolver(sender, rawMessage);

        try {
            return MM.deserialize(format, resolver);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Chat] Channel format parse error: " + e.getMessage()
                    + " | format: " + format);
            try {
                return MM.deserialize(
                        "<gray><player_name><dark_gray>: </dark_gray><white><chat_message></white>",
                        resolver);
            } catch (Throwable ignored) {
                return Component.text(sender.getName() + ": " + rawMessage);
            }
        }
    }


    private String normalizePlaceholders(String format) {
        if (format == null) return "<player_name><dark_gray>: </dark_gray><chat_message>";

        if (format.contains("§")) {
            format = format.replaceAll("§([0-9a-fk-orA-FK-OR])", "&$1");
        }

        boolean hasPlayerName = format.contains("<player_name>")
                || format.contains("<player_displayname>")
                || format.contains("{player}")
                || format.contains("{displayname}")
                || format.contains("%player_name%")
                || format.contains("%player_displayname%");

        boolean hasBracketMessage  = format.contains("{message}");
        boolean hasPercentMessage  = format.contains("%chat_message%");

        if ((hasBracketMessage || hasPercentMessage) && !hasPlayerName) {

            String replacement = "<lp_prefix><player_name><dark_gray>: </dark_gray><chat_message>";
            if (hasBracketMessage)  format = format.replace("{message}", replacement);
            if (hasPercentMessage)  format = format.replace("%chat_message%", replacement);
        } else {
            format = format.replace("{message}", "<chat_message>");
            format = format.replace("%chat_message%", "<chat_message>");
        }

        return format
                .replace("{player}",      "<player_name>")
                .replace("{displayname}", "<player_displayname>")
                .replace("{prefix}",      "<lp_prefix>")
                .replace("{suffix}",      "<lp_suffix>")
                .replace("%player_name%",        "<player_name>")
                .replace("%player_displayname%", "<player_displayname>");
    }

    private TagResolver buildResolver(Player player, String rawMessage) {
        boolean canColors = player.hasPermission("oreo.chat.colors");
        TagResolver msgResolver = canColors
                ? Placeholder.parsed("chat_message", rawMessage)
                : Placeholder.unparsed("chat_message", rawMessage);

        Component lpPrefix = buildLpPrefix(player);
        Component lpSuffix = buildLpSuffix(player);

        String serverName = safeServerName();
        String world = player.getWorld().getName();
        String x = coord(player.getLocation().getX());
        String y = coord(player.getLocation().getY());
        String z = coord(player.getLocation().getZ());
        String ping = String.valueOf(getPingSafe(player));
        String health = String.valueOf((int) Math.ceil(player.getHealth()));
        String maxHealth = String.valueOf(getMaxHealthSafe(player));
        String level = String.valueOf(player.getLevel());
        String gamemode = player.getGameMode().name();
        String uuid = player.getUniqueId().toString();
        String time24 = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        return TagResolver.resolver(
                msgResolver,
                Placeholder.component("lp_prefix", lpPrefix),
                Placeholder.component("lp_suffix", lpSuffix),
                Placeholder.unparsed("player_name", player.getName()),
                Placeholder.unparsed("player_displayname", player.getDisplayName()),
                Placeholder.unparsed("server_name", serverName),
                Placeholder.unparsed("player_world", world),
                Placeholder.unparsed("player_x", x),
                Placeholder.unparsed("player_y", y),
                Placeholder.unparsed("player_z", z),
                Placeholder.unparsed("player_ping", ping),
                Placeholder.unparsed("player_health", health),
                Placeholder.unparsed("player_max_health", maxHealth),
                Placeholder.unparsed("player_level", level),
                Placeholder.unparsed("player_gamemode", gamemode),
                Placeholder.unparsed("player_uuid", uuid),
                Placeholder.unparsed("time_24h", time24),
                Placeholder.unparsed("date", date)
        );
    }

    // ─── LuckPerms ───────────────────────────────────────────────────────────

    private Component buildLpPrefix(Player p) {
        try {
            CachedMetaData meta = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(p);
            String prefix = meta.getPrefix();
            return legacyOrMM(prefix);
        } catch (Throwable ignored) {
            return Component.empty();
        }
    }

    private Component buildLpSuffix(Player p) {
        try {
            CachedMetaData meta = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(p);
            String suffix = meta.getSuffix();
            return legacyOrMM(suffix);
        } catch (Throwable ignored) {
            return Component.empty();
        }
    }


    private Component legacyOrMM(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("§")) {
            try { return LEGACY_SEC.deserialize(text); } catch (Throwable ignored) {}
        }
        if (text.contains("&")) {
            try { return LEGACY_AMP.deserialize(text); } catch (Throwable ignored) {}
        }
        if (text.contains("<")) {
            try { return MM.deserialize(text); } catch (Throwable ignored) {}
        }
        return Component.text(text);
    }


    private List<Player> getRecipients(Player sender, ChatChannel channel) {
        List<Player> recipients = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChatChannel playerChannel = channelManager.getPlayerChannel(player);
            if (playerChannel == null || !playerChannel.getId().equalsIgnoreCase(channel.getId())) {
                continue;
            }
            if (!channel.canView(player)) continue;

            switch (channel.getScope()) {
                case ALL, SERVER, SHARD -> recipients.add(player);
                case WORLD -> {
                    if (player.getWorld().equals(sender.getWorld())) recipients.add(player);
                }
                case RANGE -> {
                    if (player.getWorld().equals(sender.getWorld())) {
                        Location sLoc = sender.getLocation();
                        if (sLoc.distance(player.getLocation()) <= channel.getRangeBlocks()) {
                            recipients.add(player);
                        }
                    }
                }
            }
        }
        return recipients;
    }


    private void sendDiscord(String webhookUrl, Player sender, ChatChannel channel, String rawMessage) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        try {
            String serverName = safeServerName();
            String channelPlain = plainDisplayName(channel);
            String username = "[" + serverName + "] #" + channelPlain;
            DiscordWebhookSender.sendAsync(webhookUrl, username, rawMessage, sender.getName());
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatChannelHandler] Discord webhook failed: " + e.getMessage());
        }
    }


    private String applyPapi(String input, Player p) {
        if (input == null || input.isEmpty()) return input;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, input);
            }
        } catch (Throwable ignored) {}
        return input;
    }

    private String coord(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    private int getPingSafe(Player player) {
        try { return player.getPing(); } catch (Throwable ignored) { return -1; }
    }

    private int getMaxHealthSafe(Player player) {
        try {

            var attr = player.getAttribute(org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH"));
            if (attr == null) attr = player.getAttribute(org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH"));
            if (attr != null) return (int) Math.ceil(attr.getValue());
        } catch (Throwable ignored) {}
        try { return (int) Math.ceil(player.getMaxHealth()); } catch (Throwable ignored) {}
        return 20;
    }

    private String safeServerName() {
        try { return plugin.getConfigService().serverName(); }
        catch (Throwable t) { return Bukkit.getServer().getName(); }
    }

    private String plainDisplayName(ChatChannel channel) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(MM.deserialize(channel.getDisplayName()));
        } catch (Throwable t) {
            return channel.getDisplayName();
        }
    }

    // Kept for backwards compat – no longer used internally
    @Deprecated
    public void setHoverProvider(Object provider) { /* no-op */ }
}
