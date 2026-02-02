package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelHandler;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modules.chat.chatservices.MuteService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class AsyncChatListenerWithChannels implements Listener {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private final ChatChannelHandler channelHandler;
    private final MuteService muteService;

    private final boolean hoverEnabled;
    private final List<String> hoverLines;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public AsyncChatListenerWithChannels(
            OreoEssentials plugin,
            FormatManager formatManager,
            CustomConfig chatConfig,
            ChatSyncManager syncManager,
            boolean discordEnabled,
            String discordWebhookUrl,
            MuteService muteService,
            ChatChannelManager channelManager
    ) {
        this.plugin = plugin;
        this.channelManager = channelManager;
        this.muteService = muteService;
        this.channelHandler = new ChatChannelHandler(plugin, channelManager, syncManager, formatManager);

        // IMPORTANT: Pass this listener to the handler so it can use hover methods
        this.channelHandler.setHoverProvider(this);

        final FileConfiguration conf = chatConfig.getCustomConfig();
        this.hoverEnabled = conf.getBoolean("chat.hover.enabled", true);
        this.hoverLines = conf.getStringList("chat.hover.lines");
        if (hoverLines.isEmpty()) {
            hoverLines.add("<gold>Player: <white>%player_name%</white></gold>");
            hoverLines.add("<gray>Health: <white>%player_health%/%player_max_health%</white></gray>");
        }

        Bukkit.getLogger().info("[AsyncChatListenerWithChannels] Initialized with hover support: "
                + (hoverEnabled ? "ENABLED" : "DISABLED") + " (" + hoverLines.size() + " lines)");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        if (!plugin.getSettingsConfig().chatEnabled()) return;

        final Player player = event.getPlayer();
        final ModGuiService gui = plugin.getModGuiService();

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

        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            player.sendMessage("§cYou are muted and cannot send messages.");
            event.setCancelled(true);
            return;
        }

        if (gui != null && gui.isStaffChatEnabled(player.getUniqueId())) {
            event.setCancelled(true);
            final String staffMsg = event.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("oreo.staffchat")) {
                        p.sendMessage("§b[StaffChat] §f" + player.getName() + ": §7" + staffMsg);
                    }
                }
            });
            return;
        }

        if (channelManager != null && channelManager.isEnabled()) {
            event.setCancelled(true);
            final String message = event.getMessage();
            final ChatChannel channel = channelManager.getPlayerChannel(player);

            Bukkit.getScheduler().runTask(plugin, () -> {
                channelHandler.sendChannelMessage(player, message, channel);
            });
        }
    }


    public boolean isHoverEnabled() {
        return hoverEnabled;
    }

    public Component createHoverComponent(Player player) {
        StringBuilder hoverText = new StringBuilder();

        for (int i = 0; i < hoverLines.size(); i++) {
            String line = hoverLines.get(i);
            line = replaceHoverPlaceholders(line, player);
            hoverText.append(line);
            if (i < hoverLines.size() - 1) {
                hoverText.append("\n");
            }
        }

        try {
            return MM.deserialize(hoverText.toString(), createHoverPlaceholders(player));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Chat] Failed to parse hover text: " + e.getMessage());
            return Component.text(hoverText.toString());
        }
    }

    public Component addHoverToNameSection(Component message, Component hoverComponent, String playerName) {
        List<Component> children = new ArrayList<>(message.children());
        boolean modified = false;

        for (int i = 0; i < children.size(); i++) {
            Component child = children.get(i);
            String childText = PlainTextComponentSerializer.plainText().serialize(child);

            if (childText.contains(playerName) ||
                    (childText.length() > 3 && playerName.contains(childText))) {
                Component withHover = addHoverRecursive(child, hoverComponent);
                children.set(i, withHover);
                modified = true;
                break;
            }
        }

        if (modified) {
            return message.children(children);
        }

        return message;
    }


    private Component addHoverRecursive(Component component, Component hoverComponent) {
        Component withHover = component.hoverEvent(HoverEvent.showText(hoverComponent));

        if (!component.children().isEmpty()) {
            List<Component> newChildren = new ArrayList<>();
            for (Component child : component.children()) {
                newChildren.add(addHoverRecursive(child, hoverComponent));
            }
            withHover = withHover.children(newChildren);
        }

        return withHover;
    }

    private String replaceHoverPlaceholders(String text, Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }

        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_uuid%", player.getUniqueId().toString());
        text = text.replace("%player_health%", String.valueOf((int) Math.ceil(player.getHealth())));
        text = text.replace("%player_max_health%", String.valueOf((int) Math.ceil(player.getMaxHealth())));
        text = text.replace("%player_level%", String.valueOf(player.getLevel()));
        text = text.replace("%player_ping%", String.valueOf(getPingSafe(player)));
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%player_x%", String.format(Locale.ENGLISH, "%.1f", player.getLocation().getX()));
        text = text.replace("%player_y%", String.format(Locale.ENGLISH, "%.1f", player.getLocation().getY()));
        text = text.replace("%player_z%", String.format(Locale.ENGLISH, "%.1f", player.getLocation().getZ()));
        text = text.replace("%player_gamemode%", player.getGameMode().name());
        text = text.replace("%luckperms_primary_group%", resolvePrimaryGroup(player));

        return text;
    }

    private TagResolver createHoverPlaceholders(Player player) {
        return TagResolver.resolver(
                Placeholder.unparsed("player_name", player.getName()),
                Placeholder.unparsed("player_displayname", player.getDisplayName()),
                Placeholder.unparsed("player_uuid", player.getUniqueId().toString()),
                Placeholder.unparsed("player_health", String.valueOf((int) Math.ceil(player.getHealth()))),
                Placeholder.unparsed("player_max_health", String.valueOf((int) Math.ceil(player.getMaxHealth()))),
                Placeholder.unparsed("player_level", String.valueOf(player.getLevel())),
                Placeholder.unparsed("player_ping", String.valueOf(getPingSafe(player))),
                Placeholder.unparsed("player_world", player.getWorld().getName()),
                Placeholder.unparsed("player_gamemode", player.getGameMode().name()),
                Placeholder.unparsed("luckperms_primary_group", resolvePrimaryGroup(player))
        );
    }

    private int getPingSafe(Player p) {
        try { return p.getPing(); }
        catch (Throwable ignored) { return -1; }
    }

    private String resolvePrimaryGroup(Player p) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User u = lp.getPlayerAdapter(Player.class).getUser(p);
            if (u == null) return "default";
            String primary = u.getPrimaryGroup();
            return primary != null ? primary : "default";
        } catch (Throwable ignored) {
            return "default";
        }
    }
}