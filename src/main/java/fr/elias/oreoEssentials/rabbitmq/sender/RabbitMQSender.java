package fr.elias.oreoEssentials.rabbitmq.sender;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.IncomingPacketListener;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RabbitMQSender implements PacketSender {

    // ---- Broadcast (GLOBAL) fanout exchange ----
    // All servers publish to this exchange, each server has its own queue bound to it.
    private static final String GLOBAL_FANOUT_EXCHANGE = "oreo.global.fanout";
    // Queue name pattern for each server: oreo.global.<serverName>
    private static final String GLOBAL_QUEUE_PREFIX = "oreo.global.";

    private final String connectionString;
    private final String serverName; // IMPORTANT: must be unique per backend (ex shard-0-0, shard-0-1)

    private volatile Connection connection;
    private volatile Channel channel;

    private final List<IncomingPacketListener> listeners = new ArrayList<>();

    // We store "logical ids" (e.g. "global", "homes", "chat") that we are subscribed to.
    private final Set<String> subscribedLogicalIds = ConcurrentHashMap.newKeySet();

    // We store physical queue names we actually consume from (e.g. "global" OR "oreo.global.shard-0-0").
    private final Set<String> consumingQueues = ConcurrentHashMap.newKeySet();

    // Track consumer tags so we can avoid duplicate consumers after reconnect.
    private final Map<String, String> consumerTagsByQueue = new ConcurrentHashMap<>();

    public RabbitMQSender(String connectionString, String serverName) {
        this.connectionString = connectionString;
        this.serverName = (serverName == null || serverName.isBlank()) ? "unknown" : serverName.trim();
    }

    @Override
    public void sendPacket(PacketChannel packetChannel, byte[] content) {
        try {
            ensureConnected();

            for (String id : packetChannel) {
                if (id == null || id.isBlank()) continue;

                // GLOBAL must be a broadcast (fanout exchange)
                if ("global".equalsIgnoreCase(id)) {
                    // Fanout ignores routing key, but basicPublish requires one -> use empty string
                    channel.basicPublish(GLOBAL_FANOUT_EXCHANGE, "", null, content);

                    dbg("[RMQ/SEND@" + serverName + "] GLOBAL fanout exchange=" + GLOBAL_FANOUT_EXCHANGE
                            + " bytes=" + content.length);
                    continue;
                }

                // Everything else: keep your existing behavior (direct to queue via default exchange)
                channel.basicPublish("", id, null, content);

                dbg("[RMQ/SEND@" + serverName + "] direct queue=" + id + " bytes=" + content.length);
            }

            System.out.println("[OreoEssentials]  Sent RabbitMQ message");
        } catch (IOException e) {
            System.err.println("[OreoEssentials] ❌ Failed to send RabbitMQ message.");
            e.printStackTrace();
            reconnect();
        }
    }

    @Override
    public void registerChannel(PacketChannel packetChannel) {
        try {
            ensureConnected();

            for (String id : packetChannel) {
                if (id == null || id.isBlank()) continue;
                final String logicalId = id.trim();

                if (!subscribedLogicalIds.add(logicalId)) {
                    // already registered
                    continue;
                }

                // GLOBAL = fanout exchange + per-server queue
                if ("global".equalsIgnoreCase(logicalId)) {
                    declareGlobalFanout();
                    String globalQueue = GLOBAL_QUEUE_PREFIX + serverName;

                    declareQueue(globalQueue);
                    bindQueueToGlobalFanout(globalQueue);
                    startConsumer(globalQueue);

                    dbg("[RMQ/REG@" + serverName + "] channel=global -> queue=" + globalQueue
                            + " exchange=" + GLOBAL_FANOUT_EXCHANGE);
                    continue;
                }

                // Other channels: old behavior (each server consumes the same queue name)
                // NOTE: this is "competing consumers" by design. It's fine for targeted queues,
                // but DON'T use it for broadcast.
                declareQueue(logicalId);
                startConsumer(logicalId);

                dbg("[RMQ/REG@" + serverName + "] channel=" + logicalId + " -> directQueue=" + logicalId);
            }
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to register channel(s)!");
            e.printStackTrace();
            reconnect();
        }
    }

    @Override
    public void registerListener(IncomingPacketListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private synchronized void ensureConnected() {
        try {
            if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) return;
            connect();
            rebindAllConsumers();
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ not connected and reconnect failed", e);
        }
    }

    private void reconnect() {
        System.err.println("[OreoEssentials] 🔄 Attempting to reconnect to RabbitMQ...");
        close();
        if (connect()) {
            System.out.println("[OreoEssentials]  Successfully reconnected to RabbitMQ!");
            try {
                rebindAllConsumers();
            } catch (Exception e) {
                System.err.println("[OreoEssentials] ❌ Failed to rebind consumers after reconnect:");
                e.printStackTrace();
            }
        } else {
            System.err.println("[OreoEssentials] ❌ Reconnection failed!");
        }
    }

    public boolean connect() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(connectionString);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            connection = factory.newConnection();
            channel = connection.createChannel();

            // declare required infrastructure if global is used
            if (subscribedLogicalIds.contains("global")) {
                declareGlobalFanout();
            }

            System.out.println("[OreoEssentials]  Connected to RabbitMQ successfully!");
            dbg("[RMQ/CONNECT@" + serverName + "] channelOpen=" + channel.isOpen());

            return true;
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to connect to RabbitMQ!");
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        try {
            // Try to cancel consumers (best-effort)
            try {
                for (Map.Entry<String, String> entry : consumerTagsByQueue.entrySet()) {
                    String q = entry.getKey();
                    String tag = entry.getValue();
                    if (tag != null && channel != null && channel.isOpen()) {
                        try {
                            channel.basicCancel(tag);
                            dbg("[RMQ/CANCEL@" + serverName + "] queue=" + q + " tag=" + tag);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            consumerTagsByQueue.clear();
            consumingQueues.clear();

            if (channel != null) {
                try { channel.close(); } catch (Exception ignore) {}
            }
            if (connection != null) {
                try { connection.close(); } catch (Exception ignore) {}
            }
        } finally {
            channel = null;
            connection = null;
            System.out.println("[OreoEssentials]  RabbitMQ connection closed.");
        }
    }

    private void declareQueue(String name) throws IOException {
        // durable = true, exclusive = false, autoDelete = false
        channel.queueDeclare(name, true, false, false, null);
        dbg("[RMQ/QUEUE@" + serverName + "] declared queue=" + name);
    }

    private void declareGlobalFanout() throws IOException {
        channel.exchangeDeclare(GLOBAL_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);
        dbg("[RMQ/XCHG@" + serverName + "] declared fanout exchange=" + GLOBAL_FANOUT_EXCHANGE);
    }

    private void bindQueueToGlobalFanout(String queue) throws IOException {
        channel.queueBind(queue, GLOBAL_FANOUT_EXCHANGE, "");
        dbg("[RMQ/BIND@" + serverName + "] queue=" + queue + " -> exchange=" + GLOBAL_FANOUT_EXCHANGE);
    }

    private void startConsumer(String queue) throws IOException {
        if (queue == null || queue.isBlank()) return;

        // avoid duplicate consumers
        if (!consumingQueues.add(queue)) {
            dbg("[RMQ/CONS@" + serverName + "] already consuming queue=" + queue);
            return;
        }

        String tag = channel.basicConsume(queue, false, (consumerTag, delivery) -> {
            try {
                byte[] content = delivery.getBody();
                dbg("[RMQ/RECV@" + serverName + "] queue=" + queue
                        + " bytes=" + content.length
                        + " deliveryTag=" + delivery.getEnvelope().getDeliveryTag());

                handleIncomingPacket(queue, content);

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception ex) {
                try {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                } catch (IOException ioEx) {
                    System.err.println("[OreoEssentials] ❌ basicNack failed: " + ioEx.getMessage());
                }
                throw ex;
            }
        }, consumerTag -> {
            dbg("[RMQ/CANCELLED@" + serverName + "] consumerTag=" + consumerTag + " queue=" + queue);
        });

        consumerTagsByQueue.put(queue, tag);
        dbg("[RMQ/CONS@" + serverName + "] started consumer queue=" + queue + " tag=" + tag);
    }

    private void rebindAllConsumers() throws IOException {
        dbg("[RMQ/REBIND@" + serverName + "] subscribedLogicalIds=" + subscribedLogicalIds);

        // Important: after reconnect, our channel changed, consumer tags are invalid.
        consumerTagsByQueue.clear();
        consumingQueues.clear();

        // Re-register each logical subscription to rebuild infra + consumers
        for (String logical : new ArrayList<>(subscribedLogicalIds)) {
            // reuse existing method to keep logic identical
            registerChannel(PacketChannels.individual(logical));
        }
    }

    private void handleIncomingPacket(String queueId, byte[] content) {
        try {
            // Map physical queue -> logical channel id
            // - global fanout queue: oreo.global.<serverName> => logical "global"
            // - other queues: logical == queue name
            final String logicalId;
            if (queueId != null && queueId.startsWith(GLOBAL_QUEUE_PREFIX)) {
                logicalId = "global";
            } else {
                logicalId = queueId;
            }

            PacketChannel logical = PacketChannels.individual(logicalId);

            List<IncomingPacketListener> snapshot;
            synchronized (listeners) {
                snapshot = new ArrayList<>(listeners);
            }

            for (IncomingPacketListener listener : snapshot) {
                try {
                    listener.onReceive(logical, content);
                } catch (Throwable t) {
                    System.err.println("[OreoEssentials] ❌ Listener threw while handling incoming packet:");
                    t.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Error while handling incoming packet!");
            e.printStackTrace();
        }
    }

    private void dbg(String msg) {
        // keep simple: always log RMQ debug to stdout (or change to plugin logger if you pass plugin here)
        // If you want it gated by config.debug, tell me and I’ll wire it.
        System.out.println("[OreoEssentials] " + msg);
    }
}
