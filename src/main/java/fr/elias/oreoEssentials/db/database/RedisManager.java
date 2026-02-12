package fr.elias.oreoEssentials.db.database;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.UUID;

public class RedisManager {
    private JedisPool jedisPool;
    private final boolean enabled;

    private final String host;
    private final int port;
    private final String password;

    public RedisManager(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.enabled = (host != null && !host.isEmpty());
    }

    public boolean connect() {
        if (!enabled) return false;

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);

            if (password != null && !password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, 2000);
            }

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Double getBalance(UUID playerUUID) {
        if (!enabled || jedisPool == null) return null;

        try (Jedis jedis = jedisPool.getResource()) {
            String key = "balance:" + playerUUID.toString();
            String value = jedis.get(key);
            return value != null ? Double.parseDouble(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void setBalance(UUID playerUUID, double balance) {
        if (!enabled || jedisPool == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            String key = "balance:" + playerUUID.toString();
            jedis.setex(key, 600, String.valueOf(balance));
        } catch (Exception ignored) {}
    }

    public void giveBalance(UUID playerUUID, double amount) {
        if (!enabled || jedisPool == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            String key = "balance:" + playerUUID.toString();
            String current = jedis.get(key);
            double newBalance = (current != null ? Double.parseDouble(current) : 100.0) + amount;
            jedis.setex(key, 600, String.valueOf(newBalance));
        } catch (Exception ignored) {}
    }

    public void takeBalance(UUID playerUUID, double amount) {
        if (!enabled || jedisPool == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            String key = "balance:" + playerUUID.toString();
            String current = jedis.get(key);
            double newBalance = Math.max(0, (current != null ? Double.parseDouble(current) : 100.0) - amount);
            jedis.setex(key, 600, String.valueOf(newBalance));
        } catch (Exception ignored) {}
    }

    public void deleteBalance(UUID playerUUID) {
        if (!enabled || jedisPool == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            String key = "balance:" + playerUUID.toString();
            jedis.del(key);
        } catch (Exception ignored) {}
    }

    public void clearCache() {
        if (!enabled || jedisPool == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        } catch (Exception ignored) {}
    }

    public void shutdown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}