package fr.elias.oreoEssentials.modules.shop.managers;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public final class ShopManager {

    private final ShopModule module;
    private final Logger log;

    private final Map<String, Shop> shops = new LinkedHashMap<>();

    /**
     * Bundled shop files copied from the jar on first run.
     * Add any new shop YAML name here to have it deployed automatically.
     */
    private static final String[] BUNDLED_SHOPS = {
        "building_blocks", "ores_minerals", "food", "tools", "weapons_armor",
        "redstone", "farming_nature", "mob_drops", "nether", "end_misc",
        "potions", "enchanted_books", "brewing", "token_shop"
    };

    public ShopManager(ShopModule module) {
        this.module = module;
        this.log    = module.getPlugin().getLogger();
        ensureShopsFolder();
        loadShops();
    }

    // -------------------------------------------------------------------------

    public void reload() {
        shops.clear();
        loadShops();
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private void ensureShopsFolder() {
        File dir = module.getShopConfig().getShopsFolder();
        boolean firstRun = !dir.exists();

        if (firstRun) {
            dir.mkdirs();
            for (String name : BUNDLED_SHOPS) {
                try {
                    module.getPlugin().saveResource("shop/shops/" + name + ".yml", false);
                } catch (IllegalArgumentException ignored) {}
            }
            log.info("[Shop] Created shops/ folder and deployed " + BUNDLED_SHOPS.length + " bundled shop(s).");
            return;
        }

        // Opt-in: re-deploy any missing bundled shops on every startup
        if (module.getShopConfig().isAutoDeployMissing()) {
            int deployed = 0;
            for (String name : BUNDLED_SHOPS) {
                File target = new File(dir, name + ".yml");
                if (!target.exists()) {
                    try {
                        module.getPlugin().saveResource("shop/shops/" + name + ".yml", false);
                        deployed++;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (deployed > 0) {
                log.info("[Shop] Auto-deployed " + deployed + " missing bundled shop(s).");
            }
        }
    }

    private void loadShops() {
        File dir = module.getShopConfig().getShopsFolder();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("[Shop] No .yml files found in shop/shops/");
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) loadFile(f);
        log.info("[Shop] Loaded " + shops.size() + " shop(s) from " + files.length + " file(s).");
    }

    private void loadFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String fileId = file.getName().replace(".yml", "").toLowerCase();

        if (cfg.isConfigurationSection("shops")) {
            ConfigurationSection sec = cfg.getConfigurationSection("shops");
            if (sec == null) return;
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s != null) parseAndRegister(id.toLowerCase(), s, file.getName());
            }
            return;
        }
        String shopId = cfg.getString("shop-id", fileId).toLowerCase();
        parseAndRegister(shopId, cfg, file.getName());
    }

    private void parseAndRegister(String shopId, ConfigurationSection sec, String src) {
        String title      = sec.getString("title", shopId);
        int rows          = Math.max(1, sec.getInt("rows", 6));
        int pages         = Math.max(1, sec.getInt("pages", 1));
        String currencyId = sec.getString("currency", null); // null = use Vault

        Shop shop = new Shop(shopId, title, rows, pages, currencyId);

        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String itemId : itemsSec.getKeys(false)) {
                ConfigurationSection is = itemsSec.getConfigurationSection(itemId);
                if (is == null) continue;
                ShopItem item = parseItem(shopId, itemId, is);
                if (item != null) shop.addItem(item);
            }
        }

        if (shops.containsKey(shopId)) {
            log.warning("[Shop] Duplicate shop id '" + shopId + "' in " + src + " — skipping.");
            return;
        }
        shops.put(shopId, shop);
        log.info("[Shop] Loaded '" + shopId + "' (" + shop.getItems().size() + " items) from " + src);
    }

    @SuppressWarnings("deprecation")
    private ShopItem parseItem(String shopId, String itemId, ConfigurationSection s) {
        String iaId   = s.getString("itemsadder-id", null);
        String nexoId = s.getString("nexo-id", null);

        String materialName = s.getString("material", "PAPER");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            if (iaId == null && nexoId == null) {
                log.warning("[Shop] Unknown material '" + materialName + "' in " + shopId + "/" + itemId);
                return null;
            }
            material = Material.PAPER;
        }

        String       name    = s.getString("name", "");
        List<String> lore    = s.getStringList("lore");
        double       buy     = s.getDouble("buy-price", -1);
        double       sell    = s.getDouble("sell-price", -1);
        int          amount  = Math.max(1, s.getInt("amount", 1));
        int          slot    = s.getInt("slot", 10);
        int          page    = Math.max(1, s.getInt("page", 1));
        String       perm    = s.getString("permission", null);
        int          cmd     = s.getInt("custom-model-data", 0);
        short        damage  = (short) s.getInt("damage", 0);

        Map<Enchantment, Integer> enchants = new HashMap<>();
        ConfigurationSection eSec = s.getConfigurationSection("enchantments");
        if (eSec != null) {
            for (String eName : eSec.getKeys(false)) {
                try {
                    Enchantment ench = Enchantment.getByName(eName.toUpperCase());
                    if (ench == null)
                        ench = org.bukkit.Registry.ENCHANTMENT.get(
                                org.bukkit.NamespacedKey.minecraft(eName.toLowerCase()));
                    if (ench != null) enchants.put(ench, eSec.getInt(eName));
                    else log.warning("[Shop] Unknown enchantment '" + eName + "' in " + itemId);
                } catch (Exception ex) {
                    log.warning("[Shop] Error parsing enchantment '" + eName + "': " + ex.getMessage());
                }
            }
        }

        PotionType potionType = null;
        String ptn = s.getString("potion-type", null);
        if (ptn != null) {
            try { potionType = PotionType.valueOf(ptn.toUpperCase()); }
            catch (IllegalArgumentException e) {
                log.warning("[Shop] Unknown potion-type '" + ptn + "' in " + itemId);
            }
        }

        return new ShopItem(itemId, shopId, material, name, lore,
                buy, sell, amount, slot, page, perm,
                enchants, potionType, cmd, damage, iaId, nexoId);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    public Shop              getShop(String id)    { return shops.get(id.toLowerCase()); }
    public Map<String, Shop> getAllShops()          { return Collections.unmodifiableMap(shops); }
    public int               getShopCount()        { return shops.size(); }

    /** Finds the highest sell-price ShopItem for a given ItemStack across all shops. */
    public ShopItem findBestSellItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        ShopItem best = null;
        for (Shop shop : shops.values()) {
            ShopItem found = shop.findMatchingItem(stack);
            if (found != null && found.canSell()) {
                if (best == null || found.getSellPrice() > best.getSellPrice()) best = found;
            }
        }
        return best;
    }
}