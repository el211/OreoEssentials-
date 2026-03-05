package fr.elias.oreoEssentials.modules.orders;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;

/**
 * Loads all Orders module config files from plugins/OreoEssentials/orders/.
 * RabbitMQ credentials are read from the core config.yml (not duplicated here).
 */
public final class OrdersConfig {

    private final File folder;

    private boolean enabled;
    private boolean debug;
    private String  storageType;          // "mongodb" | "sqlite"
    private String  sqliteFile;
    private String  mongoCollection;

    private boolean crossServerEnabled;
    private String  crossServerExchange;

    private int    maxActiveOrdersPerPlayer;
    private int    maxQtyPerOrder;
    private double minUnitPrice;

    private boolean allowVault;
    private boolean allowCustomCurrencies;
    private String  defaultCurrency;

    private int liveRefreshDebounceTicks;

    private boolean feesEnabled;
    private double  createFeePercent;
    private double  fillFeePercent;

    private YamlConfiguration lang;
    private YamlConfiguration gui;
    private String langCode; // e.g. "en", "fr"

    public OrdersConfig(OreoEssentials plugin) {
        this.folder = new File(plugin.getDataFolder(), "orders");
        if (!folder.exists()) folder.mkdirs();

        saveDefault("settings.yml");
        saveDefault("lang_en.yml");
        saveDefault("lang_fr.yml");
        saveDefault("gui.yml");

        reload();
    }

    public void reload() {
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(new File(folder, "settings.yml"));
        gui  = YamlConfiguration.loadConfiguration(new File(folder, "gui.yml"));

        langCode = settings.getString("orders.lang", "en").toLowerCase(Locale.ROOT).trim();
        File langFile = new File(folder, "lang_" + langCode + ".yml");
        if (!langFile.exists()) {
            // Unknown language code — save the bundled file if we have it, else fall back to en
            saveDefault("lang_" + langCode + ".yml");
            if (!langFile.exists()) {
                langFile = new File(folder, "lang_en.yml");
            }
        }
        lang = YamlConfiguration.loadConfiguration(langFile);

        enabled     = settings.getBoolean("orders.enabled", true);
        debug       = settings.getBoolean("orders.debug", false);
        storageType = settings.getString("orders.storage.type", "sqlite").toLowerCase(Locale.ROOT);
        sqliteFile  = settings.getString("orders.storage.sqlite.file", "orders.db");
        mongoCollection = settings.getString("orders.storage.mongodb.collection", "oreo_orders");

        crossServerEnabled  = settings.getBoolean("orders.cross_server.enabled", false);
        crossServerExchange = settings.getString("orders.cross_server.exchange", "oreo.orders");

        maxActiveOrdersPerPlayer = settings.getInt("orders.limits.max_active_orders_per_player", 10);
        maxQtyPerOrder           = settings.getInt("orders.limits.max_quantity_per_order", 100000);
        minUnitPrice             = settings.getDouble("orders.limits.min_unit_price", 0.01);

        allowVault             = settings.getBoolean("orders.economy.allow_vault", true);
        allowCustomCurrencies  = settings.getBoolean("orders.economy.allow_custom_currencies", true);
        defaultCurrency        = settings.getString("orders.economy.default_currency", "money");

        liveRefreshDebounceTicks = settings.getInt("orders.ui.live_refresh_debounce_ticks", 10);

        feesEnabled      = settings.getBoolean("orders.fees.enabled", false);
        createFeePercent = settings.getDouble("orders.fees.create_fee_percent", 0.0);
        fillFeePercent   = settings.getDouble("orders.fees.fill_fee_percent", 0.0);
    }


    public String msg(String key) {
        String raw = lang.getString(key, "&cMissing lang key: " + key);
        return c(raw);
    }

    public String msg(String key, Map<String, String> placeholders) {
        String m = msg(key);
        for (var e : placeholders.entrySet()) {
            m = m.replace("{" + e.getKey() + "}", e.getValue());
        }
        return m;
    }


    public String guiTitle(String guiKey, String fallback) {
        return c(gui.getString(guiKey + ".title", fallback));
    }

    public int guiSlot(String guiKey, String btnKey, int fallback) {
        return gui.getInt(guiKey + ".buttons." + btnKey + ".slot", fallback);
    }

    public Material guiMaterial(String guiKey, String btnKey, Material fallback) {
        String name = gui.getString(guiKey + ".buttons." + btnKey + ".material", "");
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return fallback; }
    }

    public String guiNameRaw(String guiKey, String btnKey, String fallback) {
        return gui.getString(guiKey + ".buttons." + btnKey + ".name", fallback);
    }

    public Material guiBorder(String guiKey, Material fallback) {
        String name = gui.getString(guiKey + ".border", "");
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return fallback; }
    }


    public boolean enabled()                   { return enabled; }
    public boolean debug()                     { return debug; }
    public String  storageType()               { return storageType; }
    public String  sqliteFile()                { return sqliteFile; }
    public String  mongoCollection()           { return mongoCollection; }
    public boolean crossServerEnabled()        { return crossServerEnabled; }
    public String  crossServerExchange()       { return crossServerExchange; }
    public int     maxActiveOrdersPerPlayer()  { return maxActiveOrdersPerPlayer; }
    public int     maxQtyPerOrder()            { return maxQtyPerOrder; }
    public double  minUnitPrice()              { return minUnitPrice; }
    public boolean allowVault()                { return allowVault; }
    public boolean allowCustomCurrencies()     { return allowCustomCurrencies; }
    public String  defaultCurrency()           { return defaultCurrency; }
    public int     liveRefreshDebounceTicks()  { return liveRefreshDebounceTicks; }
    public boolean feesEnabled()               { return feesEnabled; }
    public double  createFeePercent()          { return createFeePercent; }
    public double  fillFeePercent()            { return fillFeePercent; }
    public File    folder()                    { return folder; }
    public String  langCode()                  { return langCode; }
    public YamlConfiguration getLang()         { return lang; }
    public YamlConfiguration getGui()          { return gui; }


    private void saveDefault(String name) {
        File target = new File(folder, name);
        if (target.exists()) return;
        try (InputStream in = getClass().getResourceAsStream("/orders/" + name)) {
            if (in != null) {
                Files.copy(in, target.toPath());
            }
            // If no bundled resource for this name, silently skip (e.g. custom language code)
        } catch (Exception ignored) {}
    }

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
