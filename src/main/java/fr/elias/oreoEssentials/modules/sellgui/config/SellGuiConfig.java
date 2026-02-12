package fr.elias.oreoEssentials.modules.sellgui.config;

import fr.elias.oreoEssentials.modules.sellgui.SellItemIdResolver;
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


    public double getUnitPrice(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return -1;

        String externalKey = SellItemIdResolver.resolveKey(item);
        if (externalKey != null) {
            Double ext = prices.get(externalKey.toUpperCase(Locale.ROOT));
            if (ext != null) return ext;
        }

        String matKey = item.getType().name();
        int cmd = getCustomModelData(item);

        if (cmd != -1) {
            String cmdKey = (matKey + ":" + cmd).toUpperCase(Locale.ROOT);
            Double p = prices.get(cmdKey);
            if (p != null) return p;
        }

        Double p = prices.get(matKey.toUpperCase(Locale.ROOT));
        return p == null ? -1 : p;
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
