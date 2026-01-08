package fr.elias.oreoEssentials.rabbitmq.sender;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.event.IncomingPacketListener;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class RabbitMQSender implements PacketSender {

    private final String connectionString;

    private volatile Connection connection;
    private volatile Channel channel;

    private final List<IncomingPacketListener> listeners = new ArrayList<>();
    private final List<String> queues = new ArrayList<>();

    public RabbitMQSender(String connectionString) {
        this.connectionString = connectionString;
    }


    @Override
    public void sendPacket(PacketChannel packetChannel, byte[] content) {
        try {
            ensureConnected();
            for (String id : packetChannel) {
                this.channel.basicPublish("", id, null, content);
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
                if (!queues.contains(id)) {
                    queues.add(id);
                    declareQueue(id);
                    startConsumer(id);
                }
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
            // After fresh connect, re-declare and re-bind consumers for previously registered queues
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

            // idempotent: declare existing queues again
            for (String q : queues) {
                declareQueue(q);
            }
            System.out.println("[OreoEssentials]  Connected to RabbitMQ successfully!");
            return true;
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to connect to RabbitMQ!");
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        try {
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
        this.channel.queueDeclare(name, true, false, false, null);
    }

    private void startConsumer(String queue) throws IOException {
        this.channel.basicConsume(queue, false, (consumerTag, delivery) -> {
            try {
                byte[] content = delivery.getBody();
                handleIncomingPacket(queue, content);
                this.channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception ex) {
                try {
                    this.channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                } catch (IOException ioEx) {
                    System.err.println("[OreoEssentials] ❌ basicNack failed: " + ioEx.getMessage());
                }
                throw ex;
            }
        }, consumerTag -> {
        });
    }

    private void rebindAllConsumers() throws IOException {
        for (String q : queues) {
            declareQueue(q);
            startConsumer(q);
        }
    }

    private void handleIncomingPacket(String queueId, byte[] content) {
        try {
            PacketChannel logical = PacketChannels.individual(queueId);
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
}
