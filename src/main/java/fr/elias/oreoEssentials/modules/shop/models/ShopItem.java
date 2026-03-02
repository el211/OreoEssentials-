package fr.elias.oreoEssentials.modules.shop.models;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.hooks.ItemsAdderHook;
import fr.elias.oreoEssentials.modules.shop.hooks.NexoHook;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopItem {

    private final String id;
    private final String shopId;

    private final Material      material;
    private final String        displayName;
    private final List<String>  lore;
    private final int           customModelData;
    private final short         damage;
    private final Map<Enchantment, Integer> enchantments;
    private final PotionType    potionType;
    private final double buyPrice;
    private final double sellPrice;
    private final int    amount;

    private final int    slot;
    private final int    page;
    private final String permission;

    private final String itemsAdderId;
    private final String nexoId;


    public ShopItem(String id, String shopId, Material material, String displayName,
                    List<String> lore, double buyPrice, double sellPrice,
                    int amount, int slot, int page, String permission,
                    Map<Enchantment, Integer> enchantments, PotionType potionType,
                    int customModelData, short damage,
                    String itemsAdderId, String nexoId) {
        this.id             = id;
        this.shopId         = shopId;
        this.material       = material;
        this.displayName    = displayName;
        this.lore           = lore;
        this.buyPrice       = buyPrice;
        this.sellPrice      = sellPrice;
        this.amount         = amount;
        this.slot           = slot;
        this.page           = page;
        this.permission     = permission;
        this.enchantments   = enchantments;
        this.potionType     = potionType;
        this.customModelData = customModelData;
        this.damage         = damage;
        this.itemsAdderId   = itemsAdderId;
        this.nexoId         = nexoId;
    }

    public ItemStack buildItemStack() {
        ShopModule module = ShopModule.getActive();

        if (itemsAdderId != null && module != null) {
            ItemsAdderHook ia = module.getItemsAdderHook();
            if (ia != null && ia.isEnabled()) {
                ItemStack built = ia.buildItem(itemsAdderId);
                if (built != null) return applyMeta(built);
            }
        }

        if (nexoId != null && module != null) {
            NexoHook nexo = module.getNexoHook();
            if (nexo != null && nexo.isEnabled()) {
                ItemStack built = nexo.buildItem(nexoId);
                if (built != null) return applyMeta(built);
            }
        }

        return applyMeta(new ItemStack(material, 1));
    }

    private ItemStack applyMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isEmpty())
                meta.setDisplayName(colorize(displayName));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(colorize(line));
                meta.setLore(colored);
            }
            if (customModelData > 0)
                meta.setCustomModelData(customModelData);
            if (!enchantments.isEmpty() && !(meta instanceof PotionMeta)) {
                enchantments.forEach((ench, lvl) -> meta.addEnchant(ench, lvl, true));
            }
            if (potionType != null && meta instanceof PotionMeta pm)
                pm.setBasePotionType(potionType);
            item.setItemMeta(meta);
        }

        // Enchanted book stored enchants
        if (item.getType() == Material.ENCHANTED_BOOK && !enchantments.isEmpty()) {
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) item.getItemMeta();
            if (esm != null) {
                enchantments.forEach((ench, lvl) -> esm.addStoredEnchant(ench, lvl, true));
                item.setItemMeta(esm);
            }
        }

        return item;
    }


    public boolean matches(ItemStack item) {
        if (item == null) return false;
        ShopModule module = ShopModule.getActive();

        if (itemsAdderId != null && module != null) {
            ItemsAdderHook ia = module.getItemsAdderHook();
            if (ia != null && ia.isEnabled()) {
                return ia.matches(item, itemsAdderId);
            }
        }

        if (nexoId != null && module != null) {
            NexoHook nexo = module.getNexoHook();
            if (nexo != null && nexo.isEnabled()) {
                return nexo.matches(item, nexoId);
            }
        }

        if (item.getType() != material) return false;
        if (potionType != null) {
            ItemMeta meta = item.getItemMeta();
            return (meta instanceof PotionMeta pm) && pm.getBasePotionType() == potionType;
        }
        return true;
    }


    private static String colorize(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }


    public String               getId()             { return id; }
    public String               getShopId()         { return shopId; }
    public Material             getMaterial()       { return material; }
    public String               getDisplayName()    { return displayName; }
    public List<String>         getLore()           { return lore; }
    public double               getBuyPrice()       { return buyPrice; }
    public double               getSellPrice()      { return sellPrice; }
    public int                  getAmount()         { return amount; }
    public int                  getSlot()           { return slot; }
    public int                  getPage()           { return page; }
    public String               getPermission()     { return permission; }
    public Map<Enchantment, Integer> getEnchantments() { return enchantments; }
    public PotionType           getPotionType()     { return potionType; }
    public int                  getCustomModelData(){ return customModelData; }
    public short                getDamage()         { return damage; }
    public String               getItemsAdderId()   { return itemsAdderId; }
    public String               getNexoId()         { return nexoId; }

    public boolean canBuy()          { return buyPrice  >= 0; }
    public boolean canSell()         { return sellPrice >= 0; }
    public boolean isItemsAdderItem(){ return itemsAdderId != null; }
    public boolean isNexoItem()      { return nexoId != null; }
}