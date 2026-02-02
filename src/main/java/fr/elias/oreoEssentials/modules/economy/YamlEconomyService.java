package fr.elias.oreoEssentials.modules.economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

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
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private String path(UUID u) { return "balances." + u.toString(); }

    @Override public double getBalance(UUID player) {
        return cfg.getDouble(path(player), 0.0);
    }

    @Override public boolean deposit(UUID player, double amount) {
        if (amount <= 0) return false;
        double bal = getBalance(player) + amount;
        cfg.set(path(player), round(bal));
        save();
        return true;
    }

    @Override public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) return false;
        double bal = getBalance(player);
        if (bal < amount) return false;
        cfg.set(path(player), round(bal - amount));
        save();
        return true;
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
