// File: src/main/java/fr/elias/oreoEssentials/util/ChatSyncManager.java
package fr.elias.oreoEssentials.util;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class ChatSyncManager {

    private static final String EXCHANGE_NAME = "chat_sync";
    private static final String EX_CHANNEL_MSG = "CHANMSG";
    private static final String EX_CHANNEL_SYS = "CHANSYS";

    private static final UUID SERVER_ID = UUID.randomUUID();

    private final MuteService muteService; // may be null
    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private boolean enabled;

    /* ----------------------------- Ctors ----------------------------- */

    /** Back-compat constructor (no mute checks on receive). */
    public ChatSyncManager(boolean enabled, String rabbitUri) {
        this(enabled, rabbitUri, null);
    }

    /** Preferred constructor with local mute checks on receive. */
    public ChatSyncManager(boolean enabled, String rabbitUri, MuteService muteService) {
        this.enabled = enabled;
        this.muteService = muteService;

        if (!enabled) return;

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(rabbitUri);
            // optional: automatic recovery helps if broker restarts
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            rabbitConnection = factory.newConnection();
            rabbitChannel = rabbitConnection.createChannel();
            rabbitChannel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        } catch (Exception e) {
            this.enabled = false;
            Bukkit.getLogger().severe("[OreoEssentials] ChatSync init failed: " + e.getMessage());
            safeClose();
        }
    }

    /* ----------------------------- Chat publish ----------------------------- */

    /**
     * Publish a chat message. Includes sender UUID so remote servers can apply their mute rules.
     * Payload: serverUUID;;playerUUID;;base64(name);;base64(message)
     */
    public void publishMessage(UUID playerId, String serverName, String playerName, String message) {
        if (!enabled) return;

        try {
            String payload = SERVER_ID + ";;" + playerId + ";;" + b64(playerName) + ";;" + b64(message);
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync publish failed: " + e.getMessage());
        }
    }

    /** Legacy publish (without UUID) – kept to avoid breaking old callers; discouraged. */
    @Deprecated
    public void publishMessage(String serverName, String playerName, String message) {
        publishMessage(new UUID(0L, 0L), serverName, playerName, message);
    }

    /* ----------------------------- Control broadcast ----------------------------- */

    /** Broadcast a mute to all servers. */
    public void broadcastMute(UUID playerId, long untilEpochMillis, String reason, String by) {
        if (!enabled) return;

        try {
            // CTRL;;MUTE;;serverId;;playerUUID;;untilMillis;;b64(reason);;b64(by)
            String payload = "CTRL;;MUTE;;" + SERVER_ID
                    + ";;" + playerId
                    + ";;" + untilEpochMillis
                    + ";;" + b64(nullSafe(reason))
                    + ";;" + b64(nullSafe(by));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync MUTE broadcast failed: " + e.getMessage());
        }
    }

    /** Broadcast chat control (global mute, slowmode, clear chat…) */
    public void broadcastChatControl(String type, String value, String actorName) {
        if (!enabled) return;

        try {
            // CTRL;;CHAT;;type;;serverId;;b64(value);;b64(actor)
            String payload = "CTRL;;CHAT;;"
                    + nullSafe(type)
                    + ";;" + SERVER_ID
                    + ";;" + b64(nullSafe(value))
                    + ";;" + b64(nullSafe(actorName));

            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null,
                    payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync CHAT_CONTROL broadcast failed: " + e.getMessage());
        }
    }

    // in ChatSyncManager (interface/class you own)
    public void publishChannelMessage(UUID senderId, String server, String senderName, String channel, String message) {
        if (!enabled) return;

        try {
            // CHANMSG;;serverId;;senderUUID;;b64(server);;b64(senderName);;b64(channel);;b64(message)
            String payload = EX_CHANNEL_MSG
                    + ";;" + SERVER_ID
                    + ";;" + senderId
                    + ";;" + b64(nullSafe(server))
                    + ";;" + b64(nullSafe(senderName))
                    + ";;" + b64(nullSafe(channel))
                    + ";;" + b64(nullSafe(message));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync channel message publish failed: " + e.getMessage());
        }
    }

    public void publishChannelSystem(String server, String channel, String message) {
        if (!enabled) return;

        try {
            // CHANSYS;;serverId;;b64(server);;b64(channel);;b64(message)
            String payload = EX_CHANNEL_SYS
                    + ";;" + SERVER_ID
                    + ";;" + b64(nullSafe(server))
                    + ";;" + b64(nullSafe(channel))
                    + ";;" + b64(nullSafe(message));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync channel system publish failed: " + e.getMessage());
        }
    }

    /**
     * Helper pour envoyer un message "système" global (mute, ban, etc.)
     * Utilisée par MuteCommand: sync.broadcastSystemMessage(broadcastMsg);
     */
    public void broadcastSystemMessage(String message) {
        if (!enabled) return;
        String serverName;
        try {
            serverName = OreoEssentials.get().getConfigService().serverName();
        } catch (Throwable t) {
            serverName = Bukkit.getServer().getName();
        }
        // On force un channel générique "SYSTEM" (tu peux changer le nom si tu veux filtrer un jour)
        publishChannelSystem(serverName, "SYSTEM", message);
    }

    /** Broadcast an unmute to all servers. */
    public void broadcastUnmute(UUID playerId) {
        if (!enabled) return;

        try {
            // CTRL;;UNMUTE;;serverId;;playerUUID
            String payload = "CTRL;;UNMUTE;;" + SERVER_ID + ";;" + playerId;
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync UNMUTE broadcast failed: " + e.getMessage());
        }
    }

    /* ----------------------------- Subscribe/receive ----------------------------- */

    public void subscribeMessages() {
        if (!enabled) return;

        try {
            String q = rabbitChannel.queueDeclare().getQueue();
            rabbitChannel.queueBind(q, EXCHANGE_NAME, "");

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);

                // ---- Control messages (mute/unmute + chat control) ----
                if (msg.startsWith("CTRL;;")) {
                    // Possible forms:
                    // 1) CTRL;;UNMUTE;;serverId;;playerUUID
                    // 2) CTRL;;MUTE;;serverId;;playerUUID;;untilMillis;;b64(reason);;b64(by)
                    // 3) CTRL;;CHAT;;type;;serverId;;b64(value);;b64(actor)
                    String[] p = msg.split(";;", 8);
                    if (p.length < 2) return;

                    String action = p[1]; // "MUTE", "UNMUTE" or "CHAT";

                    // ======== CHAT CONTROL (GLOBAL_MUTE, SLOWMODE, CLEAR_CHAT) ========
                    if ("CHAT".equalsIgnoreCase(action)) {
                        if (p.length < 6) return;

                        String type     = p[2];              // e.g. "GLOBAL_MUTE", "SLOWMODE", "CLEAR_CHAT"
                        String originId = p[3];              // server UUID as string (can ignore if you want)
                        String valueB64 = p[4];
                        String actorB64 = p[5];

                        String value = fromB64(valueB64);
                        String actor = fromB64(actorB64);

                        // make final/effectively-final copies for lambda
                        final String fType  = type;
                        final String fValue = value;
                        final String fActor = actor;

                        // Apply on main thread using ModGuiService
                        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                            var plugin = OreoEssentials.get();
                            var svc = plugin.getModGuiService();
                            if (svc == null) return;

                            switch (fType.toUpperCase()) {
                                case "CLEAR_CHAT" -> {
                                    for (int i = 0; i < 200; i++) {
                                        Bukkit.broadcastMessage("");
                                    }
                                    Bukkit.broadcastMessage("§cChat has been cleared by §e" + fActor);
                                }
                                case "GLOBAL_MUTE" -> {
                                    boolean muted = Boolean.parseBoolean(fValue);
                                    svc.setChatMuted(muted);
                                    Bukkit.broadcastMessage(muted
                                            ? "§cGlobal chat has been muted by §e" + fActor
                                            : "§aGlobal chat has been unmuted.");
                                }
                                case "SLOWMODE" -> {
                                    int seconds;
                                    try {
                                        seconds = Integer.parseInt(fValue);
                                    } catch (NumberFormatException ex) {
                                        seconds = 0;
                                    }
                                    svc.setSlowmodeSeconds(Math.max(0, seconds));
                                    Bukkit.broadcastMessage("§eSlowmode is now §6" + seconds + "s §7(network).");
                                }
                                default -> {
                                    // unknown type, ignore
                                }
                            }
                        });
                        return;
                    }

                    // ======== LEGACY MUTE / UNMUTE CONTROL ========
                    if (p.length >= 4) {
                        String originServer = p[2]; // still unused
                        String uuidStr = p[3];

                        try {
                            UUID target = UUID.fromString(uuidStr);
                            if ("UNMUTE".equalsIgnoreCase(action)) {
                                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                                    if (muteService != null) muteService.unmute(target);
                                });
                            } else if ("MUTE".equalsIgnoreCase(action) && p.length >= 7) {
                                long until = Long.parseLong(p[4]);
                                String reason = fromB64(p[5]);
                                String by = fromB64(p[6]);
                                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                                    if (muteService != null) muteService.mute(target, until, reason, by);
                                });
                            }
                        } catch (Exception ignored) {
                            /* bad payload */
                        }
                    }
                    return; // do not fall through to normal chat handling
                }

                // ---- Channel system messages (CHANSYS) ----
                if (msg.startsWith(EX_CHANNEL_SYS + ";;")) {
                    // CHANSYS;;serverId;;b64(server);;b64(channel);;b64(message)
                    String[] p = msg.split(";;", 5);
                    if (p.length < 5) return;

                    String originServerId = p[1];
                    if (SERVER_ID.toString().equals(originServerId)) return; // ignore loopback

                    String serverName = fromB64(p[2]);   // not used now, kept for future
                    String channel    = fromB64(p[3]);   // e.g. "SYSTEM"
                    String message    = fromB64(p[4]);

                    final String toSend = ChatColor.translateAlternateColorCodes('&', message);

                    Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                        Bukkit.broadcastMessage(toSend);
                        Bukkit.getConsoleSender().sendMessage(toSend);
                    });
                    return;
                }

                // ---- Channel user messages (CHANMSG) ----
                if (msg.startsWith(EX_CHANNEL_MSG + ";;")) {
                    // CHANMSG;;serverId;;senderUUID;;b64(server);;b64(senderName);;b64(channel);;b64(message)
                    String[] p = msg.split(";;", 7);
                    if (p.length < 7) return;

                    String originServerId = p[1];
                    if (SERVER_ID.toString().equals(originServerId)) return; // ignore loopback

                    String senderUuidStr  = p[2];
                    String serverNameB64  = p[3];
                    String senderNameB64  = p[4];
                    String channelB64     = p[5];
                    String messageB64     = p[6];

                    UUID senderUuid = null;
                    try {
                        senderUuid = UUID.fromString(senderUuidStr);
                    } catch (Exception ignored) {}

                    // mute check réseau
                    if (muteService != null && senderUuid != null && muteService.isMuted(senderUuid)) {
                        return;
                    }

                    String message = fromB64(messageB64);
                    final String toSend = ChatColor.translateAlternateColorCodes('&', message);

                    Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(toSend));
                    return;
                }

                // ---- Legacy simple chat messages ----
                String[] split = msg.split(";;", 4);
                if (split.length != 4) return;

                String originServerId = split[0];
                String senderUuidStr = split[1];
                String nameB64 = split[2]; // not used currently, kept for future
                String msgB64 = split[3];

                // Ignore loopback
                if (SERVER_ID.toString().equals(originServerId)) return;

                UUID senderUuid = null;
                try {
                    senderUuid = UUID.fromString(senderUuidStr);
                } catch (Exception ignored) {
                }

                // If we have muteService and a valid UUID, drop message when that UUID is muted locally
                if (muteService != null && senderUuid != null && muteService.isMuted(senderUuid)) return;

                String decodedMessage = fromB64(msgB64);

                // Broadcast on main thread; message is expected to use '&' color codes
                final String toSend = ChatColor.translateAlternateColorCodes('&', decodedMessage);
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(toSend));
            };

            rabbitChannel.basicConsume(q, true, cb, tag -> {});
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync subscribe failed: " + e.getMessage());
        }
    }

    /* ----------------------------- Lifecycle ----------------------------- */

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

    /* ----------------------------- Helpers ----------------------------- */

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
