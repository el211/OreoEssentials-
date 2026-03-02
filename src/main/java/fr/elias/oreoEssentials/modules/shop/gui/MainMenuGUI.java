package fr.elias.oreoEssentials.modules.shop.gui;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.util.Lang.color;

public final class MainMenuGUI implements InventoryProvider {

    private final ShopModule module;
    private SmartInventory smartInv;

    public MainMenuGUI(ShopModule module) {
        this.module = module;
        build();
    }

    private void build() {
        smartInv = SmartInventory.builder()
                .id("oe_shop_main_menu")
                .title(color(module.getShopConfig().getMainMenuTitle()))
                .size(module.getShopConfig().getMainMenuRows(), 9)
                .provider(this)
                .manager(module.getPlugin().getInvManager())
                .closeable(true)
                .updateFrequency(20)
                .build();
    }

    public void open(Player player)   { smartInv.open(player); }
    public void rebuild()             { build(); }


    @Override
    public void init(Player player, InventoryContents contents) {
        int rows = module.getShopConfig().getMainMenuRows();

        if (module.getShopConfig().getRawConfig().getBoolean("main-menu.fill-empty-slots", true)) {
            Material fill = safeMat(
                    module.getShopConfig().getRawConfig().getString("main-menu.fill-material",
                            "BLACK_STAINED_GLASS_PANE"),
                    Material.BLACK_STAINED_GLASS_PANE);
            contents.fill(ClickableItem.empty(filler(fill)));
        }

        ConfigurationSection items =
                module.getShopConfig().getRawConfig().getConfigurationSection("main-menu-items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection sec = items.getConfigurationSection(key);
                if (sec == null) continue;

                int slot = sec.getInt("slot", -1);
                if (slot < 0 || slot >= rows * 9) continue;

                Material mat = safeMat(sec.getString("material", "CHEST"), Material.CHEST);
                String name  = sec.getString("name", key);
                List<String> lore = sec.getStringList("lore");
                String shopId = sec.getString("shop", null);

                ItemStack icon = buildIcon(mat, name, lore);
                contents.set(slot / 9, slot % 9, ClickableItem.from(icon, data -> {
                    if (shopId == null) return;
                    Shop shop = module.getShopManager().getShop(shopId);
                    if (shop == null) {
                        player.sendMessage(color(module.getShopConfig().getMessage("shop-not-found")
                                .replace("{shop}", shopId)));
                        return;
                    }
                    if (!player.hasPermission("oshopgui.shop")) {
                        player.sendMessage(color(module.getShopConfig().getMessage("no-permission")));
                        return;
                    }
                    module.getShopGUI().open(player, shop, 1);
                }));
            }
        }

        int closeSlot = module.getShopConfig().getRawConfig().getInt("main-menu.close-slot", 49);
        ItemStack close = buildIcon(Material.BARRIER,
                module.getShopConfig().getRawMessage("gui-close-name", "&c&lClose"),
                module.getShopConfig().getRawMessage("gui-close-lore", "&7Click to close"));
        contents.set(closeSlot / 9, closeSlot % 9,
                ClickableItem.from(close, e -> player.closeInventory()));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}


    private static ItemStack buildIcon(Material mat, String name, Object lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        List<String> lines = new ArrayList<>();
        if (lore instanceof List<?> l) {
            for (Object o : l) lines.add(color(String.valueOf(o)));
        } else if (lore instanceof String s) {
            lines.add(color(s));
        }
        meta.setLore(lines);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material safeMat(String name, Material fallback) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

}