package fr.elias.oreoEssentials.db.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JsonEconomyDatabase implements PlayerEconomyDatabase {

    private final JavaPlugin plugin;
    private final RedisManager redis;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Object lock = new Object();
    private File file;

    private Map<String, Account> accounts = new ConcurrentHashMap<>();

    private static final double STARTING_BALANCE = 100.0;
    private static final double MIN_BALANCE = 0.0;
    private final double MAX_BALANCE;
    private static final boolean ALLOW_NEGATIVE = false;

    public JsonEconomyDatabase(JavaPlugin plugin, RedisManager redis) {
        this.plugin = plugin;
        this.redis = redis;
        this.MAX_BALANCE = plugin.getConfig().getDouble("economy.max-balance", 1_000_000_000.0);

    }

    @Override
    public boolean connect(String url, String user, String password) {
        try {
            this.file = new File(plugin.getDataFolder(), "economy.json");
            load();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to init JSON economy.");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void giveBalance(UUID playerUUID, String name, double amount) {
        synchronized (lock) {
            double cur = getBalanceInternal(playerUUID);
            setBalanceInternal(playerUUID, name, cur + amount);
        }
    }

    @Override
    public void takeBalance(UUID playerUUID, String name, double amount) {
        synchronized (lock) {
            double cur = getBalanceInternal(playerUUID);
            setBalanceInternal(playerUUID, name, cur - amount);
        }
    }

    @Override
    public void setBalance(UUID playerUUID, String name, double amount) {
        synchronized (lock) {
            setBalanceInternal(playerUUID, name, amount);
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        Double cached = redis.getBalance(playerUUID);
        if (cached != null) return cached;
        synchronized (lock) {
            double v = getBalanceInternal(playerUUID);
            redis.setBalance(playerUUID, v);
            return v;
        }
    }

    @Override
    public double getOrCreateBalance(UUID playerUUID, String name) {
        Double cached = redis.getBalance(playerUUID);
        if (cached != null) return cached;
        synchronized (lock) {
            Account acc = accounts.get(playerUUID.toString());
            if (acc == null) {
                acc = new Account(name, STARTING_BALANCE);
                accounts.put(playerUUID.toString(), acc);
                save();
            } else if (name != null && (acc.name == null || !acc.name.equals(name))) {
                acc.name = name;
                save();
            }
            redis.setBalance(playerUUID, acc.balance);
            return acc.balance;
        }
    }

    @Override
    public void populateCache(OfflinePlayerCache cache) {
        synchronized (lock) {
            for (Map.Entry<String, Account> e : accounts.entrySet()) {
                try {
                    UUID id = UUID.fromString(e.getKey());
                    String name = e.getValue().name;
                    if (name != null) cache.add(name, id);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void clearCache() {
        redis.clearCache();
    }

    @Override
    public void close() {
        synchronized (lock) { save(); }
    }

    private double getBalanceInternal(UUID id) {
        Account acc = accounts.get(id.toString());
        if (acc == null) {
            acc = new Account(null, STARTING_BALANCE);
            accounts.put(id.toString(), acc);
            save();
        }
        return acc.balance;
    }

    private void setBalanceInternal(UUID id, String name, double amount) {
        Account acc = accounts.get(id.toString());
        if (acc == null) {
            acc = new Account(name, STARTING_BALANCE);
            accounts.put(id.toString(), acc);
        }
        if (name != null) acc.name = name;
        acc.balance = clamp(amount, MIN_BALANCE, MAX_BALANCE, ALLOW_NEGATIVE);
        save();
        redis.setBalance(id, acc.balance);
    }

    private static double clamp(double value, double min, double max, boolean allowNegative) {
        double v = value;
        if (!allowNegative) v = Math.max(min, v);
        return Math.min(max, v);
    }

    private void load() {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            if (!file.exists()) { save(); return; }
            try (FileReader r = new FileReader(file)) {
                Type t = new TypeToken<Map<String, Account>>(){}.getType();
                Map<String, Account> read = gson.fromJson(r, t);
                if (read != null) accounts.putAll(read);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try (FileWriter w = new FileWriter(file)) {
            gson.toJson(accounts, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class Account {
        String name;
        double balance;
        Account() {}
        Account(String name, double balance) { this.name = name; this.balance = balance; }
    }
}