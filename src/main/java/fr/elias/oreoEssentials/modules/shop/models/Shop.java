package fr.elias.oreoEssentials.modules.shop.models;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class Shop {

    private final String id;
    private final String title;
    private final int    rows;
    private final int    totalPages;
    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    public Shop(String id, String title, int rows, int totalPages) {
        this.id         = id;
        this.title      = title;
        this.rows       = rows;
        this.totalPages = totalPages;
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


    public String               getId()        { return id; }
    public String               getTitle()     { return title; }
    public int                  getRows()      { return rows; }
    public int                  getTotalPages(){ return totalPages; }
    public Map<String, ShopItem> getItems()    { return items; }
    public Collection<ShopItem> getAllItems()  { return items.values(); }
}