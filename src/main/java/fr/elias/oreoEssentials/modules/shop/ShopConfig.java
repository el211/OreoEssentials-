package fr.elias.oreoEssentials.modules.shop;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


public final class ShopConfig {

    private final OreoEssentials plugin;
    private final File shopFolder;

    private FileConfiguration config;
    private FileConfiguration messages;
    private String            language;

    public ShopConfig(OreoEssentials plugin) {
        this.plugin     = plugin;
        this.shopFolder = new File(plugin.getDataFolder(), "shop");
        reload();
    }


    public void reload() {
        shopFolder.mkdirs();
        this.config   = loadOrCreate("shop/config.yml");
        this.messages = loadOrCreate("shop/messages.yml");
        this.language = config.getString("language", "en");
    }

    private FileConfiguration loadOrCreate(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            try (InputStream is = plugin.getResource(resourcePath)) {
                if (is != null) {
                    plugin.saveResource(resourcePath, false);
                } else {
                    file.getParentFile().mkdirs();
                    try { file.createNewFile(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Shop] Could not create " + resourcePath + ": " + e.getMessage());
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(is, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {}
        return cfg;
    }


    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public long   getTransactionCooldown()      { return config.getLong("anti-dupe.transaction-cooldown-ms", 300L); }
    public boolean isAntiDupeEnabled()          { return config.getBoolean("anti-dupe.enabled", true); }
    public boolean isVerifyInventory()          { return config.getBoolean("anti-dupe.verify-inventory", true); }
    public boolean isLogSuspicious()            { return config.getBoolean("anti-dupe.log-suspicious", true); }
    public int    getMaxTransactionsPerSecond() { return config.getInt("anti-dupe.max-transactions-per-second", 5); }
    public boolean isLockDuringTransaction()    { return config.getBoolean("anti-dupe.lock-during-transaction", true); }



    public String getMainMenuTitle() {
        return config.getString("main-menu.title", "&8&lSHOP");
    }

    public int getMainMenuRows() {
        return config.getInt("main-menu.rows", 6);
    }


    public String getCurrencySymbol() {
        return config.getString("economy.currency-symbol", "$");
    }



    public boolean isAmountSelectionEnabled() {
        return config.getBoolean("amount-selection.enabled", true);
    }



    public boolean isLoggingEnabled() {
        return config.getBoolean("logging.enabled", true);
    }

    public String getLogFile() {
        return config.getString("logging.log-file", "shop/transactions.log");
    }

    public String getLogFormat() {
        return config.getString("logging.log-format",
                "[{date}] {player} {action} x{amount} {item} for {price} ({economy})");
    }



    public int getPreviousPageSlot() { return config.getInt("gui.previous-page-slot", 45); }
    public int getNextPageSlot()     { return config.getInt("gui.next-page-slot", 53); }
    public int getBackButtonSlot()   { return config.getInt("gui.back-button-slot", 49); }


    public FileConfiguration getRawConfig()    { return config; }
    public FileConfiguration getMessagesConfig() { return messages; }


    public String getRawMessage(String key, String fallback) {
        String msg = messages.getString(language + "." + key, null);
        if (msg == null && !"en".equals(language)) {
            msg = messages.getString("en." + key, null);
        }
        return msg != null ? msg : fallback;
    }


    public String getMessage(String key) {
        String prefix = messages.getString(language + ".prefix",
                messages.getString("en.prefix", "&8[&6Shop&8] &r"));
        String msg = messages.getString(language + "." + key, null);
        if (msg == null && !"en".equals(language)) {
            msg = messages.getString("en." + key, null);
        }
        if (msg == null) msg = "&cMissing message: " + key;
        return msg.replace("{prefix}", prefix);
    }


    public File getShopsFolder() {
        return new File(shopFolder, "shops");
    }
}