package fr.elias.oreoEssentials.modules.chat.channels.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.ChatSyncManager;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelAnnounceCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private final ChatSyncManager syncManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ChannelAnnounceCommand(OreoEssentials plugin, ChatChannelManager channelManager, ChatSyncManager syncManager) {
        this.plugin = plugin;
        this.channelManager = channelManager;
        this.syncManager = syncManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oreo.channel.announce")) {
            sender.sendMessage(mm.deserialize("<red>You don't have permission to use this command!</red>"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Usage: /channelannounce <channel> <message></red>"));
            return true;
        }

        if (!channelManager.isEnabled()) {
            sender.sendMessage(mm.deserialize("<red>Channels are not enabled!</red>"));
            return true;
        }

        String channelId = args[0].toLowerCase();
        ChatChannel channel = channelManager.getChannel(channelId);

        if (channel == null) {
            sender.sendMessage(mm.deserialize("<red>Channel '" + channelId + "' not found!</red>"));
            return true;
        }

        if (!channel.isEnabled()) {
            sender.sendMessage(mm.deserialize("<red>Channel '" + channelId + "' is disabled!</red>"));
            return true;
        }

        StringBuilder msgBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) msgBuilder.append(" ");
            msgBuilder.append(args[i]);
        }
        String rawMessage = msgBuilder.toString();

        String announcementFormat = "<gold><bold>[ANNOUNCEMENT]</bold></gold> <gray>»</gray> " + rawMessage;
        Component announcement;

        try {
            announcement = mm.deserialize(announcementFormat);
        } catch (Exception e) {
            sender.sendMessage(mm.deserialize("<red>Invalid MiniMessage format!</red>"));
            return true;
        }

        int recipientCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ChatChannel playerChannel = channelManager.getPlayerChannel(player);

            if (playerChannel == null || !playerChannel.getId().equalsIgnoreCase(channel.getId())) {
                continue;
            }

            if (!channel.canView(player)) {
                continue;
            }

            if (channel.getScope() == ChatChannel.ChannelScope.WORLD && sender instanceof Player) {
                Player senderPlayer = (Player) sender;
                if (!player.getWorld().equals(senderPlayer.getWorld())) {
                    continue;
                }
            }

            if (channel.getScope() == ChatChannel.ChannelScope.RANGE && sender instanceof Player) {
                Player senderPlayer = (Player) sender;
                if (!player.getWorld().equals(senderPlayer.getWorld()) ||
                        player.getLocation().distance(senderPlayer.getLocation()) > channel.getRangeBlocks()) {
                    continue;
                }
            }

            player.sendMessage(announcement);
            recipientCount++;
        }

        Bukkit.getConsoleSender().sendMessage(announcement);

        if (syncManager != null && (channel.getScope() == ChatChannel.ChannelScope.ALL ||
                channel.getScope() == ChatChannel.ChannelScope.SHARD)) {
            try {
                String serverName = plugin.getConfigService().serverName();
                String jsonComponent = GsonComponentSerializer.gson().serialize(announcement);
                syncManager.publishChannelSystem(serverName, channel.getId(), jsonComponent);
            } catch (Exception e) {
                plugin.getLogger().warning("[ChannelAnnounce] Failed to sync announcement: " + e.getMessage());
            }
        }

        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        sender.sendMessage(mm.deserialize(
                "<green>Announcement sent to </green><yellow>" + channel.getDisplayName() +
                        "</yellow><green> (</green><white>" + recipientCount + "</white><green> recipients)</green>"
        ));

        plugin.getLogger().info("[ChannelAnnounce] " + senderName + " sent announcement to channel " +
                channel.getId() + ": " + rawMessage);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("oreo.channel.announce")) {
            return completions;
        }

        if (args.length == 1) {
            // Suggest channel names
            String partial = args[0].toLowerCase();
            completions = channelManager.getAllChannels().stream()
                    .filter(ChatChannel::isEnabled)
                    .map(ChatChannel::getId)
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length >= 2) {
            completions.addAll(Arrays.asList(
                    "<gold>Your message here</gold>",
                    "<red>Urgent message!</red>",
                    "<gradient:#FF1493:#00FF7F>Gradient text</gradient>",
                    "<bold>Bold text</bold>",
                    "<italic>Italic text</italic>",
                    "Your announcement..."
            ));
        }

        return completions;
    }
}