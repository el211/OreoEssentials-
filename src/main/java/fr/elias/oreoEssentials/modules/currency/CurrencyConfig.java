package fr.elias.oreoEssentials.modules.currency;

import fr.elias.oreoEssentials.modules.currency.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Configuration for the currency system with MiniMessage support
 */
public class CurrencyConfig {

    private final Plugin plugin;
    private final File configFile;
    private FileConfiguration config;

    public CurrencyConfig(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "currency-config.yml");
        load();
    }

    private void load() {
        if (!configFile.exists()) {
            saveDefault();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void saveDefault() {
        try {
            configFile.getParentFile().mkdirs();

            FileConfiguration defaultConfig = new YamlConfiguration();

            defaultConfig.set("currency.enabled", true);
            defaultConfig.set("currency.storage", "json");  // Options: json, mongodb
            defaultConfig.set("currency.default-currency", "money");

            defaultConfig.set("currency.cross-server", false);

            defaultConfig.set("currency.transfer.min-amount", 0.01);
            defaultConfig.set("currency.transfer.max-amount", 1000000.0);
            defaultConfig.set("currency.transfer.cooldown", 0);  // Seconds between transfers (0 = no cooldown)

            defaultConfig.set("messages.prefix", "<gradient:gold:yellow>[Currency]</gradient> ");
            defaultConfig.set("messages.currency-created", "<green>Created currency: <white>{name} {symbol}");
            defaultConfig.set("messages.currency-deleted", "<green>Deleted currency: <white>{id}");
            defaultConfig.set("messages.currency-not-found", "<red>Currency not found: {id}");
            defaultConfig.set("messages.insufficient-balance", "<gradient:red:dark_red>Insufficient balance!</gradient>");
            defaultConfig.set("messages.transfer-sent", "<gradient:green:dark_green>Sent {amount} to <white>{player}</gradient>");
            defaultConfig.set("messages.transfer-received", "<gradient:green:dark_green>Received {amount} from <white>{player}</gradient>");
            defaultConfig.set("messages.balance", "<yellow>{currency}: <green>{amount}");
            defaultConfig.set("messages.balance-all-header", "<gradient:gold:yellow>════════ Your Balances ════════</gradient>");
            defaultConfig.set("messages.balance-all-footer", "<gradient:gold:yellow>═══════════════════════════════</gradient>");
            defaultConfig.set("messages.top-header", "<gradient:gold:yellow>════ {currency} Top ════</gradient>");
            defaultConfig.set("messages.top-footer", "<gradient:gold:yellow>═══════════════════════════════</gradient>");

            defaultConfig.save(configFile);
            plugin.getLogger().info("[Currency] Created currency-config.yml with MiniMessage support");
        } catch (IOException e) {
            plugin.getLogger().severe("[Currency] Failed to create currency-config.yml: " + e.getMessage());
        }
    }

    public void reload() {
        load();
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Currency] Failed to save currency-config.yml: " + e.getMessage());
        }
    }


    public boolean isEnabled() {
        return config.getBoolean("currency.enabled", true);
    }

    public String getStorageType() {
        return config.getString("currency.storage", "json").toLowerCase();
    }

    public String getDefaultCurrency() {
        return config.getString("currency.default-currency", "money");
    }

    /**
     * Returns true if MongoDB storage should be used
     * This uses the main OreoEssentials MongoDB connection if storage=mongodb
     */
    public boolean useMongoStorage() {
        return "mongodb".equalsIgnoreCase(getStorageType());
    }

    /**
     * Returns true if cross-server features should be enabled
     * Requires: storage=mongodb AND cross-server=true
     */
    public boolean isCrossServerEnabled() {
        return config.getBoolean("currency.cross-server", false) && useMongoStorage();
    }

    public double getMinTransferAmount() {
        return config.getDouble("currency.transfer.min-amount", 0.01);
    }

    public double getMaxTransferAmount() {
        return config.getDouble("currency.transfer.max-amount", 1000000.0);
    }

    public int getTransferCooldown() {
        return config.getInt("currency.transfer.cooldown", 0);
    }

    /**
     * Get raw message without processing (for debugging)
     */
    public String getMessageRaw(String key) {
        return config.getString("messages." + key, "<red>Message not found: " + key);
    }

    /**
     * Get a message with MiniMessage formatting and placeholder support
     *
     * Examples:
     * - getMessage("transfer-sent", "{amount}", "💎 100", "{player}", "Nabil")
     * - getMessage("balance", "{currency}", "Gems", "{amount}", "💎 500.00")
     *
     * @param key Message key from config
     * @param placeholders Pairs of placeholder and value (e.g., "{player}", "EliasFazel", "{amount}", "100")
     * @return Formatted message with MiniMessage colors and replaced placeholders
     */
    public String getMessage(String key, Object... placeholders) {
        String message = getMessageRaw(key);

        message = applyPlaceholders(message, placeholders);

        message = ColorUtil.color(message);

        return message;
    }

    /**
     * Get a message with PlaceholderAPI support
     *
     * @param player Player for PlaceholderAPI placeholders (%player_name%, etc.)
     * @param key Message key
     * @param placeholders Custom placeholders
     * @return Formatted message with PlaceholderAPI + custom placeholders + MiniMessage
     */
    public String getMessage(Player player, String key, Object... placeholders) {
        String message = getMessage(key, placeholders);

        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                message = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, message);
            } catch (Exception e) {
            }
        }

        return message;
    }

    /**
     * Get prefix with MiniMessage formatting
     */
    public String getPrefix() {
        return getMessage("prefix");
    }

    public FileConfiguration getConfig() {
        return config;
    }


    /**
     * Replace placeholders in a message
     * Accepts pairs: "{placeholder}", "value", "{another}", "value2"
     */
    private String applyPlaceholders(String message, Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return message;
        }

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);
            message = message.replace(placeholder, value);
        }

        return message;
    }
}