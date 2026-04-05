package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.TimeFormatter;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

public class ManageGUI implements InventoryProvider {

    private final AuctionHouseModule module;

    public ManageGUI(AuctionHouseModule module) { this.module = module; }

    public static SmartInventory getInventory(AuctionHouseModule module) {
        return SmartInventory.builder()
                .id("oe_ah_manage")
                .provider(new ManageGUI(module))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(c(module.getConfig().getGui().getString("manage.title", "&6&lYour Listings")))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pg = contents.pagination();
        contents.fillBorders(ClickableItem.empty(glass(module.getConfig().guiBorder("manage", Material.GRAY_STAINED_GLASS_PANE))));

        List<Auction> auctions = module.getPlayerActiveListings(player.getUniqueId());

        if (auctions.isEmpty()) {
            var cfg = module.getConfig();
            int slot = cfg.guiSlot("manage", "no-listings", 22);
            ItemStack none = named(cfg.guiMaterial("manage", "no-listings", Material.BARRIER),
                    cfg.guiNameRaw("manage", "no-listings", "&c&lNo Active Listings"));
            ItemMeta m = none.getItemMeta();
            m.setLore(cfg.guiLoreRaw("manage", "no-listings",
                    List.of(c("&7Use &e/ahs <price> &7to list an item!"))));
            none.setItemMeta(m);
            contents.set(slot / 9, slot % 9, ClickableItem.empty(none));
        } else {
            ClickableItem[] items = auctions.stream().map(a -> listingItem(player, a)).toArray(ClickableItem[]::new);
            pg.setItems(items);
            pg.setItemsPerPage(28);
            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            it.blacklist(1,8).blacklist(2,0).blacklist(2,8).blacklist(3,0).blacklist(3,8).blacklist(4,1);
            pg.addToIterator(it);
        }

        var cfg = module.getConfig();

        if (!pg.isFirst()) {
            int slot = cfg.guiSlot("manage", "prev-page", 48);
            contents.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("manage", "prev-page", Material.ARROW),
                            cfg.guiNameRaw("manage", "prev-page", "&e&lPrevious")),
                    e -> { click(player); getInventory(module).open(player, pg.previous().getPage()); }));
        }
        if (!pg.isLast()) {
            int slot = cfg.guiSlot("manage", "next-page", 50);
            contents.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("manage", "next-page", Material.ARROW),
                            cfg.guiNameRaw("manage", "next-page", "&e&lNext")),
                    e -> { click(player); getInventory(module).open(player, pg.next().getPage()); }));
        }

        int expiredCount = module.getPlayerExpired(player.getUniqueId()).size();
        int expSlot = cfg.guiSlot("manage", "expired", 46);
        String expName = cfg.guiNameRaw("manage", "expired", "&c&lExpired ({count})")
                .replace("{count}", String.valueOf(expiredCount));
        contents.set(expSlot / 9, expSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("manage", "expired", Material.RED_WOOL), expName),
                e -> { if (expiredCount > 0) { click(player); ExpiredGUI.getInventory(module).open(player); } }));

        int soldCount = module.getPlayerSold(player.getUniqueId()).size();
        int soldSlot = cfg.guiSlot("manage", "sold", 52);
        String soldName = cfg.guiNameRaw("manage", "sold", "&a&lSold ({count})")
                .replace("{count}", String.valueOf(soldCount));
        contents.set(soldSlot / 9, soldSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("manage", "sold", Material.GREEN_WOOL), soldName),
                e -> { if (soldCount > 0) { click(player); SoldGUI.getInventory(module).open(player); } }));

        int backSlot = cfg.guiSlot("manage", "back", 49);
        contents.set(backSlot / 9, backSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("manage", "back", Material.BARRIER),
                        cfg.guiNameRaw("manage", "back", "&c&lBack")),
                e -> { click(player); BrowseGUI.getInventory(module).open(player); }));

        ItemStack stats = named(Material.BOOK, "&6&lStatistics");
        ItemMeta sm = stats.getItemMeta();
        sm.setLore(List.of("", c("&7Active: &e" + auctions.size()),
                c("&7Sold: &a" + soldCount), c("&7Expired: &c" + expiredCount), ""));
        stats.setItemMeta(sm);
        contents.set(0, 4, ClickableItem.empty(stats));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private ClickableItem listingItem(Player player, Auction a) {
        ItemStack display = a.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(c("&7Price: &a" + module.formatMoney(a.getPrice())));
        lore.add(c("&7Time Left: &e" + TimeFormatter.format(a.getTimeRemaining())));
        lore.add("");
        lore.add(c("&e&lLeft-Click: &eCancel"));
        lore.add(c("&a&lRight-Click: &aView in Browse"));
        meta.setLore(lore);
        display.setItemMeta(meta);

        return ClickableItem.of(display, e -> {
            if (e.isLeftClick()) {
                click(player);
                if (module.cancelAuction(player, a.getId())) {
                    try { player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); } catch (Throwable ignored) {}
                    getInventory(module).open(player);
                }
            } else if (e.isRightClick()) {
                click(player);
                BrowseGUI.getInventory(module, a.getCategory()).open(player);
            }
        });
    }
}