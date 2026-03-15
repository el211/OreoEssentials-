package fr.elias.oreoEssentials.modules.webpanel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Publishes player sync payloads to the owner's RabbitMQ instance and
 * consumes incoming inventory action messages (SELL / DELETE) from the backend.
 *
 * Queues:
 *   "oreo.webpanel.sync"    — plugin publishes, backend consumes (player data)
 *   "oreo.webpanel.actions" — backend publishes, plugin consumes (sell/delete actions)
 */
public class WebPanelRabbitPublisher {

    private static final String SYNC_QUEUE    = "oreo.webpanel.sync";
    private static final String ACTIONS_QUEUE = "oreo.webpanel.actions";

    private final Connection connection;
    private final Logger logger;
    private Channel actionConsumerChannel;

    /**
     * Opens a persistent AMQP connection.
     * @throws Exception if the URI is invalid or connection refused.
     */
    public WebPanelRabbitPublisher(String amqpUri, Logger logger) throws Exception {
        this.logger = logger;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(amqpUri);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        this.connection = factory.newConnection("oreo-webpanel");
        logger.info("[WebPanel-AMQP] Connected to RabbitMQ.");
    }

    /**
     * Publishes one player sync payload.
     * @param uuid       Minecraft UUID
     * @param playerName Minecraft name
     * @param dataJson   Full JSON string built by WebPanelSyncService
     */
    /**
     * Starts a durable consumer on the "oreo.webpanel.actions" queue.
     * The callback receives each action JsonObject; runs on the AMQP thread —
     * the caller is responsible for scheduling work back to the main thread.
     */
    public void startActionConsumer(java.util.function.Consumer<JsonObject> onAction) {
        try {
            actionConsumerChannel = connection.createChannel();
            actionConsumerChannel.queueDeclare(ACTIONS_QUEUE, true, false, false, null);
            actionConsumerChannel.basicConsume(ACTIONS_QUEUE, false,
                    (tag, delivery) -> {
                        try {
                            String body = new String(delivery.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                            JsonObject action = new Gson().fromJson(body, JsonObject.class);
                            onAction.accept(action);
                            actionConsumerChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        } catch (Exception e) {
                            logger.warning("[WebPanel-AMQP] Failed to handle action: " + e.getMessage());
                        }
                    },
                    tag -> logger.warning("[WebPanel-AMQP] Action consumer cancelled")
            );
            logger.info("[WebPanel-AMQP] Subscribed to actions queue.");
        } catch (Exception e) {
            logger.warning("[WebPanel-AMQP] Could not start action consumer: " + e.getMessage());
        }
    }

    public void publish(UUID uuid, String playerName, String dataJson) {
        try (Channel channel = connection.createChannel()) {
            // Declare queue idempotently — safe to call multiple times
            channel.queueDeclare(SYNC_QUEUE, true, false, false, null);

            JsonObject payload = new JsonObject();
            payload.addProperty("playerUuid",     uuid.toString());
            payload.addProperty("playerName",     playerName);
            payload.addProperty("playerDataJson", dataJson);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            channel.basicPublish(
                    "",    // default exchange (direct to queue by name)
                    SYNC_QUEUE,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    body
            );
        } catch (Exception e) {
            logger.warning("[WebPanel-AMQP] Publish failed: " + e.getMessage());
        }
    }

    /** Call on plugin disable. */
    public void close() {
        try { if (actionConsumerChannel != null && actionConsumerChannel.isOpen()) actionConsumerChannel.close(); } catch (Exception ignored) {}
        try { connection.close(); } catch (Exception ignored) {}
    }
}
