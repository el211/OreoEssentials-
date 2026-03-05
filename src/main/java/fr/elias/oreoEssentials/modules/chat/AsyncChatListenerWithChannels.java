package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelHandler;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modules.chat.chatservices.MuteService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;


@SuppressWarnings("UnstableApiUsage")
public class AsyncChatListenerWithChannels implements Listener {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;
    private final ChatChannelHandler channelHandler;
    private final MuteService muteService;

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
        this.channelHandler = new ChatChannelHandler(
                plugin, channelManager, syncManager, formatManager, chatConfig,
                discordEnabled, discordWebhookUrl
        );

        Bukkit.getLogger().info("[Chat] AsyncChatListenerWithChannels initialized (Paper AsyncChatEvent)");
    }


    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        event.viewers().clear();
        event.setCancelled(true);

        // AH price input: player is typing price after selecting currency in picker
        final Player player0 = event.getPlayer();
        try {
            AuctionHouseModule ahm = AuctionHouseModule.getInstance();
            if (ahm != null && ahm.isWaitingForPrice(player0.getUniqueId())) {
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                ahm.consumePriceInput(player0, raw);
                return;
            }
        } catch (Throwable ignored) {}

        // Orders/Market chat inputs (qty, price, fill-qty)
        try {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om =
                    fr.elias.oreoEssentials.modules.orders.OrdersModule.getInstance();
            if (om != null) {
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                java.util.UUID uid = player0.getUniqueId();
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForQty(uid)) {
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumeQtyInput(om, player0, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForPrice(uid)) {
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumePriceInput(om, player0, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.isWaitingForFillQty(uid)) {
                    fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.consumeFillQtyInput(om, player0, raw);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        if (!plugin.getSettingsConfig().chatEnabled()) return;

        final Player player = event.getPlayer();
        final ModGuiService gui = plugin.getModGuiService();

        if (gui != null && gui.chatMuted()) {
            player.sendMessage("§cChat is currently muted.");
            return;
        }

        if (gui != null && gui.getSlowmodeSeconds() > 0) {
            if (!gui.canSendMessage(player.getUniqueId())) {
                long left = gui.getRemainingSlowmode(player.getUniqueId());
                player.sendMessage("§cYou must wait §e" + left + "s §cbefore chatting again.");
                return;
            }
            gui.recordMessage(player.getUniqueId());
        }

        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            player.sendMessage("§cYou are muted and cannot send messages.");
            return;
        }

        if (gui != null && gui.isStaffChatEnabled(player.getUniqueId())) {
            final String staffMsg = PlainTextComponentSerializer.plainText().serialize(event.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("oreo.staffchat")) {
                        p.sendMessage("§b[StaffChat] §f" + player.getName() + ": §7" + staffMsg);
                    }
                }
            });
            return;
        }

        var notesListener = plugin.getNotesChat();
        if (notesListener != null && notesListener.isWaitingForNote(player.getUniqueId())) return;

        if (channelManager != null && channelManager.isEnabled()) {
            final String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            final ChatChannel channel = channelManager.getPlayerChannel(player);
            Bukkit.getScheduler().runTask(plugin, () ->
                    channelHandler.sendChannelMessage(player, message, channel));
        }
    }
}
