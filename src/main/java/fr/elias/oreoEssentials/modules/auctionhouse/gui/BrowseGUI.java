package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.TimeFormatter;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BrowseGUI implements InventoryProvider {

    private final AuctionHouseModule module;
    private final AuctionCategory category;
    private final String searchQuery;

    public BrowseGUI(AuctionHouseModule module, AuctionCategory category, String searchQuery) {
        this.module = module;
        this.category = category;

        String q = (searchQuery == null ? null : searchQuery.trim());

        this.searchQuery = (q == null || q.isEmpty()) ? null : q.toLowerCase();

    }


    public static SmartInventory getInventory(AuctionHouseModule module) {
        return getInventory(module, null, null);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, AuctionCategory category) {
        return getInventory(module, category, null);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, String searchQuery) {
        return getInventory(module, null, searchQuery);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, AuctionCategory category, String searchQuery) {
        var cfg = module.getConfig();
        var gui = cfg.getGui();
        String title;
        if (searchQuery != null && !searchQuery.isBlank()) {
            String pattern = category != null
                    ? gui.getString("browse.title-search-category", "&6&lSearch: &f{query} &8- &e{category}")
                    : gui.getString("browse.title-search", "&6&lSearch: &f{query}");
            title = c(pattern
                    .replace("{query}", searchQuery)
                    .replace("{category}", category != null ? category.getDisplayName() : ""));
        } else {
            String pattern = category != null
                    ? gui.getString("browse.title-category", "&6&lAuction House &8- &e{category}")
                    : gui.getString("browse.title", "&6&lAuction House");
            title = c(pattern.replace("{category}", category != null ? category.getDisplayName() : ""));
        }

        String id = "oe_ah_browse"
                + (category != null ? "_" + category.name() : "")
                + (searchQuery != null && !searchQuery.isBlank() ? "_search" : "");

        return SmartInventory.builder()
                .id(id)
                .provider(new BrowseGUI(module, category, searchQuery))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(title)
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();
        contents.fillBorders(ClickableItem.empty(glass(module.getConfig().guiBorder("browse", Material.GRAY_STAINED_GLASS_PANE))));
        // Register this player so applyIncomingSync() can push live updates to them.
        module.registerBrowseViewer(player.getUniqueId(), category, searchQuery, pagination.getPage());

        List<Auction> auctions = category == null
                ? module.getAllActiveAuctions()
                : module.getAuctionsByCategory(category);

        if (searchQuery != null) {
            auctions = auctions.stream()
                    .filter(a -> matchesSearch(a, searchQuery))
                    .toList();
        }

        if (auctions.isEmpty()) {
            var cfg = module.getConfig();
            String btnKey = searchQuery != null ? "no-results" : "no-auctions";
            String defName = searchQuery != null ? "&c&lNo Results" : "&c&lNo Auctions Available";
            int slot = cfg.guiSlot("browse", btnKey, 22);
            contents.set(slot / 9, slot % 9, ClickableItem.empty(
                    named(cfg.guiMaterial("browse", btnKey, Material.BARRIER),
                            cfg.guiNameRaw("browse", btnKey, defName))));
        } else {
            ClickableItem[] items = auctions.stream()
                    .map(a -> auctionItem(player, a))
                    .toArray(ClickableItem[]::new);

            pagination.setItems(items);
            pagination.setItemsPerPage(28);

            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            it.blacklist(1, 8).blacklist(2, 0).blacklist(2, 8).blacklist(3, 0).blacklist(3, 8).blacklist(4, 1);
            pagination.addToIterator(it);
        }

        nav(player, contents, pagination);
        controls(player, contents);

        contents.set(0, 4, ClickableItem.empty(balanceHead(player)));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        contents.set(0, 4, ClickableItem.empty(balanceHead(player)));
        // Keep the viewer's page cursor current so refreshes land on the right page.
        module.updateBrowseViewerPage(player.getUniqueId(), contents.pagination().getPage());
    }

    private boolean matchesSearch(Auction a, String q) {
        if (a == null) return false;

        if (a.getSellerName() != null && a.getSellerName().toLowerCase().contains(q)) return true;

        ItemStack it = a.getItem();
        if (it == null) return false;

        if (it.getType().name().toLowerCase().contains(q)) return true;

        if (it.hasItemMeta() && it.getItemMeta() != null) {
            ItemMeta meta = it.getItemMeta();

            if (meta.hasDisplayName() && meta.getDisplayName() != null) {
                String dn = ChatColor.stripColor(meta.getDisplayName());
                if (dn != null && dn.toLowerCase().contains(q)) return true;
            }

            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line == null) continue;
                    String s = ChatColor.stripColor(line);
                    if (s != null && s.toLowerCase().contains(q)) return true;
                }
            }
        }

        if (a.getItemsAdderID() != null && a.getItemsAdderID().toLowerCase().contains(q)) return true;
        if (a.getNexoID() != null && a.getNexoID().toLowerCase().contains(q)) return true;
        if (a.getOraxenID() != null && a.getOraxenID().toLowerCase().contains(q)) return true;

        return false;
    }

    private ClickableItem auctionItem(Player viewer, Auction a) {
        ItemStack display = a.getItem().clone();
        ItemMeta meta = display.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(c("&7Seller: &e" + a.getSellerName()));
        lore.add(c("&7Price: &a" + module.formatMoney(a.getPrice(), a.getCurrencyId())));
        if (a.getCurrencyId() != null) lore.add(c("&7Currency: &b" + a.getCurrencyId()));
        lore.add(c("&7Time Left: &e" + TimeFormatter.format(a.getTimeRemaining())));
        lore.add(c("&7Category: &b" + a.getCategory().getDisplayName()));
        if (a.getItemsAdderID() != null) lore.add(c("&d&lItemsAdder Item"));
        else if (a.getNexoID() != null) lore.add(c("&d&lNexo Item"));
        else if (a.getOraxenID() != null) lore.add(c("&d&lOraxen Item"));
        lore.add("");
        lore.add(a.getSeller().equals(viewer.getUniqueId())
                ? c("&e&lYOUR LISTING — click to manage")
                : c("&a&lClick to purchase!"));

        meta.setLore(lore);
        display.setItemMeta(meta);

        return ClickableItem.of(display, e -> {
            click(viewer);
            if (a.getSeller().equals(viewer.getUniqueId())) {
                ManageGUI.getInventory(module).open(viewer);
            } else {
                ConfirmGUI.getInventory(module, a).open(viewer);
            }
        });
    }

    private void nav(Player p, InventoryContents c, Pagination pg) {
        var cfg = module.getConfig();
        if (!pg.isFirst()) {
            int slot = cfg.guiSlot("browse", "prev-page", 48);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "prev-page", Material.ARROW),
                            cfg.guiNameRaw("browse", "prev-page", "&e&lPrevious Page")),
                    e -> { click(p); getInventory(module, category, searchQuery).open(p, pg.previous().getPage()); }));
        }

        if (!pg.isLast()) {
            int slot = cfg.guiSlot("browse", "next-page", 50);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "next-page", Material.ARROW),
                            cfg.guiNameRaw("browse", "next-page", "&e&lNext Page")),
                    e -> { click(p); getInventory(module, category, searchQuery).open(p, pg.next().getPage()); }));
        }

        int piSlot = cfg.guiSlot("browse", "page-indicator", 49);
        String piName = cfg.guiNameRaw("browse", "page-indicator", "&ePage {page}")
                .replace("{page}", String.valueOf(pg.getPage() + 1));
        c.set(piSlot / 9, piSlot % 9, ClickableItem.empty(
                named(cfg.guiMaterial("browse", "page-indicator", Material.PAPER), piName)));
    }

    private void controls(Player p, InventoryContents c) {
        var cfg = module.getConfig();

        if (searchQuery != null) {
            int slot = cfg.guiSlot("browse", "clear-search", 45);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "clear-search", Material.MAP),
                            cfg.guiNameRaw("browse", "clear-search", "&f&lClear Search")),
                    e -> { click(p); getInventory(module, category, null).open(p); }));
        }

        if (category == null) {
            int slot = cfg.guiSlot("browse", "categories", 46);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "categories", Material.COMPASS),
                            cfg.guiNameRaw("browse", "categories", "&b&lCategories")),
                    e -> { click(p); CategoryGUI.getInventory(module).open(p); }));
        } else {
            int slot = cfg.guiSlot("browse", "back-to-all", 46);
            c.set(slot / 9, slot % 9, ClickableItem.of(
                    named(cfg.guiMaterial("browse", "back-to-all", Material.BARRIER),
                            cfg.guiNameRaw("browse", "back-to-all", "&c&lBack to All")),
                    e -> { click(p); getInventory(module, null, searchQuery).open(p); }));
        }

        int ylSlot = cfg.guiSlot("browse", "your-listings", 52);
        c.set(ylSlot / 9, ylSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("browse", "your-listings", Material.CHEST),
                        cfg.guiNameRaw("browse", "your-listings", "&6&lYour Listings")),
                e -> { click(p); ManageGUI.getInventory(module).open(p); }));

        int closeSlot = cfg.guiSlot("browse", "close", 53);
        c.set(closeSlot / 9, closeSlot % 9, ClickableItem.of(
                named(cfg.guiMaterial("browse", "close", Material.BARRIER),
                        cfg.guiNameRaw("browse", "close", "&c&lClose")),
                e -> { click(p); p.closeInventory(); }));
    }

    private ItemStack balanceHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName(c("&6&lYour Balance"));
        m.setLore(List.of("",
                c("&7Balance: &a" + module.formatMoney(module.getEconomy().getBalance(p))),
                c("&7Your Listings: &e" + module.getPlayerActiveListings(p.getUniqueId()).size()),
                ""));
        head.setItemMeta(m);
        return head;
    }

    static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    static ItemStack glass(Material m) { ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.setDisplayName(" "); i.setItemMeta(meta); return i; }
    static ItemStack named(Material m, String name) { ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.setDisplayName(c(name)); i.setItemMeta(meta); return i; }
    static void click(Player p) { try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .5f, 1f); } catch(Throwable ignored){} }
}
