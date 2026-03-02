package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

public class CategoryGUI implements InventoryProvider {

    private final AuctionHouseModule module;

    public CategoryGUI(AuctionHouseModule module) { this.module = module; }

    public static SmartInventory getInventory(AuctionHouseModule module) {
        return SmartInventory.builder()
                .id("oe_ah_categories")
                .provider(new CategoryGUI(module))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(c(module.getConfig().getGui().getString("categories.title", "&b&lCategories")))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fillBorders(ClickableItem.empty(glass(module.getConfig().guiBorder("categories", Material.CYAN_STAINED_GLASS_PANE))));

        YamlConfiguration cats = module.getConfig().getCategories();
        for (AuctionCategory cat : AuctionCategory.values()) {
            if (cat == AuctionCategory.ALL) continue;
            if (!module.getConfig().isCategoryEnabled(cat)) continue;

            String path = cat.name().toLowerCase();
            int slot = cats.getInt(path + ".slot", 10 + cat.ordinal());
            String displayName = cats.getString(path + ".display-name", cat.getDisplayName());
            String iconName = cats.getString(path + ".icon", cat.getIcon().name());
            List<String> desc = cats.getStringList(path + ".description");

            Material icon;
            try { icon = Material.valueOf(iconName); } catch (Exception e) { icon = cat.getIcon(); }

            int count = module.getAuctionsByCategory(cat).size();
            List<String> lore = new ArrayList<>();
            for (String l : desc) lore.add(c(l));
            lore.add("");
            lore.add(c("&7Items available: &e" + count));
            lore.add("");
            lore.add(module.getConfig().hasCategoryPermission(player, cat) ? c("&a&lClick to browse!") : c("&c&lNo permission!"));

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(c(displayName));
            meta.setLore(lore);
            item.setItemMeta(meta);

            int row = slot / 9, col = slot % 9;
            contents.set(row, col, ClickableItem.of(item, e -> {
                if (module.getConfig().hasCategoryPermission(player, cat)) {
                    click(player);
                    BrowseGUI.getInventory(module, cat).open(player);
                } else {
                    try { player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); } catch (Throwable ignored) {}
                    player.sendMessage(module.getConfig().getMessage("errors.no-permission"));
                }
            }));
        }

        var cfg = module.getConfig();
        int backSlot = cfg.guiSlot("categories", "back", 49);
        contents.set(backSlot / 9, backSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("categories", "back", Material.BARRIER),
                        cfg.guiNameRaw("categories", "back", "&c&lBack to Browse")),
                e -> { click(player); BrowseGUI.getInventory(module, (AuctionCategory) null, null).open(player); }));

    }

    @Override public void update(Player player, InventoryContents contents) {}
}