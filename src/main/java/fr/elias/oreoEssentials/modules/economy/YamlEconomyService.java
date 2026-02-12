package fr.elias.oreoEssentials.modules.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class YamlEconomyService implements EconomyService {
    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;

    public YamlEconomyService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        load();
    }

    private void load() {
        try {
            if (!file.exists()) file.getParentFile().mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (IOException ignored) {}
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String path(UUID u) {
        return "balances." + u.toString();
    }

    @Override
    public double getBalance(UUID player) {
        return cfg.getDouble(path(player), 0.0);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) return false;
        double bal = getBalance(player) + amount;
        cfg.set(path(player), round(bal));
        save();
        return true;
    }

    @Override
    public List<TopEntry> topBalances(int limit) {
        if (!cfg.isConfigurationSection("balances")) {
            return List.of();
        }

        Map<String, Object> map = cfg.getConfigurationSection("balances").getValues(false);

        return map.entrySet().stream()
                .map(e -> {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(e.getKey());
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }

                    double bal = 0.0;
                    Object v = e.getValue();
                    if (v instanceof Number n) {
                        bal = n.doubleValue();
                    } else {
                        try {
                            bal = Double.parseDouble(String.valueOf(v));
                        } catch (Exception ignored) {}
                    }

                    String name = resolveName(uuid);
                    return new TopEntry(uuid, name, bal);
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

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) return false;
        double bal = getBalance(player);
        if (bal < amount) return false;
        cfg.set(path(player), round(bal - amount));
        save();
        return true;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}