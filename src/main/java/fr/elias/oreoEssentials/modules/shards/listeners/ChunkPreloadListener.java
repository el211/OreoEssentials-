package fr.elias.oreoEssentials.modules.shards.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkPreloadListener implements Listener {

    private final Plugin plugin;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    // Store pending teleports: UUID -> Location
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    public ChunkPreloadListener(Plugin plugin, String redisHost, int redisPort, String redisPassword) {
        this.plugin = plugin;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;

        startRedisListener();
    }

    private void startRedisListener() {
        new Thread(() -> {
            try (Jedis sub = new Jedis(redisHost, redisPort)) {
                if (redisPassword != null && !redisPassword.isEmpty()) {
                    sub.auth(redisPassword);
                }

                plugin.getLogger().info("[ShardPreload] Listening for chunk preload requests...");

                sub.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handlePreloadRequest(message);
                    }
                }, "shard_preload_chunks");

            } catch (Exception e) {
                plugin.getLogger().severe("[ShardPreload] Redis listener died: " + e.getMessage());
            }
        }, "ShardPreload-Redis").start();
    }

    private void handlePreloadRequest(String message) {
        // Message format: "UUID|targetShard|x|z"
        String[] parts = message.split("\\|");
        if (parts.length != 4) return;

        try {
            UUID playerId = UUID.fromString(parts[0]);
            String targetShard = parts[1];
            double x = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            // Check if this is OUR shard
            String currentShard = System.getProperty("shardId", "shard-0-0");
            if (!currentShard.equals(targetShard)) {
                return; // Not for us
            }

            plugin.getLogger().info(String.format("[ShardPreload] Pre-loading chunks for %s at (%.2f, %.2f)",
                    playerId, x, z));

            // Store the pending teleport
            pendingTeleports.put(playerId, new PendingTeleport(x, 100, z)); // Y=100 temp, will be adjusted

            // Pre-load chunks on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                World world = Bukkit.getWorld("world");
                if (world != null) {
                    // Load chunk
                    world.getChunkAt((int) x >> 4, (int) z >> 4);
                    plugin.getLogger().info(String.format("[ShardPreload] ✓ Chunks loaded for %s", playerId));
                }
            });

        } catch (Exception e) {
            plugin.getLogger().severe("[ShardPreload] Error handling preload: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PendingTeleport teleport = pendingTeleports.remove(player.getUniqueId());

        if (teleport != null) {
            // This player just transferred! Teleport them to the correct position
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World world = player.getWorld();

                // Get the correct Y coordinate (highest block)
                double y = world.getHighestBlockYAt((int) teleport.x, (int) teleport.z) + 1;

                Location targetLoc = new Location(world, teleport.x, y, teleport.z);
                player.teleport(targetLoc);

                plugin.getLogger().info(String.format("[ShardPreload] ✓ Teleported %s to correct position after transfer",
                        player.getName()));

            }, 5L); // Wait 5 ticks for player to fully load
        }
    }

    private static class PendingTeleport {
        final double x, y, z;

        PendingTeleport(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}