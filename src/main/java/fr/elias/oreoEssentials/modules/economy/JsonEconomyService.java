package fr.elias.oreoEssentials.modules.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class JsonEconomyService implements EconomyService {
    private final Plugin plugin;
    private final File file;
    private final Gson gson;
    private Map<String, Double> balances;

    public JsonEconomyService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.balances = new HashMap<>();
        load();
    }

    private void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                balances = new HashMap<>();
                save();
                return;
            }

            try (Reader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, Double>>(){}.getType();
                Map<String, Double> loaded = gson.fromJson(reader, type);
                balances = (loaded != null) ? loaded : new HashMap<>();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Economy] Failed to load balances.json: " + e.getMessage());
            balances = new HashMap<>();
        }
    }

    public void save() {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(balances, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("[Economy] Failed to save balances.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public double getBalance(UUID player) {
        return balances.getOrDefault(player.toString(), 0.0);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) return false;

        double currentBalance = getBalance(player);
        double newBalance = round(currentBalance + amount);
        balances.put(player.toString(), newBalance);
        save();
        return true;
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) return false;

        double currentBalance = getBalance(player);
        if (currentBalance < amount) return false;

        double newBalance = round(currentBalance - amount);
        balances.put(player.toString(), newBalance);
        save();
        return true;
    }

    @Override
    public List<TopEntry> topBalances(int limit) {
        return balances.entrySet().stream()
                .map(entry -> {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(entry.getKey());
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[Economy] Invalid UUID in balances.json: " + entry.getKey());
                        return null;
                    }

                    double balance = entry.getValue();
                    String name = resolveName(uuid);
                    return new TopEntry(uuid, name, balance);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(TopEntry::balance).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String resolveName(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String name = p.getName();
        return (name != null && !name.isBlank()) ? name : uuid.toString().substring(0, 8);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}