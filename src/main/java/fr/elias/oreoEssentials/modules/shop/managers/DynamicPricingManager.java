package fr.elias.oreoEssentials.modules.shop.managers;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class DynamicPricingManager {

    private static final String ROOT          = "dynamic-pricing.";
    private static final String ENABLED       = ROOT + "enabled";
    private static final String DEMAND_SCALE  = ROOT + "demand-scale";
    private static final String MAX_BUY_MULT  = ROOT + "max-buy-multiplier";
    private static final String MIN_SELL_MULT = ROOT + "min-sell-multiplier";
    private static final String DECAY_INTERVAL= ROOT + "decay-interval-minutes";
    private static final String DECAY_RATE    = ROOT + "decay-rate";
    private static final String SAVE_INTERVAL = ROOT + "save-interval-minutes";
    private static final String SHOW_TREND    = ROOT + "show-trend-in-lore";

    private final ShopModule module;
    private final File dataFile;

    private final Map<String, Long> buyCounts = new HashMap<>();
    private BukkitTask decayTask, saveTask;

    private boolean enabled;
    private double  demandScale;
    private double  maxBuyMultiplier;
    private double  minSellMultiplier;
    private boolean showTrendInLore;

    public DynamicPricingManager(ShopModule module) {
        this.module   = module;
        this.dataFile = new File(module.getPlugin().getDataFolder(), "shop/dynamic_prices.yml");
        load();
        scheduleTasks();
    }


    public boolean isEnabled() { return enabled; }

    public void recordBuy(ShopItem item, int qty) {
        if (!enabled) return;
        buyCounts.merge(key(item), (long) qty, Long::sum);
    }

    public double getBuyMultiplier(ShopItem item) {
        if (!enabled) return 1.0;
        double p = pressure(item);
        return Math.min(1.0 + p * (maxBuyMultiplier - 1.0), maxBuyMultiplier);
    }

    public double getSellMultiplier(ShopItem item) {
        if (!enabled) return 1.0;
        double p = pressure(item);
        return Math.max(1.0 - p * (1.0 - minSellMultiplier), minSellMultiplier);
    }

    public String getTrendLore(ShopItem item) {
        if (!enabled || !showTrendInLore) return null;
        double p   = pressure(item);
        double pct = p * (maxBuyMultiplier - 1.0) * 100.0;
        if (p < 0.05)  return "§7● Market: §anormal";
        if (p < 0.30)  return String.format("§e▲ Demand: §e+%.0f%% §7(buy cost up)", pct);
        if (p < 0.70)  return String.format("§c▲▲ High demand: §c+%.0f%% §7(prices rising)", pct);
        return             String.format("§4▲▲▲ §lExtreme demand: §4+%.0f%% §7(very expensive!)", pct);
    }

    public long getBuyCount(ShopItem item) { return buyCounts.getOrDefault(key(item), 0L); }
    public void resetItem(ShopItem item)   { buyCounts.remove(key(item)); }
    public void resetAll()                 { buyCounts.clear(); }


    public void reload() { cancelTasks(); load(); scheduleTasks(); }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        buyCounts.forEach((k, v) -> yaml.set("counts." + k.replace(".", "_"), v));
        try { yaml.save(dataFile); }
        catch (IOException e) {
            module.getPlugin().getLogger().log(Level.WARNING, "[Shop] Failed to save dynamic_prices.yml", e);
        }
    }

    public void shutdown() { cancelTasks(); save(); }


    private void load() {
        FileConfiguration cfg = module.getShopConfig().getRawConfig();
        enabled           = cfg.getBoolean(ENABLED,       true);
        demandScale       = cfg.getDouble (DEMAND_SCALE,  500.0);
        maxBuyMultiplier  = cfg.getDouble (MAX_BUY_MULT,  3.0);
        minSellMultiplier = cfg.getDouble (MIN_SELL_MULT, 0.2);
        showTrendInLore   = cfg.getBoolean(SHOW_TREND,    true);

        if (demandScale   <= 0) demandScale   = 500.0;
        if (maxBuyMultiplier < 1) maxBuyMultiplier = 1.0;
        if (minSellMultiplier < 0 || minSellMultiplier > 1) minSellMultiplier = 0.2;

        buyCounts.clear();
        if (dataFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            if (yaml.isConfigurationSection("counts")) {
                for (String k : yaml.getConfigurationSection("counts").getKeys(false)) {
                    buyCounts.put(k.replace("_", ":"), yaml.getLong("counts." + k));
                }
            }
        }
    }

    private void scheduleTasks() {
        if (!enabled) return;
        FileConfiguration cfg = module.getShopConfig().getRawConfig();
        long decayMinutes = cfg.getLong(DECAY_INTERVAL, 60L);
        double decayRate  = cfg.getDouble(DECAY_RATE,   0.05);
        long saveMinutes  = cfg.getLong(SAVE_INTERVAL,  5L);

        long decayTicks = decayMinutes * 60L * 20L;
        long saveTicks  = saveMinutes  * 60L * 20L;

        decayTask = module.getPlugin().getServer().getScheduler().runTaskTimer(module.getPlugin(), () -> {
            buyCounts.replaceAll((k, v) -> Math.max(Math.round(v * (1.0 - decayRate)), 0L));
            buyCounts.values().removeIf(v -> v <= 0);
        }, decayTicks, decayTicks);

        saveTask = module.getPlugin().getServer().getScheduler().runTaskTimer(
                module.getPlugin(), this::save, saveTicks, saveTicks);
    }

    private void cancelTasks() {
        if (decayTask != null) { decayTask.cancel(); decayTask = null; }
        if (saveTask  != null) { saveTask.cancel();  saveTask  = null; }
    }

    private double pressure(ShopItem item) {
        return Math.min((double) buyCounts.getOrDefault(key(item), 0L) / demandScale, 1.0);
    }

    private String key(ShopItem item) { return item.getShopId() + ":" + item.getId(); }
}