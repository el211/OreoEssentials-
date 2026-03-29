package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.util.OreScheduler;
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


    /**
     * Intercepts orders/AH chat input at LOW priority — BEFORE OreoFactions' HIGHEST handler.
     * If the player is waiting for chat input (qty, price, etc.), we cancel the event here
     * so OreoFactions' ignoreCancelled=true handler never sees the message.
     * For normal messages we do nothing, letting OreoFactions handle them as usual.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onLegacyChatInput(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String raw = event.getMessage();

        try {
            AuctionHouseModule ahm = AuctionHouseModule.getInstance();
            if (ahm != null && ahm.isWaitingForPrice(player.getUniqueId())) {
                event.setCancelled(true);
                event.getRecipients().clear();
                ahm.consumePriceInput(player, raw);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om =
                    fr.elias.oreoEssentials.modules.orders.OrdersModule.getInstance();
            if (om != null) {
                java.util.UUID uid = player.getUniqueId();
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForQty(uid)) {
                    event.setCancelled(true);
                    event.getRecipients().clear();
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumeQtyInput(om, player, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForPrice(uid)) {
                    event.setCancelled(true);
                    event.getRecipients().clear();
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumePriceInput(om, player, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.isWaitingForFillQty(uid)) {
                    event.setCancelled(true);
                    event.getRecipients().clear();
                    fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.consumeFillQtyInput(om, player, raw);
                    return;
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().severe("[Orders] Legacy chat input error: " + t);
            t.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!plugin.getSettingsConfig().chatEnabled()) return;
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        // AH/Orders GUI input handlers — run regardless of chat.enabled so in-game GUIs keep working
        final Player player0 = event.getPlayer();
        try {
            AuctionHouseModule ahm = AuctionHouseModule.getInstance();
            if (ahm != null && ahm.isWaitingForPrice(player0.getUniqueId())) {
                event.viewers().clear();
                event.setCancelled(true);
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                ahm.consumePriceInput(player0, raw);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om =
                    fr.elias.oreoEssentials.modules.orders.OrdersModule.getInstance();
            if (om != null) {
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                java.util.UUID uid = player0.getUniqueId();
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForQty(uid)) {
                    event.viewers().clear();
                    event.setCancelled(true);
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumeQtyInput(om, player0, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForPrice(uid)) {
                    event.viewers().clear();
                    event.setCancelled(true);
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumePriceInput(om, player0, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.isWaitingForFillQty(uid)) {
                    event.viewers().clear();
                    event.setCancelled(true);
                    fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.consumeFillQtyInput(om, player0, raw);
                    return;
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().severe("[Orders] Chat input handler error: " + t);
            t.printStackTrace();
        }

        // Chat module disabled — leave the event alone so CHC (or other chat plugins) can handle it
        if (!plugin.getSettingsConfig().chatEnabled()) return;

        event.viewers().clear();
        event.setCancelled(true);

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
            OreScheduler.run(plugin, () -> {
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
            OreScheduler.run(plugin, () ->
                    channelHandler.sendChannelMessage(player, message, channel));
        }
    }
}
