package fr.elias.oreoEssentials.modules.shop.models;

import fr.elias.oreoEssentials.modules.shop.rotation.RotationConfig;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class Shop {

    private final String id;
    private final String title;
    private final int    rows;
    private final int    totalPages;
    /** null = use Vault; non-null = use CurrencyService with this ID */
    private final String currencyId;
    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    /** null = not a rotating shop */
    private RotationConfig rotationConfig;

    private boolean hideBackButton = false;

    public Shop(String id, String title, int rows, int totalPages, String currencyId) {
        this.id         = id;
        this.title      = title;
        this.rows       = rows;
        this.totalPages = totalPages;
        this.currencyId = (currencyId != null && !currencyId.isBlank()) ? currencyId.toLowerCase() : null;
    }


    public void addItem(ShopItem item) {
        items.put(item.getId(), item);
    }

    public List<ShopItem> getItemsForPage(int page) {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItem item : items.values()) {
            if (item.getPage() == page) result.add(item);
        }
        return result;
    }

    public ShopItem getItemBySlot(int slot, int page) {
        for (ShopItem item : items.values()) {
            if (item.getSlot() == slot && item.getPage() == page) return item;
        }
        return null;
    }

    public ShopItem findMatchingItem(ItemStack stack) {
        if (stack == null) return null;
        for (ShopItem shopItem : items.values()) {
            if (shopItem.canSell() && shopItem.matches(stack)) return shopItem;
        }
        return null;
    }


    public void setRotationConfig(RotationConfig rotationConfig) {
        this.rotationConfig = rotationConfig;
    }

    public String               getId()        { return id; }
    public String               getTitle()     { return title; }
    public int                  getRows()      { return rows; }
    public int                  getTotalPages(){ return totalPages; }
    /** null = Vault economy; non-null = CurrencyService currency ID */
    public String               getCurrencyId(){ return currencyId; }
    public Map<String, ShopItem> getItems()    { return items; }
    public Collection<ShopItem> getAllItems()  { return items.values(); }

    public void setHideBackButton(boolean hideBackButton) { this.hideBackButton = hideBackButton; }

    /** null when rotation is disabled for this shop. */
    public RotationConfig getRotationConfig() { return rotationConfig; }
    /** True when this shop has a valid rotating configuration. */
    public boolean isRotating()               { return rotationConfig != null; }
    /** True when the back-to-main-menu button should not be shown (e.g. NPC-only shop). */
    public boolean isHideBackButton()         { return hideBackButton; }
}