package fr.elias.oreoEssentials.modules.webpanel;

import com.google.gson.JsonObject;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Publishes player sync payloads to the owner's RabbitMQ instance.
 * The backend consumes from the same queue for real-time dashboard updates.
 *
 * Queue: "oreo.webpanel.sync" — durable, non-exclusive, non-auto-delete.
 * Messages are persistent so they survive RabbitMQ restarts.
 */
public class WebPanelRabbitPublisher {

    private static final String QUEUE = "oreo.webpanel.sync";

    private final Connection connection;
    private final Logger logger;

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
    public void publish(UUID uuid, String playerName, String dataJson) {
        try (Channel channel = connection.createChannel()) {
            // Declare queue idempotently — safe to call multiple times
            channel.queueDeclare(QUEUE, true, false, false, null);

            JsonObject payload = new JsonObject();
            payload.addProperty("playerUuid",     uuid.toString());
            payload.addProperty("playerName",     playerName);
            payload.addProperty("playerDataJson", dataJson);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            channel.basicPublish(
                    "",    // default exchange (direct to queue by name)
                    QUEUE,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    body
            );
        } catch (Exception e) {
            logger.warning("[WebPanel-AMQP] Publish failed: " + e.getMessage());
        }
    }

    /** Call on plugin disable. */
    public void close() {
        try { connection.close(); } catch (Exception ignored) {}
    }
}
