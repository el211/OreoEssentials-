package fr.elias.oreoEssentials.modules.sellgui.config;

import fr.elias.oreoEssentials.modules.sellgui.SellItemIdResolver;
import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class SellGuiConfig {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration cfg;

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Map<String, Double> prices = new HashMap<>();

    public SellGuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (file == null) file = new File(plugin.getDataFolder(), "sellgui.yml");
        if (!file.exists()) plugin.saveResource("sellgui.yml", false);

        cfg = YamlConfiguration.loadConfiguration(file);

        prices.clear();
        if (cfg.isConfigurationSection("prices")) {
            for (String key : cfg.getConfigurationSection("prices").getKeys(false)) {
                double price = cfg.getDouble("prices." + key, -1D);
                if (price >= 0) prices.put(key.toUpperCase(Locale.ROOT), price);
            }
        }
    }

    public String title() {
        return mmToLegacy(cfg.getString("settings.gui.title", "Sell Items"));
    }

    public String confirmTitle() {
        return mmToLegacy(cfg.getString("settings.gui.confirm_title", "Confirm Sale"));
    }

    public boolean allowUnsellableInGui() {
        return cfg.getBoolean("settings.allow_unsellable_items_in_gui", false);
    }


    public String msgNothingSellable() {
        return mmToLegacy(cfg.getString("settings.messages.nothing_sellable", "<red>Nothing sellable in the sell slots.</red>"));
    }

    public String msgReturnedAll() {
        return mmToLegacy(cfg.getString("settings.messages.returned_all", "<green>Returned all items.</green>"));
    }


    public Material buttonMaterial(String key, Material def) {
        String path = "settings.buttons." + key + ".material";
        String raw = cfg.getString(path, def.name());
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return def;
        }
    }

    public String buttonName(String key, String defMiniMessage) {
        return mmToLegacy(cfg.getString("settings.buttons." + key + ".name", defMiniMessage));
    }

    public List<String> buttonLore(String key, List<String> defMiniMessageLore) {
        List<String> raw = cfg.getStringList("settings.buttons." + key + ".lore");
        if (raw == null || raw.isEmpty()) raw = defMiniMessageLore;
        List<String> out = new ArrayList<>();
        for (String line : raw) out.add(mmToLegacy(line));
        return out;
    }

    public ItemStack buildButton(String key, Material defMat, String defNameMM, List<String> defLoreMM) {
        Material mat = buttonMaterial(key, defMat);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(buttonName(key, defNameMM));
            List<String> lore = buttonLore(key, defLoreMM);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }


    /**
     * Resolves the sell unit-price for an item.
     *
     * Priority order:
     *  1. Custom-item key (ItemsAdder / Nexo) defined in sellgui.yml
     *  2. MATERIAL:custom-model-data key defined in sellgui.yml
     *  3. Plain MATERIAL key defined in sellgui.yml
     *  4. Best sell-price found across all shop/*.yml files (fallback)
     *
     * sellgui.yml always wins over shop prices when explicitly configured.
     */
    public double getUnitPrice(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return -1;

        // 1. External custom-item key (IA:namespace:id  or  NEXO:id)
        String externalKey = SellItemIdResolver.resolveKey(item);
        if (externalKey != null) {
            Double ext = prices.get(externalKey.toUpperCase(Locale.ROOT));
            if (ext != null) return ext;
        }

        String matKey = item.getType().name().toUpperCase(Locale.ROOT);
        int cmd = getCustomModelData(item);

        // 2. MATERIAL:custom-model-data key
        if (cmd != -1) {
            Double p = prices.get(matKey + ":" + cmd);
            if (p != null) return p;
        }

        // 3. Plain material key
        Double p = prices.get(matKey);
        if (p != null) return p;

        // 4. Fallback: best sell-price from the shop module
        ShopModule shop = ShopModule.getActive();
        if (shop != null && shop.isEnabled()) {
            ShopItem shopItem = shop.getShopManager().findBestSellItem(item);
            if (shopItem != null && shopItem.canSell()) {
                return shopItem.getSellPrice();
            }
        }

        return -1;
    }


    private int getCustomModelData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        try {
            if (meta.hasCustomModelData()) return meta.getCustomModelData();
        } catch (Throwable ignored) {}
        return -1;
    }

    private String mmToLegacy(String input) {
        if (input == null) return "";
        Component c = MINI.deserialize(input);
        return LEGACY.serialize(c);
    }
}
