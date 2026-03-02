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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

public class SellGUI implements InventoryProvider {

    private final AuctionHouseModule module;
    private final ItemStack itemToSell;
    private final double price;
    private final long durationHours;
    private final AuctionCategory selectedCategory;

    public SellGUI(AuctionHouseModule module, ItemStack item, double price, long durationHours, AuctionCategory selectedCategory) {
        this.module = module;
        this.itemToSell = item;
        this.price = price;
        this.durationHours = durationHours;
        this.selectedCategory = (selectedCategory != null ? selectedCategory : AuctionCategory.fromItem(item));
    }

    public static SmartInventory getInventory(AuctionHouseModule module, ItemStack item, double price, long duration) {
        return getInventory(module, item, price, duration, null);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, ItemStack item, double price, long duration, AuctionCategory selected) {
        return SmartInventory.builder()
                .id("oe_ah_sell_" + (selected != null ? selected.name() : "auto"))
                .provider(new SellGUI(module, item, price, duration, selected))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(c(module.getConfig().getGui().getString("sell.title", "&6&lSelect Category")))
                .build();
    }


    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fillBorders(ClickableItem.empty(glass(module.getConfig().guiBorder("sell", Material.ORANGE_STAINED_GLASS_PANE))));

        ItemStack preview = itemToSell.clone();
        ItemMeta pm = preview.getItemMeta();
        pm.setLore(List.of("",
                c("&7Price: &a" + module.formatMoney(price)),
                c("&7Duration: &e" + durationHours + " hours"),
                c("&7Category: &b" + selectedCategory.getDisplayName()),
                "", c("&eSelect a category below!")));
        preview.setItemMeta(pm);
        contents.set(0, 4, ClickableItem.empty(preview));

        YamlConfiguration cats = module.getConfig().getCategories();
        for (AuctionCategory cat : AuctionCategory.values()) {
            if (cat == AuctionCategory.ALL) continue;
            if (!module.getConfig().isCategoryEnabled(cat)) continue;

            String path = cat.name().toLowerCase();
            int slot = cats.getInt(path + ".slot", 10 + cat.ordinal());
            String displayName = cats.getString(path + ".display-name", cat.getDisplayName());
            String iconName = cats.getString(path + ".icon", cat.getIcon().name());

            Material icon;
            try { icon = Material.valueOf(iconName); } catch (Exception e) { icon = cat.getIcon(); }

            List<String> lore = new ArrayList<>();
            if (selectedCategory == cat) { lore.add(c("&a&l✔ SELECTED")); lore.add(""); }
            lore.add(module.getConfig().hasCategoryPermission(player, cat) ? c("&e&lClick to select!") : c("&c&lNo permission!"));

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(c(displayName));
            meta.setLore(lore);
            if (selectedCategory == cat) {
                meta.addEnchant(Enchantment.INFINITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);

            contents.set(slot / 9, slot % 9, ClickableItem.of(item, e -> {
                if (module.getConfig().hasCategoryPermission(player, cat)) {
                    click(player);
                    getInventory(module, itemToSell, price, durationHours, cat).open(player);

                } else {
                    try { player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); } catch (Throwable ignored) {}
                }
            }));
        }

        var cfg = module.getConfig();

        ItemStack confirm = named(cfg.guiMaterial("sell", "confirm", Material.GREEN_WOOL),
                cfg.guiNameRaw("sell", "confirm", "&a&l✔ CONFIRM LISTING"));
        ItemMeta cm = confirm.getItemMeta();
        cm.setLore(List.of("",
                c("&7Item: &e" + itemToSell.getType().name()),
                c("&7Price: &a" + module.formatMoney(price)),
                c("&7Duration: &e" + durationHours + " hours"),
                c("&7Category: &b" + selectedCategory.getDisplayName()),
                "", c("&a&lClick to list!"), ""));
        confirm.setItemMeta(cm);
        int confirmSlot = cfg.guiSlot("sell", "confirm", 52);
        contents.set(confirmSlot / 9, confirmSlot % 9, ClickableItem.of(confirm, e -> {
            click(player); player.closeInventory();
            if (module.createAuction(player, itemToSell, price, durationHours, selectedCategory)) {
                player.getInventory().setItemInMainHand(null);
                try { player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .5f, 1.5f); } catch (Throwable ignored) {}
            }
        }));

        int cancelSlot = cfg.guiSlot("sell", "cancel", 46);
        contents.set(cancelSlot / 9, cancelSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("sell", "cancel", Material.RED_WOOL),
                        cfg.guiNameRaw("sell", "cancel", "&c&l✗ CANCEL")),
                e -> { click(player); player.closeInventory(); player.sendMessage(c("&cListing cancelled.")); }));
    }

    @Override public void update(Player player, InventoryContents contents) {}
}