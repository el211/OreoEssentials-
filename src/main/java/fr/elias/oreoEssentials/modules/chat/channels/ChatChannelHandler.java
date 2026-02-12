package fr.elias.oreoEssentials.modules.chat.channels;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.ChatSyncManager;
import fr.elias.oreoEssentials.modules.chat.FormatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Handles channel message routing, formatting, and delivery with proper channel isolation
 */
public class ChatChannelHandler {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private final ChatSyncManager syncManager;
    private final FormatManager formatManager;

    private Object hoverProvider;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ChatChannelHandler(
            OreoEssentials plugin,
            ChatChannelManager channelManager,
            ChatSyncManager syncManager,
            FormatManager formatManager
    ) {
        this.plugin = plugin;
        this.channelManager = channelManager;
        this.syncManager = syncManager;
        this.formatManager = formatManager;
    }

    /**
     * Set the hover provider (AsyncChatListenerWithChannels instance)
     */
    public void setHoverProvider(Object provider) {
        this.hoverProvider = provider;
    }

    /**
     * Process and send a channel message with proper channel isolation AND hover support
     */
    public void sendChannelMessage(Player sender, String message, ChatChannel channel) {
        if (channel == null || !channel.isEnabled()) {
            sender.sendMessage("§cYour current channel is disabled. Switching to default channel.");
            channel = channelManager.getDefaultChannel();
            channelManager.setPlayerChannel(sender, channel);
        }

        if (!channel.canTalk(sender)) {
            sender.sendMessage("§cYou don't have permission to talk in this channel.");
            return;
        }

        List<Player> recipients = getRecipients(sender, channel);

        if (recipients.isEmpty()) {
            sender.sendMessage("§cNo one can hear you in this channel.");
            return;
        }

        String formattedMessage = formatManager.formatMessage(sender, message);

        if (channelManager.isGlobalFormattingEnabled() && channel.isFormattingEnabled()) {
            String channelTag = getChannelTagForPlayer(channel, sender);
            if (!channelTag.isEmpty()) {
                formattedMessage = channelTag + " " + formattedMessage;
            }
        }

        TagResolver resolver = createComprehensiveResolver(sender);

        Component component;
        try {
            component = MM.deserialize(formattedMessage, resolver);
            component = addHoverIfEnabled(component, sender);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatChannelHandler] Failed to parse message: " + e.getMessage());
            component = Component.text(formattedMessage);
        }

        for (Player recipient : recipients) {
            recipient.sendMessage(component);
        }

        Bukkit.getConsoleSender().sendMessage(component);

        if (syncManager != null) {
            try {
                String jsonComponent = GsonComponentSerializer.gson().serialize(component);
                syncManager.publishChannelMessage(
                        sender.getUniqueId(),
                        plugin.getConfigService().serverName(),
                        sender.getName(),
                        channel.getId(),
                        jsonComponent
                );
            } catch (Exception e) {
                Bukkit.getLogger().severe("[ChatChannelHandler] Failed to publish cross-server message: " + e.getMessage());
            }
        }

        if (channel.hasDiscordWebhook()) {
            try {
                String serverName = plugin.getConfigService().serverName();
                String channelDisplayName = channel.getDisplayName();
                String strippedMessage = DiscordWebhookSender.stripFormatting(message);

                DiscordWebhookSender.sendAsync(
                        channel.getDiscordWebhook(),
                        "[" + serverName + "] " + channelDisplayName,
                        strippedMessage,
                        sender.getName()
                );
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ChatChannelHandler] Failed to send Discord webhook: " + e.getMessage());
            }
        }
    }

    private TagResolver createComprehensiveResolver(Player player) {
        String serverName = plugin.getConfigService().serverName();
        String world = player.getWorld().getName();
        String x = formatCoord(player.getLocation().getX());
        String y = formatCoord(player.getLocation().getY());
        String z = formatCoord(player.getLocation().getZ());
        String ping = String.valueOf(getPingSafe(player));
        String health = String.valueOf((int) Math.ceil(player.getHealth()));
        String maxHealth = String.valueOf((int) Math.ceil(player.getMaxHealth()));
        String level = String.valueOf(player.getLevel());
        String gamemode = player.getGameMode().name();
        String uuid = player.getUniqueId().toString();
        String time24 = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        return TagResolver.resolver(
                Placeholder.unparsed("server_name", serverName),
                Placeholder.unparsed("player_uuid", uuid),
                Placeholder.unparsed("player_name", player.getName()),
                Placeholder.unparsed("player_displayname", player.getDisplayName()),
                Placeholder.unparsed("player_world", world),
                Placeholder.unparsed("player_x", x),
                Placeholder.unparsed("player_y", y),
                Placeholder.unparsed("player_z", z),
                Placeholder.unparsed("player_ping", ping),
                Placeholder.unparsed("player_health", health),
                Placeholder.unparsed("player_max_health", maxHealth),
                Placeholder.unparsed("player_level", level),
                Placeholder.unparsed("player_gamemode", gamemode),
                Placeholder.unparsed("time_24h", time24),
                Placeholder.unparsed("date", date)
        );
    }

    private String getChannelTagForPlayer(ChatChannel channel, Player player) {
        String format = channel.getFormatForPlayer(player);
        if (format.equals("{message}")) return "";
        int messageIdx = format.indexOf("{message}");
        if (messageIdx > 0) return format.substring(0, messageIdx).trim();
        return "";
    }

    private List<Player> getRecipients(Player sender, ChatChannel channel) {
        List<Player> recipients = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            ChatChannel playerChannel = channelManager.getPlayerChannel(player);
            if (playerChannel == null || !playerChannel.getId().equalsIgnoreCase(channel.getId())) {
                continue;
            }

            if (!channel.canView(player)) {
                continue;
            }

            switch (channel.getScope()) {
                case ALL, SERVER, SHARD -> recipients.add(player);
                case WORLD -> {
                    if (player.getWorld().equals(sender.getWorld())) {
                        recipients.add(player);
                    }
                }
                case RANGE -> {
                    if (player.getWorld().equals(sender.getWorld())) {
                        Location sLoc = sender.getLocation();
                        Location pLoc = player.getLocation();
                        if (sLoc.distance(pLoc) <= channel.getRangeBlocks()) {
                            recipients.add(player);
                        }
                    }
                }
            }
        }

        return recipients;
    }

    private String formatCoord(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    private int getPingSafe(Player player) {
        try {
            return player.getPing();
        } catch (Throwable t) {
            return -1;
        }
    }

    private Component addHoverIfEnabled(Component component, Player sender) {
        try {
            if (hoverProvider instanceof fr.elias.oreoEssentials.modules.chat.AsyncChatListenerWithChannels listener) {
                if (listener.isHoverEnabled()) {
                    Component hoverComponent = listener.createHoverComponent(sender);
                    component = listener.addHoverToNameSection(component, hoverComponent, sender.getName());
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[ChatChannelHandler] Failed to add hover: " + t.getMessage());
        }
        return component;
    }
}