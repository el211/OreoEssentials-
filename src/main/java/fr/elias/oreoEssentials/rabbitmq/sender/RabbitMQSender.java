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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class RabbitMQSender implements PacketSender {

    private static final String GLOBAL_FANOUT_EXCHANGE = "oreo.global.fanout";
    private static final String GLOBAL_QUEUE_PREFIX = "oreo.global.";

    private final String connectionString;
    private final String serverName;
    private final BooleanSupplier debugEnabled;

    private volatile Connection connection;
    private volatile Channel channel;

    private final List<IncomingPacketListener> listeners = new ArrayList<>();
    private final Set<String> subscribedLogicalIds = ConcurrentHashMap.newKeySet();
    private final Set<String> consumingQueues = ConcurrentHashMap.newKeySet();
    private final Map<String, String> consumerTagsByQueue = new ConcurrentHashMap<>();

    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "OreoEssentials-RMQ-Reconnect");
                t.setDaemon(true);
                return t;
            });
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile boolean reconnecting = false;
    private volatile boolean closed = false;

    public RabbitMQSender(String connectionString, String serverName) {
        this(connectionString, serverName, () -> false);
    }

    public RabbitMQSender(String connectionString, String serverName, BooleanSupplier debugEnabled) {
        this.connectionString = connectionString;
        this.serverName = (serverName == null || serverName.isBlank()) ? "unknown" : serverName.trim();
        this.debugEnabled = debugEnabled != null ? debugEnabled : () -> false;
    }

    @Override
    public void sendPacket(PacketChannel packetChannel, byte[] content) {
        if (closed) return;
        try {
            ensureConnected();
            for (String id : packetChannel) {
                if (id == null || id.isBlank()) continue;
                if ("global".equalsIgnoreCase(id)) {
                    channel.basicPublish(GLOBAL_FANOUT_EXCHANGE, "", null, content);
                    dbg("[RMQ/SEND@" + serverName + "] GLOBAL fanout exchange=" + GLOBAL_FANOUT_EXCHANGE
                            + " bytes=" + content.length);
                } else {
                    channel.basicPublish("", id, null, content);
                    dbg("[RMQ/SEND@" + serverName + "] direct queue=" + id + " bytes=" + content.length);
                }
            }
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to send RabbitMQ message: " + e.getMessage());
            reconnect();
        }
    }

    @Override
    public void registerChannel(PacketChannel packetChannel) {
        if (closed) return;
        try {
            ensureConnected();
            for (String id : packetChannel) {
                if (id == null || id.isBlank()) continue;
                final String logicalId = id.trim();
                if (!subscribedLogicalIds.add(logicalId)) continue;

                if ("global".equalsIgnoreCase(logicalId)) {
                    declareGlobalFanout();
                    String globalQueue = GLOBAL_QUEUE_PREFIX + serverName;
                    declareQueue(globalQueue);
                    bindQueueToGlobalFanout(globalQueue);
                    startConsumer(globalQueue);
                    dbg("[RMQ/REG@" + serverName + "] channel=global -> queue=" + globalQueue
                            + " exchange=" + GLOBAL_FANOUT_EXCHANGE);
                } else {
                    declareQueue(logicalId);
                    startConsumer(logicalId);
                    dbg("[RMQ/REG@" + serverName + "] channel=" + logicalId + " -> directQueue=" + logicalId);
                }
            }
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to register channel(s): " + e.getMessage());
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
        if (closed) throw new IllegalStateException("RabbitMQ sender is closed");
        if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) return;
        if (!connect()) throw new IllegalStateException("RabbitMQ reconnect failed");
        try {
            rebindAllConsumers();
        } catch (IOException e) {
            closeConnectionOnly();
            throw new IllegalStateException("RabbitMQ consumer rebind failed", e);
        }
    }

    private void reconnect() {
        if (closed || reconnectScheduler.isShutdown()) return;
        synchronized (this) {
            if (reconnecting) return;
            reconnecting = true;
        }

        int attempt = reconnectAttempts.getAndIncrement();
        long delaySeconds = Math.min(1L << Math.min(attempt, 5), 30L);
        System.err.println("[OreoEssentials] Scheduling RabbitMQ reconnect attempt #" + (attempt + 1)
                + " in " + delaySeconds + "s...");

        reconnectScheduler.schedule(() -> {
            reconnecting = false;
            if (closed) return;

            System.err.println("[OreoEssentials] Attempting to reconnect to RabbitMQ (attempt #" + (attempt + 1) + ")...");
            closeConnectionOnly();

            if (connect()) {
                try {
                    rebindAllConsumers();
                    reconnectAttempts.set(0);
                    System.out.println("[OreoEssentials] Successfully reconnected to RabbitMQ!");
                } catch (Exception e) {
                    System.err.println("[OreoEssentials] Failed to rebind consumers after reconnect: " + e.getMessage());
                    closeConnectionOnly();
                    reconnect();
                }
            } else {
                reconnect();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public synchronized boolean connect() {
        if (closed) return false;
        try {
            if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) return true;

            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(connectionString);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);
            factory.setConnectionTimeout(5000);
            factory.setHandshakeTimeout(5000);

            connection = factory.newConnection();
            channel = connection.createChannel();
            if (subscribedLogicalIds.contains("global")) declareGlobalFanout();

            System.out.println("[OreoEssentials] Connected to RabbitMQ successfully!");
            dbg("[RMQ/CONNECT@" + serverName + "] channelOpen=" + channel.isOpen());
            return true;
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to connect to RabbitMQ: " + e.getMessage());
            closeConnectionOnly();
            return false;
        }
    }

    @Override
    public void close() {
        closed = true;
        reconnecting = false;
        reconnectScheduler.shutdownNow();
        closeConnectionOnly();
    }

    private synchronized void closeConnectionOnly() {
        try {
            for (Map.Entry<String, String> entry : consumerTagsByQueue.entrySet()) {
                String tag = entry.getValue();
                if (tag != null && channel != null && channel.isOpen()) {
                    try { channel.basicCancel(tag); } catch (Exception ignored) {}
                }
            }
            consumerTagsByQueue.clear();
            consumingQueues.clear();
            if (channel != null) try { channel.close(); } catch (Exception ignored) {}
            if (connection != null) try { connection.close(); } catch (Exception ignored) {}
        } finally {
            channel = null;
            connection = null;
        }
    }

    private void declareQueue(String name) throws IOException {
        channel.queueDeclare(name, true, false, false, null);
    }

    private void declareGlobalFanout() throws IOException {
        channel.exchangeDeclare(GLOBAL_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);
    }

    private void bindQueueToGlobalFanout(String queue) throws IOException {
        channel.queueBind(queue, GLOBAL_FANOUT_EXCHANGE, "");
    }

    private void startConsumer(String queue) throws IOException {
        if (queue == null || queue.isBlank()) return;
        if (!consumingQueues.add(queue)) return;

        String tag = channel.basicConsume(queue, false, (consumerTag, delivery) -> {
            Channel deliveryChannel = channel;
            try {
                byte[] content = delivery.getBody();
                handleIncomingPacket(queue, content);
                if (deliveryChannel != null && deliveryChannel.isOpen()) {
                    deliveryChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            } catch (Exception ex) {
                System.err.println("[OreoEssentials] SEVERE packet handler threw on queue=" + queue
                        + " deliveryTag=" + delivery.getEnvelope().getDeliveryTag()
                        + " error=" + ex.getMessage());
                try {
                    if (deliveryChannel != null && deliveryChannel.isOpen()) {
                        deliveryChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                    }
                } catch (IOException ignored) {}
            }
        }, consumerTag -> dbg("[RMQ/CANCELLED@" + serverName + "] consumerTag=" + consumerTag + " queue=" + queue));

        consumerTagsByQueue.put(queue, tag);
    }

    private void rebindAllConsumers() throws IOException {
        consumerTagsByQueue.clear();
        consumingQueues.clear();
        List<String> toRebind = new ArrayList<>(subscribedLogicalIds);
        subscribedLogicalIds.clear();
        for (String logical : toRebind) {
            registerChannel(PacketChannels.individual(logical));
        }
    }

    private void handleIncomingPacket(String queueId, byte[] content) {
        String logicalId = queueId != null && queueId.startsWith(GLOBAL_QUEUE_PREFIX) ? "global" : queueId;
        PacketChannel logical = PacketChannels.individual(logicalId);

        List<IncomingPacketListener> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }
        for (IncomingPacketListener listener : snapshot) {
            try {
                listener.onReceive(logical, content);
            } catch (Throwable t) {
                System.err.println("[OreoEssentials] ❌ Listener threw while handling incoming packet: " + t.getMessage());
            }
        }
    }

    private void dbg(String msg) {
        if (!isDebugEnabled()) return;
        System.out.println("[OreoEssentials] " + msg);
    }

    private boolean isDebugEnabled() {
        try { return debugEnabled.getAsBoolean(); }
        catch (Throwable ignored) { return false; }
    }
}
