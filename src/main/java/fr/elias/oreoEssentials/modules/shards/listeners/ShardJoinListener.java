package fr.elias.oreoEssentials.modules.shards.listeners;


import fr.elias.oreoEssentials.modules.shards.redis.ShardHandoffManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;


public class ShardJoinListener implements Listener {

    private final Plugin plugin;
    private final ShardHandoffManager handoffManager;

    public ShardJoinListener(Plugin plugin, ShardHandoffManager handoffManager) {
        this.plugin = plugin;
        this.handoffManager = handoffManager;
    }

    @EventHandler(priority = EventPriority.LOWEST) // Run before other plugins
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if this is a shard transfer (data exists in Redis)
        PlayerSnapshot snapshot = handoffManager.loadHandoff(uuid);

        if (snapshot == null) {
            // Normal join, not a transfer
            return;
        }

        // This is a shard transfer - restore player state
        // Schedule for next tick to ensure player is fully loaded
        Bukkit.getScheduler().runTask(plugin, () -> {
            restorePlayerState(player, snapshot);
        });
    }

    /**
     * Restore all player data from snapshot
     */
    private void restorePlayerState(Player player, PlayerSnapshot snapshot) {
        try {
            // 1. Teleport to exact position
            World world = Bukkit.getWorld(snapshot.world);
            if (world == null) {
                plugin.getLogger().severe("World not found for shard transfer: " + snapshot.world);
                handoffManager.releaseLock(player.getUniqueId());
                return;
            }

            Location loc = new Location(world, snapshot.x, snapshot.y, snapshot.z, snapshot.yaw, snapshot.pitch);
            player.teleport(loc);

            // 2. Restore velocity (maintains momentum)
            player.setVelocity(new org.bukkit.util.Vector(snapshot.velX, snapshot.velY, snapshot.velZ));

            // 3. Restore health & food
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.setFoodLevel(snapshot.foodLevel);
            player.setSaturation(snapshot.saturation);
            player.setExhaustion(snapshot.exhaustion);

            // 4. Restore experience
            player.setExp(snapshot.exp);
            player.setLevel(snapshot.level);

            // 5. Restore game mode
            try {
                player.setGameMode(GameMode.valueOf(snapshot.gameMode));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid gamemode in snapshot: " + snapshot.gameMode);
            }

            // 6. Restore flight
            player.setAllowFlight(snapshot.allowFlight);
            if (snapshot.flying && snapshot.allowFlight) {
                player.setFlying(true);
            }

            // 7. Restore speeds (force defaults to fix any freeze bugs)
            player.setFlySpeed(snapshot.flySpeed > 0 ? snapshot.flySpeed : 0.1f);
            player.setWalkSpeed(snapshot.walkSpeed > 0 ? snapshot.walkSpeed : 0.2f);


            // 8. Restore other state
            player.setFireTicks(snapshot.fireTicks);
            player.setRemainingAir(snapshot.remainingAir);
            player.setFallDistance(snapshot.fallDistance);

            // 9. Restore potion effects
            if (snapshot.potionEffects != null && snapshot.potionEffects.length > 0) {
                // Clear existing effects first
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }

                // Add saved effects
                for (PotionEffect effect : snapshot.potionEffects) {
                    player.addPotionEffect(effect);
                }
            }

            // 10. Release lock (transfer complete)
            handoffManager.releaseLock(player.getUniqueId());

            // 11. Log successful transfer (for debugging)
            long transferTime = System.currentTimeMillis() - snapshot.timestamp;
            plugin.getLogger().info(String.format(
                    "Shard transfer complete for %s in %dms (from %s)",
                    player.getName(),
                    transferTime,
                    snapshot.targetShard
            ));

            // Success message (optional, can be disabled in config)
            // player.sendMessage("§aSeamlessly transferred to new area!");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to restore player state for " + player.getName());
            e.printStackTrace();

            // Release lock even on failure
            handoffManager.releaseLock(player.getUniqueId());

            // Fallback: kick player with explanation
            player.kickPlayer("§cShard transfer failed. Please rejoin.");
        }
    }
}