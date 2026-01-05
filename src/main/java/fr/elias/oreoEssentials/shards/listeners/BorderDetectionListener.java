package fr.elias.oreoEssentials.shards.listeners;

import fr.elias.oreoEssentials.shards.ShardManager;
import fr.elias.oreoEssentials.shards.config.ShardConfig;
import fr.elias.oreoEssentials.shards.redis.ShardHandoffManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects when players approach shard borders and triggers seamless transfers
 */
public class BorderDetectionListener implements Listener {

    private final ShardManager shardManager;
    private final ShardHandoffManager handoffManager;
    private final ShardConfig config;

    // Track last damage time for combat detection
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private static final long COMBAT_COOLDOWN_MS = 5000; // 5 seconds

    public BorderDetectionListener(ShardManager shardManager, ShardHandoffManager handoffManager, ShardConfig config) {
        this.shardManager = shardManager;
        this.handoffManager = handoffManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check on actual position change (not just head movement)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Skip if player is already being transferred
        if (handoffManager.isLocked(uuid)) {
            return;
        }

        // Skip if on cooldown (prevent rapid back-and-forth)
        if (handoffManager.isOnCooldown(uuid)) {
            return;
        }

        // Skip if in unsafe state
        if (isUnsafeToTransfer(player)) {
            return;
        }

        // Get current shard info
        String currentWorld = player.getWorld().getName();
        ShardConfig.WorldConfig worldConfig = config.getWorld(currentWorld);

        if (worldConfig == null || !worldConfig.enabled) {
            return;
        }

        // Check if approaching border
        double x = event.getTo().getX();
        double z = event.getTo().getZ();

        String targetShard = shardManager.getTargetShardAtBorder(
                currentWorld,
                x,
                z,
                worldConfig.transferBuffer
        );

        if (targetShard != null && !targetShard.equals(shardManager.getCurrentShardId())) {
            // Player is approaching a border - initiate transfer
            initiateTransfer(player, targetShard);
        }
    }

    /**
     * Track when player takes damage (for combat detection)
     * Call this from EntityDamageEvent listener
     */
    public void onPlayerDamage(UUID uuid) {
        lastDamageTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * Check if player is in a state where transfer would be unsafe
     */
    private boolean isUnsafeToTransfer(Player player) {
        UUID uuid = player.getUniqueId();

        // Don't transfer during recent combat
        if (lastDamageTime.containsKey(uuid)) {
            long timeSinceLastDamage = System.currentTimeMillis() - lastDamageTime.get(uuid);
            if (timeSinceLastDamage < COMBAT_COOLDOWN_MS) {
                return true;
            }
        }

        // Alternative: Check noDamageTicks (built-in immunity frames)
        if (player.getNoDamageTicks() > 0) {
            return true;
        }

        // Don't transfer while falling fast
        if (player.getVelocity().getY() < -0.5) {
            return true;
        }

        // Don't transfer if dead or very low health
        if (player.isDead() || player.getHealth() <= 0) {
            return true;
        }

        // Don't transfer in vehicle
        if (player.isInsideVehicle()) {
            return true;
        }

        // Don't transfer while gliding
        if (player.isGliding()) {
            return true;
        }

        return false;
    }

    /**
     * Initiate the seamless transfer
     */
    private void initiateTransfer(Player player, String targetShard) {
        UUID uuid = player.getUniqueId();

        // Acquire lock to prevent duplication
        if (!handoffManager.acquireLock(uuid)) {
            // Another transfer already in progress
            return;
        }

        try {
            // 1. Freeze player
            freezePlayer(player);

            // 2. Save snapshot to Redis
            boolean saved = handoffManager.saveHandoff(player, targetShard);

            if (!saved) {
                // Failed to save - abort
                unfreezePlayer(player);
                handoffManager.releaseLock(uuid);
                return;
            }

            // 3. Send to target shard via Velocity
            shardManager.transferPlayerToShard(player, targetShard);

            // 4. Set cooldown (prevents immediate return)
            handoffManager.setCooldown(uuid, config.getTransferCooldown());

            // Note: Lock is released on target server after successful restore

        } catch (Exception e) {
            // Something went wrong - cleanup
            unfreezePlayer(player);
            handoffManager.releaseLock(uuid);
            e.printStackTrace();
        }
    }

    /**
     * Freeze player during transfer (prevents movement artifacts)
     */
    private void freezePlayer(Player player) {
        player.setVelocity(new Vector(0, 0, 0));
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
    }

    /**
     * Unfreeze player (used if transfer fails)
     */
    private void unfreezePlayer(Player player) {
        player.setWalkSpeed(0.2f); // Default walk speed
        player.setFlySpeed(0.1f);  // Default fly speed
    }

    /**
     * Cleanup damage tracking on player quit
     */
    public void cleanupPlayer(UUID uuid) {
        lastDamageTime.remove(uuid);
    }
}