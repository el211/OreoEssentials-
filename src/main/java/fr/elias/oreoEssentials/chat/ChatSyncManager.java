package fr.elias.oreoEssentials.chat;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class ChatSyncManager {

    private static final String EXCHANGE_NAME = "chat_sync";
    private static final String EX_CHANNEL_MSG = "CHANMSG";
    private static final String EX_CHANNEL_SYS = "CHANSYS";

    private static final UUID SERVER_ID = UUID.randomUUID();

    private final MuteService muteService;
    private final ChatChannelManager channelManager;

    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private boolean enabled;

    public ChatSyncManager(boolean enabled, String rabbitUri) {
        this(enabled, rabbitUri, null, null);
    }

    public ChatSyncManager(boolean enabled, String rabbitUri, MuteService muteService) {
        this(enabled, rabbitUri, muteService, null);
    }

    public ChatSyncManager(boolean enabled, String rabbitUri, MuteService muteService, ChatChannelManager channelManager) {
        this.enabled = enabled;
        this.muteService = muteService;
        this.channelManager = channelManager;

        if (!enabled) return;

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(rabbitUri);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            rabbitConnection = factory.newConnection();
            rabbitChannel = rabbitConnection.createChannel();
            rabbitChannel.exchangeDeclare(EXCHANGE_NAME, "fanout");

            Bukkit.getLogger().info("[ChatSync] Connected to RabbitMQ successfully. Server ID: " + SERVER_ID);
        } catch (Exception e) {
            this.enabled = false;
            Bukkit.getLogger().severe("[ChatSync] Init failed: " + e.getMessage());
            e.printStackTrace();
            safeClose();
        }
    }

    public void publishMessage(UUID playerId, String serverName, String playerName, String message) {
        if (!enabled) return;

        try {
            String payload = SERVER_ID + ";;" + playerId + ";;" + b64(playerName) + ";;" + b64(message);
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
            Bukkit.getLogger().info("[ChatSync] Published message: " + playerName + " -> " + message.substring(0, Math.min(50, message.length())));
        } catch (IOException e) {
            Bukkit.getLogger().warning("[ChatSync] Publish failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Deprecated
    public void publishMessage(String serverName, String playerName, String message) {
        publishMessage(new UUID(0L, 0L), serverName, playerName, message);
    }

    public void broadcastMute(UUID playerId, long untilEpochMillis, String reason, String by) {
        if (!enabled) return;

        try {
            String payload = "CTRL;;MUTE;;" + SERVER_ID
                    + ";;" + playerId
                    + ";;" + untilEpochMillis
                    + ";;" + b64(nullSafe(reason))
                    + ";;" + b64(nullSafe(by));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatSync] MUTE broadcast failed: " + e.getMessage());
        }
    }

    public void broadcastChatControl(String type, String value, String actorName) {
        if (!enabled) return;

        try {
            String payload = "CTRL;;CHAT;;"
                    + nullSafe(type)
                    + ";;" + SERVER_ID
                    + ";;" + b64(nullSafe(value))
                    + ";;" + b64(nullSafe(actorName));

            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatSync] CHAT_CONTROL broadcast failed: " + e.getMessage());
        }
    }

    public void publishChannelMessage(UUID senderId, String server, String senderName, String channelId, String jsonComponent) {
        if (!enabled) return;

        try {
            String payload = EX_CHANNEL_MSG
                    + ";;" + SERVER_ID
                    + ";;" + senderId
                    + ";;" + b64(nullSafe(server))
                    + ";;" + b64(nullSafe(senderName))
                    + ";;" + b64(nullSafe(channelId))
                    + ";;" + b64(nullSafe(jsonComponent));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));

            Bukkit.getLogger().info("[ChatSync] Published channel message: " + senderName + " in channel " + channelId);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatSync] Channel message publish failed: " + e.getMessage());
        }
    }

    public void publishChannelSystem(String server, String channel, String message) {
        if (!enabled) return;

        try {
            String payload = EX_CHANNEL_SYS
                    + ";;" + SERVER_ID
                    + ";;" + b64(nullSafe(server))
                    + ";;" + b64(nullSafe(channel))
                    + ";;" + b64(nullSafe(message));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatSync] Channel system publish failed: " + e.getMessage());
        }
    }

    public void broadcastSystemMessage(String message) {
        if (!enabled) return;
        String serverName;
        try {
            serverName = OreoEssentials.get().getConfigService().serverName();
        } catch (Throwable t) {
            serverName = Bukkit.getServer().getName();
        }
        publishChannelSystem(serverName, "SYSTEM", message);
    }

    public void broadcastUnmute(UUID playerId) {
        if (!enabled) return;

        try {
            String payload = "CTRL;;UNMUTE;;" + SERVER_ID + ";;" + playerId;
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatSync] UNMUTE broadcast failed: " + e.getMessage());
        }
    }

    public void subscribeMessages() {
        if (!enabled) return;

        try {
            String q = rabbitChannel.queueDeclare().getQueue();
            rabbitChannel.queueBind(q, EXCHANGE_NAME, "");

            Bukkit.getLogger().info("[ChatSync] Subscribed to exchange '" + EXCHANGE_NAME + "' with queue: " + q);

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);

                Bukkit.getLogger().info("[ChatSync] Received message: " + msg.substring(0, Math.min(100, msg.length())));

                if (msg.startsWith("CTRL;;")) {
                    handleControlMessage(msg);
                    return;
                }

                if (msg.startsWith(EX_CHANNEL_SYS + ";;")) {
                    handleChannelSystem(msg);
                    return;
                }

                if (msg.startsWith(EX_CHANNEL_MSG + ";;")) {
                    handleChannelMessage(msg);
                    return;
                }

                handleLegacyChat(msg);
            };

            rabbitChannel.basicConsume(q, true, cb, tag -> {});
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ChatSync] Subscribe failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleControlMessage(String msg) {
        String[] parts = msg.split(";;", 8);
        if (parts.length < 2) return;

        String action = parts[1];

        if ("CHAT".equalsIgnoreCase(action)) {
            if (parts.length < 6) return;

            String type = parts[2];
            String originId = parts[3];

            // Check loopback
            if (SERVER_ID.toString().equals(originId)) {
                Bukkit.getLogger().info("[ChatSync] Ignoring own CHAT control message");
                return;
            }

            String valueB64 = parts[4];
            String actorB64 = parts[5];

            String value = fromB64(valueB64);
            String actor = fromB64(actorB64);

            final String fType = type;
            final String fValue = value;
            final String fActor = actor;

            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                var plugin = OreoEssentials.get();
                var svc = plugin.getModGuiService();
                if (svc == null) return;

                switch (fType.toUpperCase()) {
                    case "CLEAR_CHAT" -> {
                        for (int i = 0; i < 200; i++) {
                            Bukkit.broadcastMessage("");
                        }
                        Lang.send(Bukkit.getConsoleSender(), "chat.cleared-by",
                                "<red>Chat has been cleared by <yellow>%actor%</yellow>.</red>",
                                Map.of("actor", fActor));
                        Bukkit.getOnlinePlayers().forEach(player ->
                                Lang.send(player, "chat.cleared-by",
                                        "<red>Chat has been cleared by <yellow>%actor%</yellow>.</red>",
                                        Map.of("actor", fActor)));
                    }
                    case "GLOBAL_MUTE" -> {
                        boolean muted = Boolean.parseBoolean(fValue);
                        svc.setChatMuted(muted);
                        String key = muted ? "chat.global-muted" : "chat.global-unmuted";
                        if (muted) {
                            Bukkit.getOnlinePlayers().forEach(player ->
                                    Lang.send(player, key,
                                            "<red>Global chat has been muted by <yellow>%actor%</yellow>.</red>",
                                            Map.of("actor", fActor)));
                        } else {
                            Bukkit.getOnlinePlayers().forEach(player ->
                                    Lang.send(player, key,
                                            "<green>Global chat has been unmuted.</green>"));
                        }
                    }
                    case "SLOWMODE" -> {
                        int secondsValue;
                        try {
                            secondsValue = Integer.parseInt(fValue);
                        } catch (NumberFormatException ex) {
                            secondsValue = 0;
                        }
                        final int seconds = Math.max(0, secondsValue);
                        svc.setSlowmodeSeconds(seconds);
                        Bukkit.getOnlinePlayers().forEach(player ->
                                Lang.send(player, "chat.slowmode-set",
                                        "<yellow>Slowmode is now <gold>%seconds%</gold>s <gray>(network)</gray>.</yellow>",
                                        Map.of("seconds", String.valueOf(seconds))));
                    }
                }
            });
            return;
        }

        if (parts.length >= 4) {
            String originServer = parts[2];

            if (SERVER_ID.toString().equals(originServer)) {
                Bukkit.getLogger().info("[ChatSync] Ignoring own MUTE/UNMUTE message");
                return;
            }

            String uuidStr = parts[3];

            try {
                UUID target = UUID.fromString(uuidStr);
                if ("UNMUTE".equalsIgnoreCase(action)) {
                    Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                        if (muteService != null) muteService.unmute(target);
                    });
                } else if ("MUTE".equalsIgnoreCase(action) && parts.length >= 7) {
                    long until = Long.parseLong(parts[4]);
                    String reason = fromB64(parts[5]);
                    String by = fromB64(parts[6]);
                    Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                        if (muteService != null) muteService.mute(target, until, reason, by);
                    });
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void handleChannelSystem(String msg) {
        String[] parts = msg.split(";;", 5);
        if (parts.length < 5) return;

        String originServerId = parts[1];

        if (SERVER_ID.toString().equals(originServerId)) {
            Bukkit.getLogger().info("[ChatSync] Ignoring own channel system message");
            return;
        }

        String serverName = fromB64(parts[2]);
        String channelId = fromB64(parts[3]);
        String messageJsonOrText = fromB64(parts[4]);

        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            if (channelManager != null && channelManager.isEnabled()) {
                ChatChannel channel = channelManager.getChannel(channelId);

                if (channel != null && channel.isEnabled()) {
                    if (channel.getScope() == ChatChannel.ChannelScope.SERVER) {
                        String localServer = OreoEssentials.get().getConfigService().serverName();
                        if (!serverName.equalsIgnoreCase(localServer)) return;
                    }

                    Component component;
                    try {
                        component = GsonComponentSerializer.gson().deserialize(messageJsonOrText);
                    } catch (Exception ex) {
                        component = Component.text(messageJsonOrText);
                    }

                    for (var player : Bukkit.getOnlinePlayers()) {
                        ChatChannel playerChannel = channelManager.getPlayerChannel(player);
                        if (playerChannel == null || !playerChannel.getId().equalsIgnoreCase(channel.getId())) continue;
                        if (!channel.canView(player)) continue;
                        player.sendMessage(component);
                    }

                    Bukkit.getConsoleSender().sendMessage(messageJsonOrText);
                    return;
                }
            }

            Bukkit.getConsoleSender().sendMessage(messageJsonOrText);
        });
    }


    private void handleChannelMessage(String msg) {
        Bukkit.getLogger().info("[ChatSync/DEBUG] ========== CHANNEL MESSAGE RECEIVED ==========");
        Bukkit.getLogger().info("[ChatSync/DEBUG] Raw message: " + msg);

        String[] parts = msg.split(";;", 7);
        if (parts.length < 7) {
            Bukkit.getLogger().warning("[ChatSync/DEBUG] Invalid message format! Expected 7 parts, got " + parts.length);
            return;
        }

        String originServerId = parts[1];
        Bukkit.getLogger().info("[ChatSync/DEBUG] Origin server ID: " + originServerId);
        Bukkit.getLogger().info("[ChatSync/DEBUG] My server ID: " + SERVER_ID);

        // Ignore our own messages (already sent locally)
        if (SERVER_ID.toString().equals(originServerId)) {
            Bukkit.getLogger().info("[ChatSync/DEBUG] LOOPBACK - Ignoring own message");
            return;
        }

        String senderUuidStr = parts[2];
        String serverName = fromB64(parts[3]);
        String senderName = fromB64(parts[4]);
        String channelId = fromB64(parts[5]);
        String messageJson = fromB64(parts[6]);

        Bukkit.getLogger().info("[ChatSync/DEBUG] Sender: " + senderName + " (" + senderUuidStr + ")");
        Bukkit.getLogger().info("[ChatSync/DEBUG] Server: " + serverName);
        Bukkit.getLogger().info("[ChatSync/DEBUG] Channel: " + channelId);
        Bukkit.getLogger().info("[ChatSync/DEBUG] Message preview: " + messageJson.substring(0, Math.min(100, messageJson.length())));

        UUID senderUuid = null;
        try {
            senderUuid = UUID.fromString(senderUuidStr);
        } catch (Exception ignored) {
            Bukkit.getLogger().warning("[ChatSync/DEBUG] Invalid sender UUID!");
        }

        // Check if sender is muted
        if (muteService != null && senderUuid != null && muteService.isMuted(senderUuid)) {
            Bukkit.getLogger().info("[ChatSync/DEBUG] BLOCKED - Sender is muted");
            return;
        }

        final UUID finalSenderUuid = senderUuid;

        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            Bukkit.getLogger().info("[ChatSync/DEBUG] Running on main thread...");

            if (channelManager == null) {
                Bukkit.getLogger().warning("[ChatSync/DEBUG] BLOCKED - channelManager is NULL!");
                return;
            }

            if (!channelManager.isEnabled()) {
                Bukkit.getLogger().warning("[ChatSync/DEBUG] BLOCKED - Channels are disabled");
                return;
            }

            Bukkit.getLogger().info("[ChatSync/DEBUG] Channel manager is enabled, processing...");

            ChatChannel channel = channelManager.getChannel(channelId);
            if (channel == null) {
                Bukkit.getLogger().warning("[ChatSync/DEBUG] BLOCKED - Channel '" + channelId + "' not found!");
                return;
            }

            if (!channel.isEnabled()) {
                Bukkit.getLogger().warning("[ChatSync/DEBUG] BLOCKED - Channel '" + channelId + "' is disabled");
                return;
            }

            Bukkit.getLogger().info("[ChatSync/DEBUG] Channel '" + channelId + "' found and enabled!");
            Bukkit.getLogger().info("[ChatSync/DEBUG] Channel scope: " + channel.getScope());

            if (channel.getScope() == ChatChannel.ChannelScope.SERVER) {
                String localServer = OreoEssentials.get().getConfigService().serverName();
                Bukkit.getLogger().info("[ChatSync/DEBUG] SERVER-scoped channel. Local: " + localServer + ", Message from: " + serverName);
                if (!serverName.equalsIgnoreCase(localServer)) {
                    Bukkit.getLogger().info("[ChatSync/DEBUG] BLOCKED - Different server for SERVER-scoped channel");
                    return;
                }
            }

            Component component;
            try {
                component = GsonComponentSerializer.gson().deserialize(messageJson);
                Bukkit.getLogger().info("[ChatSync/DEBUG] Successfully deserialized message component");
            } catch (Exception ex) {
                component = Component.text(messageJson);
                Bukkit.getLogger().warning("[ChatSync/DEBUG] Failed to deserialize, using plain text fallback: " + ex.getMessage());
            }

            int recipientCount = 0;
            int totalOnline = Bukkit.getOnlinePlayers().size();
            Bukkit.getLogger().info("[ChatSync/DEBUG] Checking " + totalOnline + " online players...");

            for (var player : Bukkit.getOnlinePlayers()) {
                ChatChannel playerChannel = channelManager.getPlayerChannel(player);

                Bukkit.getLogger().info("[ChatSync/DEBUG] Player " + player.getName() + " is in channel: "
                        + (playerChannel == null ? "NULL" : playerChannel.getId()));

                if (playerChannel == null || !playerChannel.getId().equalsIgnoreCase(channel.getId())) {
                    Bukkit.getLogger().info("[ChatSync/DEBUG]   -> SKIPPED (wrong channel)");
                    continue;
                }

                if (!channel.canView(player)) {
                    Bukkit.getLogger().info("[ChatSync/DEBUG]   -> SKIPPED (no view permission)");
                    continue;
                }

                player.sendMessage(component);
                recipientCount++;
                Bukkit.getLogger().info("[ChatSync/DEBUG]   -> DELIVERED!");
            }

            Bukkit.getLogger().info("[ChatSync/DEBUG] ========== DELIVERED TO " + recipientCount + " PLAYERS ==========");

            Bukkit.getConsoleSender().sendMessage(component);
        });
    }


    private void handleLegacyChat(String msg) {
        String[] split = msg.split(";;", 4);
        if (split.length != 4) return;

        String originServerId = split[0];

        if (SERVER_ID.toString().equals(originServerId)) {
            Bukkit.getLogger().info("[ChatSync] Ignoring own legacy chat message (loopback prevented)");
            return;
        }

        String senderUuidStr = split[1];
        String nameB64 = split[2];
        String msgB64 = split[3];

        UUID senderUuid = null;
        try {
            senderUuid = UUID.fromString(senderUuidStr);
        } catch (Exception ignored) {
        }

        // Check if sender is muted
        if (muteService != null && senderUuid != null && muteService.isMuted(senderUuid)) {
            Bukkit.getLogger().info("[ChatSync] Blocked muted player message: " + senderUuid);
            return;
        }

        String decodedMessage = fromB64(msgB64);

        Bukkit.getLogger().info("[ChatSync] Processing legacy chat from other server: " + decodedMessage.substring(0, Math.min(50, decodedMessage.length())));

        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            if (channelManager != null && channelManager.isEnabled()) {
                Bukkit.getLogger().info("[ChatSync] Channels enabled, ignoring legacy chat message");
                return;
            }

            try {
                Component component = GsonComponentSerializer.gson().deserialize(decodedMessage);
                Bukkit.getServer().sendMessage(component);
                Bukkit.getLogger().info("[ChatSync] Displayed legacy chat as Component");
            } catch (Exception e) {
                Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(decodedMessage));
                Bukkit.getLogger().info("[ChatSync] Displayed legacy chat as plain text (fallback)");
            }
        });
    }

    public void close() {
        safeClose();
    }

    private void safeClose() {
        try {
            if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close();
        } catch (Exception ignored) {
        }
        try {
            if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close();
        } catch (Exception ignored) {
        }
    }

    private static String b64(String s) {
        if (s == null) s = "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String fromB64(String s) {
        if (s == null) return "";
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s;
    }
}