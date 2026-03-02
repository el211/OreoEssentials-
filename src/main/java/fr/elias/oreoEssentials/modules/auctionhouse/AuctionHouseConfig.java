package fr.elias.oreoEssentials.modules.auctionhouse;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;


public final class AuctionHouseConfig {

    private final File folder;

    private boolean enabled;
    private String storageType;
    private String mongoCollection;

    private double minPrice;
    private double maxPrice;
    private int maxListingsPerPlayer;
    private long defaultDurationHours;
    private long maxDurationHours;
    private double listingFeePercent;
    private double listingFeeFlat;
    private double taxPercent;

    private boolean discordEnabled;
    private String discordWebhookUrl;
    private String discordBotName;
    private String discordBotAvatar;

    private YamlConfiguration messages;

    private YamlConfiguration categories;

    private YamlConfiguration gui;

    private final Set<AuctionCategory> enabledCategories = EnumSet.noneOf(AuctionCategory.class);

    public AuctionHouseConfig(OreoEssentials plugin) {
        this.folder = new File(plugin.getDataFolder(), "auctionhouse");
        if (!folder.exists()) folder.mkdirs();

        saveDefault("config.yml");
        saveDefault("categories.yml");
        saveDefault("messages.yml");
        saveDefault("gui.yml");

        reload();
    }

    public void reload() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        messages   = YamlConfiguration.loadConfiguration(new File(folder, "messages.yml"));
        categories = YamlConfiguration.loadConfiguration(new File(folder, "categories.yml"));
        gui        = YamlConfiguration.loadConfiguration(new File(folder, "gui.yml"));

        enabled            = cfg.getBoolean("enabled", true);
        storageType        = cfg.getString("storage.type", "auto").toLowerCase(Locale.ROOT);
        mongoCollection    = cfg.getString("storage.mongodb.collection", "oreo_auctions");

        minPrice           = cfg.getDouble("listings.min-price", 1.0);
        maxPrice           = cfg.getDouble("listings.max-price", 1_000_000_000.0);
        maxListingsPerPlayer = cfg.getInt("listings.max-per-player", 10);
        defaultDurationHours = cfg.getLong("listings.default-duration-hours", 24);
        maxDurationHours   = cfg.getLong("listings.max-duration-hours", 168);
        listingFeePercent  = cfg.getDouble("fees.listing-percent", 0.0);
        listingFeeFlat     = cfg.getDouble("fees.listing-flat", 0.0);
        taxPercent         = cfg.getDouble("fees.tax-percent", 0.0);

        discordEnabled     = cfg.getBoolean("discord.enabled", false);
        discordWebhookUrl  = cfg.getString("discord.webhook-url", "");
        discordBotName     = cfg.getString("discord.bot-name", "OreoAuctions");
        discordBotAvatar   = cfg.getString("discord.bot-avatar", "");

        enabledCategories.clear();
        enabledCategories.add(AuctionCategory.ALL);
        for (AuctionCategory cat : AuctionCategory.values()) {
            if (cat == AuctionCategory.ALL) continue;
            if (categories.getBoolean(cat.name().toLowerCase() + ".enabled", true)) {
                enabledCategories.add(cat);
            }
        }
    }


    public boolean   enabled()               { return enabled; }
    public String    storageType()           { return storageType; }
    public String    mongoCollection()       { return mongoCollection; }
    public double    minPrice()              { return minPrice; }
    public double    maxPrice()              { return maxPrice; }
    public int       maxListingsPerPlayer()  { return maxListingsPerPlayer; }
    public long      defaultDurationHours()  { return defaultDurationHours; }
    public long      maxDurationHours()      { return maxDurationHours; }
    public double    listingFeePercent()     { return listingFeePercent; }
    public double    listingFeeFlat()        { return listingFeeFlat; }
    public double    taxPercent()            { return taxPercent; }
    public boolean   discordEnabled()        { return discordEnabled; }
    public String    discordWebhookUrl()     { return discordWebhookUrl; }
    public String    discordBotName()        { return discordBotName; }
    public String    discordBotAvatar()      { return discordBotAvatar; }
    public File      folder()                { return folder; }

    public YamlConfiguration getCategories() { return categories; }

    public YamlConfiguration getGui() { return gui; }

    /**
     * Retourne le titre brut (codes & non traduits) d'un menu depuis gui.yml.
     * Supporte les placeholders {category}, {query} — à remplacer avant d'appeler
     * ChatColor.translateAlternateColorCodes côté GUI.
     */
    public String guiTitleRaw(String guiKey) {
        return gui.getString(guiKey + ".title", "Auction House");
    }

    /** Retourne le Material de la bordure d'un menu, ou {@code fallback} si invalide. */
    public Material guiBorder(String guiKey, Material fallback) {
        String name = gui.getString(guiKey + ".border", "");
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception e) { return fallback; }
    }

    /** Retourne le slot brut (0-53) d'un bouton depuis gui.yml, ou {@code fallback}. */
    public int guiSlot(String guiKey, String btnKey, int fallback) {
        return gui.getInt(guiKey + ".buttons." + btnKey + ".slot", fallback);
    }

    /** Retourne le Material d'un bouton depuis gui.yml, ou {@code fallback} si invalide. */
    public Material guiMaterial(String guiKey, String btnKey, Material fallback) {
        String name = gui.getString(guiKey + ".buttons." + btnKey + ".material", "");
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception e) { return fallback; }
    }

    /**
     * Retourne le nom brut (codes & non traduits) d'un bouton depuis gui.yml.
     * Supporte les placeholders {count}, {page} — à remplacer avant d'appeler
     * BrowseGUI.named() qui se charge de la traduction des couleurs.
     */
    public String guiNameRaw(String guiKey, String btnKey, String fallback) {
        return gui.getString(guiKey + ".buttons." + btnKey + ".name", fallback);
    }

    public boolean isCategoryEnabled(AuctionCategory cat) {
        return enabledCategories.contains(cat);
    }

    public boolean hasCategoryPermission(org.bukkit.entity.Player p, AuctionCategory cat) {
        return p.hasPermission("oreo.ah.bypass")
                || p.hasPermission(cat.getPermission())
                || p.hasPermission("oreo.ah.category.*");
    }

    public String getMessage(String key) {
        String raw = messages.getString(key, "&cMissing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String msg = getMessage(key);
        for (var e : placeholders.entrySet()) {
            msg = msg.replace("%" + e.getKey() + "%", e.getValue());
        }
        return msg;
    }


    private void saveDefault(String name) {
        File target = new File(folder, name);
        if (target.exists()) return;
        try (InputStream in = getClass().getResourceAsStream("/auctionhouse/" + name)) {
            if (in != null) {
                Files.copy(in, target.toPath());
            } else {
                target.createNewFile();
            }
        } catch (Exception ignored) {}
    }
}