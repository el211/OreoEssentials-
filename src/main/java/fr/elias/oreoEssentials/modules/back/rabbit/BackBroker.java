package fr.elias.oreoEssentials.modules.back.rabbit;

import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.back.service.BackService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles cross-server /back teleportation using RabbitMQ
 */
public class BackBroker {

    private final OreoEssentials plugin;
    private final BackService backService;
    private final Connection rabbitConnection;
    private final String exchangeName = "oreo_back";
    private Channel channel;

    public BackBroker(OreoEssentials plugin, BackService backService, Connection rabbitConnection) {
        this.plugin = plugin;
        this.backService = backService;
        this.rabbitConnection = rabbitConnection;
    }

    /**
     * Initialize the broker - declares exchange and sets up listener
     */
    public void start() {
        try {
            channel = rabbitConnection.createChannel();

            // Declare fanout exchange for back requests
            channel.exchangeDeclare(exchangeName, "topic", true);

            // Create queue for this server
            String serverName = plugin.getConfigService().serverName();
            String queueName = "back_" + serverName;

            channel.queueDeclare(queueName, false, false, true, null);
            channel.queueBind(queueName, exchangeName, serverName);

            // Set up consumer to receive back requests
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                handleIncomingBackRequest(message);
            };

            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});

            plugin.getLogger().info("[BackBroker] Started successfully on server: " + serverName);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BackBroker] Failed to start", e);
        }
    }

    /**
     * Request a cross-server back teleport
     *
     * @param player The player requesting /back
     * @param backLocation The location to teleport to (on another server)
     */
    public void requestCrossServerBack(Player player, BackLocation backLocation) {
        String targetServer = backLocation.getServer();

        plugin.getLogger().info("[BackBroker] Requesting cross-server back for " +
                player.getName() + " to server: " + targetServer);

        // Build the packet
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "BACK_REQUEST");
        packet.addProperty("player_uuid", player.getUniqueId().toString());
        packet.addProperty("player_name", player.getName());
        packet.addProperty("target_server", targetServer);
        packet.addProperty("world", backLocation.getWorldName());
        packet.addProperty("x", backLocation.getX());
        packet.addProperty("y", backLocation.getY());
        packet.addProperty("z", backLocation.getZ());
        packet.addProperty("yaw", backLocation.getYaw());
        packet.addProperty("pitch", backLocation.getPitch());

        try {
            // Publish to target server's routing key
            channel.basicPublish(
                    exchangeName,
                    targetServer, // routing key = target server name
                    null,
                    packet.toString().getBytes(StandardCharsets.UTF_8)
            );

            plugin.getLogger().info("[BackBroker] Sent back packet to " + targetServer);

            // Now transfer the player to that server using BungeeCord
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerToServer(player, targetServer);
            }, 5L); // 5 tick delay to ensure packet arrives first

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BackBroker] Failed to send back request", e);
            player.sendMessage("§cFailed to send cross-server back request.");
        }
    }

    /**
     * Handle incoming back request from RabbitMQ
     */
    private void handleIncomingBackRequest(String message) {
        try {
            JsonObject json = plugin.getGson().fromJson(message, JsonObject.class);

            if (!json.has("type") || !json.get("type").getAsString().equals("BACK_REQUEST")) {
                return;
            }

            String playerUuidStr = json.get("player_uuid").getAsString();
            String worldName = json.get("world").getAsString();
            double x = json.get("x").getAsDouble();
            double y = json.get("y").getAsDouble();
            double z = json.get("z").getAsDouble();
            float yaw = json.get("yaw").getAsFloat();
            float pitch = json.get("pitch").getAsFloat();

            UUID playerUuid = UUID.fromString(playerUuidStr);

            plugin.getLogger().info("[BackBroker] Received back request for player: " + playerUuidStr +
                    " to world: " + worldName);

            // Schedule the teleport on the main thread when player joins
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUuid);

                // Player hasn't joined yet - wait for them
                if (player == null || !player.isOnline()) {
                    plugin.getLogger().info("[BackBroker] Player not online yet, will teleport on join");

                    // Store a pending teleport
                    BackLocation pending = new BackLocation(
                            plugin.getConfigService().serverName(),
                            worldName, x, y, z, yaw, pitch
                    );
                    plugin.getPendingBackTeleports().put(playerUuid, pending);
                    return;
                }

                // Player is already online - teleport immediately
                teleportPlayerToBack(player, worldName, x, y, z, yaw, pitch);
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[BackBroker] Error handling back request", e);
        }
    }

    /**
     * Teleport a player to their back location
     */
    private void teleportPlayerToBack(Player player, String worldName,
                                      double x, double y, double z,
                                      float yaw, float pitch) {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage("§cWorld '" + worldName + "' is not loaded on this server.");
            plugin.getLogger().warning("[BackBroker] World not found: " + worldName);
            return;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);

        player.teleport(location);
        player.sendMessage("§aTeleported back!");

        plugin.getLogger().info("[BackBroker] Teleported " + player.getName() +
                " to back location in world: " + worldName);
    }

    /**
     * Send player to another BungeeCord server
     */
    private void sendPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());

            plugin.getLogger().info("[BackBroker] Sent " + player.getName() +
                    " to server: " + serverName);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BackBroker] Failed to send player to server", e);
            player.sendMessage("§cFailed to transfer to server: " + serverName);
        }
    }

    /**
     * Shutdown the broker
     */
    public void shutdown() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            plugin.getLogger().info("[BackBroker] Shutdown complete");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[BackBroker] Error during shutdown", e);
        }
    }
}