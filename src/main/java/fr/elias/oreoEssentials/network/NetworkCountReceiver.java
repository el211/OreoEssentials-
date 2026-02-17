package fr.elias.oreoEssentials.network;

import com.rabbitmq.client.*;
import fr.elias.oreoEssentials.OreoEssentials;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class NetworkCountReceiver {

    public static final String EXCHANGE_NAME = "oreo.network.counts";

    private static final AtomicInteger NETWORK_TOTAL = new AtomicInteger(0);
    private static final Map<String, Integer> SERVER_TOTALS = new ConcurrentHashMap<>();

    public static int getNetworkTotal() {
        return NETWORK_TOTAL.get();
    }


    public static int getServerTotal(String serverName) {
        return SERVER_TOTALS.getOrDefault(serverName, 0);
    }


    private final OreoEssentials plugin;
    private final String uri;

    private volatile Connection connection;
    private volatile Channel channel;
    private volatile boolean running = false;

    public NetworkCountReceiver(OreoEssentials plugin, String uri) {
        this.plugin = plugin;
        this.uri    = uri;
    }


    public boolean start() {
        running = true;
        return connect();
    }

    private boolean connect() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(uri);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5_000);

            connection = factory.newConnection("OreoEssentials-CountReceiver");
            channel    = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);

            String queue = channel.queueDeclare("", false, true, true, null).getQueue();
            channel.queueBind(queue, EXCHANGE_NAME, "");

            channel.basicConsume(queue, true, (consumerTag, delivery) -> {
                try {
                    parseAndCache(delivery.getBody());
                } catch (Throwable t) {
                    plugin.getLogger().warning("[NetworkCountReceiver] Failed to parse count message: " + t.getMessage());
                }
            }, consumerTag -> {
                plugin.getLogger().warning("[NetworkCountReceiver] Consumer cancelled — reconnecting...");
                if (running) reconnectAsync();
            });

            plugin.getLogger().info("[NetworkCountReceiver] Connected, listening on exchange '" + EXCHANGE_NAME + "'");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[NetworkCountReceiver] Failed to connect: " + e.getMessage());
            connection = null;
            channel    = null;
            return false;
        }
    }

    private void parseAndCache(byte[] body) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));

        int networkTotal  = in.readInt();
        int serverCount   = in.readInt();

        NETWORK_TOTAL.set(networkTotal);

        for (int i = 0; i < serverCount; i++) {
            String serverName  = in.readUTF();
            int    playerCount = in.readInt();
            SERVER_TOTALS.put(serverName, playerCount);
        }
    }

    private void reconnectAsync() {
        Thread t = new Thread(() -> {
            try { Thread.sleep(5_000); } catch (InterruptedException ignored) {}
            if (running) {
                closeQuietly();
                connect();
            }
        }, "OreoEssentials-CountReceiverReconnect");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try { if (channel    != null) channel.close();    } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        channel    = null;
        connection = null;
    }
}