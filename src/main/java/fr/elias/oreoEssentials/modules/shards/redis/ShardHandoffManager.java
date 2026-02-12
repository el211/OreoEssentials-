package fr.elias.oreoEssentials.modules.shards.redis;

import fr.elias.oreoEssentials.modules.shards.listeners.PlayerSnapshot;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class ShardHandoffManager {

    private final JedisPool jedisPool;
    private final Gson gson;
    private static final int HANDOFF_TTL_SECONDS = 5;
    private static final String HANDOFF_PREFIX = "oreo:handoff:";
    private static final String COOLDOWN_PREFIX = "oreo:cooldown:";
    private static final String LOCK_PREFIX = "oreo:lock:";

    public ShardHandoffManager(String host, int port, String password) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);

        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        this.gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .create();
    }


    public boolean saveHandoff(Player player, String targetShard) {
        try (Jedis redis = jedisPool.getResource()) {
            UUID uuid = player.getUniqueId();
            String key = HANDOFF_PREFIX + uuid;

            // Create snapshot
            PlayerSnapshot snapshot = new PlayerSnapshot();
            snapshot.uuid = uuid.toString();
            snapshot.name = player.getName();
            snapshot.targetShard = targetShard;

            // Position
            snapshot.world = player.getWorld().getName();
            snapshot.x = player.getLocation().getX();
            snapshot.y = player.getLocation().getY();
            snapshot.z = player.getLocation().getZ();
            snapshot.yaw = player.getLocation().getYaw();
            snapshot.pitch = player.getLocation().getPitch();

            // Velocity (to restore momentum)
            snapshot.velX = player.getVelocity().getX();
            snapshot.velY = player.getVelocity().getY();
            snapshot.velZ = player.getVelocity().getZ();

            // Stats
            snapshot.health = player.getHealth();
            snapshot.foodLevel = player.getFoodLevel();
            snapshot.saturation = player.getSaturation();
            snapshot.exhaustion = player.getExhaustion();
            snapshot.exp = player.getExp();
            snapshot.level = player.getLevel();
            snapshot.gameMode = player.getGameMode().name();
            snapshot.flying = player.isFlying();
            snapshot.allowFlight = player.getAllowFlight();
            snapshot.flySpeed = player.getFlySpeed();
            snapshot.walkSpeed = player.getWalkSpeed();

            // Fire ticks
            snapshot.fireTicks = player.getFireTicks();
            snapshot.remainingAir = player.getRemainingAir();

            // Fall distance (important for no-damage transfers)
            snapshot.fallDistance = player.getFallDistance();

            // Potion effects
            snapshot.potionEffects = player.getActivePotionEffects().toArray(new PotionEffect[0]);

            // Timestamp for debugging
            snapshot.timestamp = System.currentTimeMillis();

            // Serialize and save with TTL
            String json = gson.toJson(snapshot);
            redis.setex(key, HANDOFF_TTL_SECONDS, json);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public PlayerSnapshot loadHandoff(UUID uuid) {
        try (Jedis redis = jedisPool.getResource()) {
            String key = HANDOFF_PREFIX + uuid;
            String json = redis.get(key);

            if (json == null) {
                return null;
            }

            redis.del(key);

            return gson.fromJson(json, PlayerSnapshot.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public void setCooldown(UUID uuid, long durationMs) {
        try (Jedis redis = jedisPool.getResource()) {
            String key = COOLDOWN_PREFIX + uuid;
            int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(durationMs);
            redis.setex(key, seconds, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public boolean isOnCooldown(UUID uuid) {
        try (Jedis redis = jedisPool.getResource()) {
            String key = COOLDOWN_PREFIX + uuid;
            return redis.exists(key);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public boolean acquireLock(UUID uuid) {
        try (Jedis redis = jedisPool.getResource()) {
            String key = LOCK_PREFIX + uuid;
            Long result = redis.setnx(key, String.valueOf(System.currentTimeMillis()));
            if (result == 1) {
                redis.expire(key, 10);
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Release anti-dupe lock
     */
    public void releaseLock(UUID uuid) {
        try (Jedis redis = jedisPool.getResource()) {
            String key = LOCK_PREFIX + uuid;
            redis.del(key);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if lock exists (player is mid-transfer)
     */
    public boolean isLocked(UUID uuid) {
        try (Jedis redis = jedisPool.getResource()) {
            String key = LOCK_PREFIX + uuid;
            return redis.exists(key);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Clean shutdown
     */
    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    /**
     * Test connection
     */
    public boolean testConnection() {
        try (Jedis redis = jedisPool.getResource()) {
            return "PONG".equals(redis.ping());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
