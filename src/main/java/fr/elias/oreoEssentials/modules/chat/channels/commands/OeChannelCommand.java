package fr.elias.oreoEssentials.modules.chat.channels.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.commands.OreoCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OeChannelCommand implements OreoCommand, TabCompleter {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public OeChannelCommand(OreoEssentials plugin, ChatChannelManager channelManager) {
        this.plugin = plugin;
        this.channelManager = channelManager;
    }

    @Override
    public String name() {
        return "oechannel";
    }

    @Override
    public List<String> aliases() {
        return List.of("channel", "setchannel");
    }

    @Override
    public String permission() {
        return "oreo.chat.channel";
    }

    @Override
    public String usage() {
        return "<channel>";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!channelManager.isEnabled()) {
            player.sendMessage("§cChat channels are currently disabled.");
            return true;
        }

        if (args.length == 0) {
            ChatChannel current = channelManager.getPlayerChannel(player);
            Component msg = MM.deserialize("<gray>Current channel: " + current.getDisplayName());
            player.sendMessage(msg);
            player.sendMessage("§7Use §f/" + label + " <channel> §7to switch channels");
            return true;
        }

        String channelId = args[0].toLowerCase(Locale.ROOT);
        ChatChannel channel = channelManager.getChannel(channelId);

        if (channel == null) {
            player.sendMessage("§cChannel not found: §f" + channelId);
            return true;
        }

        if (!channel.isEnabled()) {
            player.sendMessage("§cThis channel is currently disabled.");
            return true;
        }

        if (!channel.canJoin(player)) {
            player.sendMessage("§cYou don't have permission to join this channel.");
            return true;
        }

        channelManager.setPlayerChannel(player, channel);
        Component msg = MM.deserialize("<green>You are now chatting in " + channel.getDisplayName() + "</green>");
        player.sendMessage(msg);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (!channelManager.isEnabled()) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return channelManager.getAllChannels().stream()
                    .filter(ch -> ch.isEnabled())
                    .filter(ch -> ch.canJoin(player))
                    .map(ChatChannel::getId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}