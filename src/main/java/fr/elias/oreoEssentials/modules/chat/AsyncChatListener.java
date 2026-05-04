package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modules.chat.chatservices.MuteService;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;


@SuppressWarnings("UnstableApiUsage")
public final class AsyncChatListener implements Listener {

    private final FormatManager formatManager;
    private final CustomConfig chatCfg;
    private final ChatSyncManager sync;
    private final MuteService muteService;
    private final ChatHoverProvider hover;
    private final ChatItemHandler chatItemHandler;

    private final boolean discordEnabled;
    private final String discordWebhookUrl;

    private final boolean papiApplyToMessage;
    private final boolean bannedWordsEnabled;
    private final List<String> bannedWords;

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
        this.papiApplyToMessage = conf.getBoolean("chat.papi.apply-to-message", false);

        this.hover = new ChatHoverProvider(
                conf.getBoolean("chat.hover.enabled", true),
                conf.getStringList("chat.hover.lines")
        );
        this.chatItemHandler = new ChatItemHandler(chatCfg);

        var settings = OreoEssentials.get().getSettingsConfig();
        this.bannedWordsEnabled = settings.bannedWordsEnabled();
        this.bannedWords = settings.bannedWords();

        Bukkit.getLogger().info("[Chat] AsyncChatListener (non-channel, Paper AsyncChatEvent) initialized.");
    }


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
        if (!OreoEssentials.get().getSettingsConfig().chatEnabled()) return;
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    /**
     * Handles AH/Orders chat input at LOW priority with ignoreCancelled=false so it always fires,
     * even when OreoFactions has already cancelled the event (e.g. player is in faction chat mode).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onChatInput(AsyncChatEvent event) {
        try {
            var chMgr = OreoEssentials.get().getChannelManager();
            if (chMgr != null && chMgr.isEnabled()) return; // handled by AsyncChatListenerWithChannels
        } catch (Throwable ignored) {}

        final Player player = event.getPlayer();
        try {
            AuctionHouseModule ahm = AuctionHouseModule.getInstance();
            if (ahm != null && ahm.isWaitingForPrice(player.getUniqueId())) {
                event.viewers().clear();
                event.setCancelled(true);
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                ahm.consumePriceInput(player, raw);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            fr.elias.oreoEssentials.modules.orders.OrdersModule om =
                    fr.elias.oreoEssentials.modules.orders.OrdersModule.getInstance();
            if (om != null) {
                String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
                UUID uid = player.getUniqueId();
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForQty(uid)) {
                    event.viewers().clear();
                    event.setCancelled(true);
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumeQtyInput(om, player, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.isWaitingForPrice(uid)) {
                    event.viewers().clear();
                    event.setCancelled(true);
                    fr.elias.oreoEssentials.modules.orders.gui.CreateOrderFlow.consumePriceInput(om, player, raw);
                    return;
                }
                if (fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.isWaitingForFillQty(uid)) {
                    event.viewers().clear();
                    event.setCancelled(true);
                    fr.elias.oreoEssentials.modules.orders.gui.FillOrderMenu.consumeFillQtyInput(om, player, raw);
                    return;
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().severe("[Orders] Chat input handler error: " + t);
            t.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        try {
            var chMgr = OreoEssentials.get().getChannelManager();
            if (chMgr != null && chMgr.isEnabled()) return;
        } catch (Throwable t) {
            Bukkit.getLogger().severe("[Chat] AsyncChatListener: failed to check ChannelManager — aborting. " + t.getMessage());
            return;
        }

        // Chat module disabled — leave the event alone so CHC (or other chat plugins) can handle it
        if (!OreoEssentials.get().getSettingsConfig().chatEnabled()) return;

        // Modern Factions signals a handled private channel by cancelling the Paper event
        // and clearing all viewers. If the event is cancelled but viewers still exist,
        // this is Paper's legacy-chat bridge pre-cancel and global chat should still run.
        if (event.isCancelled() && event.viewers().isEmpty()) return;

        event.viewers().clear();
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final ModGuiService gui = OreoEssentials.get().getModGuiService();

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
        if (gui != null && gui.isStaffChatEnabled(player.getUniqueId())) {
            final String staffMsg = PlainTextComponentSerializer.plainText().serialize(event.message());
            OreScheduler.run(OreoEssentials.get(), () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("oreo.staffchat")) {
                        p.sendMessage("§b[StaffChat] §f" + player.getName() + ": §7" + staffMsg);
                    }
                }
            });
            return;
        }
        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            return;
        }

        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (msg.isEmpty()) return;
        if (bannedWordsEnabled) msg = censor(msg, bannedWords);

        final UUID senderUuid = player.getUniqueId();
        final String rawMsg = msg;
        final String serverName = OreoEssentials.get().getConfigService().serverName();

        OreScheduler.run(OreoEssentials.get(), () -> {
            Player live = Bukkit.getPlayer(senderUuid);
            if (live == null) return;

            String fmt = formatManager.formatMessage(live, rawMsg);

            String msgForResolver = rawMsg;
            if (papiApplyToMessage) {
                msgForResolver = applyPapi(msgForResolver, live);
            }

            boolean canColors = live.hasPermission("oreo.chat.colors");
            String msgForParsing = canColors ? FormatManager.convertLegacyToMiniMessage(msgForResolver) : msgForResolver;
            TagResolver msgResolver = canColors
                    ? Placeholder.parsed("chat_message", msgForParsing)
                    : Placeholder.unparsed("chat_message", msgForResolver);

            Component lpPrefixComp = buildLpPrefixComponent(live);

            try {
                TagResolver resolver = TagResolver.resolver(
                        msgResolver,
                        Placeholder.component("lp_prefix", lpPrefixComp),
                        Placeholder.unparsed("player_name", live.getName()),
                        Placeholder.unparsed("player_displayname", live.getDisplayName()),
                        playerPlaceholders(live)
                );

                Component out = MM.deserialize(fmt, resolver);

                if (chatItemHandler.containsItemPlaceholder(rawMsg)) {
                    out = chatItemHandler.processItemPlaceholder(out, live);
                }

                if (hover.isEnabled()) {
                    Component hoverComp = hover.createHoverComponent(live);
                    String displayName = live.getName();
                    out = hover.addHoverToNameSection(out, hoverComp, displayName);
                    out = hover.addHoverToPlayerNamesInMessage(out, live);
                }

                broadcastAndSync(live, out, senderUuid, serverName, rawMsg);

            } catch (Throwable t) {
                Bukkit.getLogger().severe("[Chat] MiniMessage parse error for format: " + fmt);
                t.printStackTrace();
                Component fallback = Component.text(live.getName() + " » " + rawMsg);
                broadcastAndSync(live, fallback, senderUuid, serverName, rawMsg);
            }
        });
    }
    private void broadcastAndSync(Player sender, Component component, UUID senderUuid,
                                  String serverName, String rawMsg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);

        // Resolve the plain-text prefix so the Discord bot can display it cleanly
        // without needing to parse the full Adventure JSON component.
        String plainPrefix = "";
        try {
            Component prefixComp = buildLpPrefixComponent(sender);
            plainPrefix = PlainTextComponentSerializer.plainText().serialize(prefixComp);
        } catch (Throwable ignored) {}

        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        maybeDiscord(sender.getName(), plain);

        String json = GsonComponentSerializer.gson().serialize(component);
        maybeSync(senderUuid, serverName, sender.getName(), json, plainPrefix, rawMsg);
    }

    // ─── LuckPerms ───────────────────────────────────────────────────────────

    private Component buildLpPrefixComponent(Player p) {
        try {
            CachedMetaData meta = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(p);
            String prefix = meta.getPrefix();
            if (prefix == null || prefix.isEmpty()) return Component.empty();
            // Resolve PAPI placeholders inside the LuckPerms prefix (e.g. %nexo_...%)
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                prefix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, prefix);
            }
            return deserializeLegacyOrMM(prefix);
        } catch (Throwable ignored) {
            return Component.empty();
        }
    }

    private Component deserializeLegacyOrMM(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("§")) {
            try { return LegacyComponentSerializer.legacySection().deserialize(text); } catch (Throwable ignored) {}
        }
        try { return LegacyComponentSerializer.legacyAmpersand().deserialize(text); } catch (Throwable ignored) {}
        return Component.text(text);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String applyPapi(String input, Player p) {
        if (input == null || input.isEmpty()) return input;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, input);
            }
        } catch (Throwable ignored) {}
        return input;
    }

    private String censor(String msg, List<String> words) {
        if (msg == null || words == null || words.isEmpty()) return msg;
        String out = msg;
        for (String w : words) {
            if (w == null || w.isBlank()) continue;
            out = out.replaceAll("(?i)" + Pattern.quote(w), "*".repeat(w.length()));
        }
        return out;
    }

    private void maybeSync(UUID uuid, String server, String name, String jsonComponent,
                           String plainPrefix, String rawMessage) {
        try {
            if (sync != null) sync.publishMessage(uuid, server, name, jsonComponent, plainPrefix, rawMessage);
        } catch (Throwable ex) {
            Bukkit.getLogger().severe("[ChatSync] Publish failed: " + ex.getMessage());
        }
    }

    private void maybeDiscord(String username, String content) {
        if (!discordEnabled || discordWebhookUrl.isEmpty()) return;
        try {
            new DiscordWebhook(OreoEssentials.get(), discordWebhookUrl).sendAsync(username, content);
        } catch (Throwable ex) {
            Bukkit.getLogger().warning("[Discord] Send failed: " + ex.getMessage());
        }
    }

    private TagResolver playerPlaceholders(Player p) {
        World w = p.getWorld();
        String world = (w != null ? w.getName() : "world");
        String x = fmtCoord(p.getLocation().getX());
        String y = fmtCoord(p.getLocation().getY());
        String z = fmtCoord(p.getLocation().getZ());
        String ping = String.valueOf(getPingSafe(p));
        String hp = String.valueOf((int) Math.ceil(p.getHealth()));
        String maxHp = String.valueOf(getMaxHealthSafe(p));
        String level = String.valueOf(p.getLevel());
        String gm = p.getGameMode() != null ? p.getGameMode().name() : "SURVIVAL";
        String uuid = p.getUniqueId().toString();
        String lpGroup = hoverPrimaryGroup(p);
        String server = OreoEssentials.get().getConfigService().serverName();
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
                Placeholder.unparsed("lp_primary_group", lpGroup),
                Placeholder.unparsed("server_name", server),
                Placeholder.unparsed("time_24h", time24),
                Placeholder.unparsed("date", date)
        );
    }

    private String fmtCoord(double v) {
        return String.format(Locale.ENGLISH, "%.1f", v);
    }

    private int getPingSafe(Player p) {
        try { return p.getPing(); } catch (Throwable ignored) { return -1; }
    }

    private int getMaxHealthSafe(Player p) {
        try {
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH"));
            if (attr == null) attr = p.getAttribute(org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH"));
            if (attr != null) return (int) Math.ceil(attr.getValue());
        } catch (Throwable ignored) {}
        try { return (int) Math.ceil(p.getMaxHealth()); } catch (Throwable ignored) {}
        return 20;
    }

    private String hoverPrimaryGroup(Player p) {
        try {
            var lp = LuckPermsProvider.get();
            var user = lp.getPlayerAdapter(Player.class).getUser(p);
            if (user == null) return "default";
            String primary = user.getPrimaryGroup();
            return primary != null ? primary : "default";
        } catch (Throwable ignored) {
            return "default";
        }
    }
}
