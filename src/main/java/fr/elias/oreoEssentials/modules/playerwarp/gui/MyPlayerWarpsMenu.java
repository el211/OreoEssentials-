package fr.elias.oreoEssentials.modules.playerwarp.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**

 * Features:
 * - Category filtering row (top)
 * - Warp list (rows 2-5)
 * - Pagination (arrows)
 * - Click warp to open edit menu
 *
 * No chat messages - pure GUI navigation.
 */
public class MyPlayerWarpsMenu implements InventoryProvider {

    private final PlayerWarpService service;
    private final String categoryFilter; // null = all

    public MyPlayerWarpsMenu(PlayerWarpService service, String categoryFilter) {
        this.service = service;
        this.categoryFilter = categoryFilter;
    }

    public static void open(Player player, PlayerWarpService service) {
        open(player, service, null);
    }

    public static void open(Player player, PlayerWarpService service, String categoryFilter) {
        SmartInventory.builder()
                .id("playerwarps_mywarps")
                .provider(new MyPlayerWarpsMenu(service, categoryFilter))
                .size(6, 9)
                .title(categoryFilter == null
                        ? "§bMy Warps"
                        : "§bMy Warps §7[§e" + categoryFilter + "§7]")
                .manager(OreoEssentials.get().getInventoryManager())
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        buildCategoriesRow(player, contents);
        update(player, contents);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();

        List<PlayerWarp> mine = service.listByOwner(player.getUniqueId());

        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            mine = mine.stream()
                    .filter(w -> categoryFilter.equalsIgnoreCase(
                            w.getCategory() == null ? "" : w.getCategory()))
                    .collect(Collectors.toList());
        }

        mine = mine.stream()
                .sorted(Comparator.comparing(PlayerWarp::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        ClickableItem[] items = new ClickableItem[mine.size()];

        for (int i = 0; i < mine.size(); i++) {
            PlayerWarp warp = mine.get(i);
            items[i] = ClickableItem.of(buildWarpItem(player, warp), e -> {
                // Opens the edit menu
                PlayerWarpEditMenu.open(player, service, warp, categoryFilter);
            });
        }

        pagination.setItems(items);
        pagination.setItemsPerPage(9 * 4);

        pagination.addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 2, 0)
                .allowOverride(true));

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.setDisplayName("§ePrevious page");
        prev.setItemMeta(prevMeta);

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setDisplayName("§eNext page");
        next.setItemMeta(nextMeta);

        contents.set(5, 2, ClickableItem.of(prev, e -> {
            if (!pagination.isFirst()) {
                pagination.previous();
                update(player, contents);
            }
        }));

        contents.set(5, 6, ClickableItem.of(next, e -> {
            if (!pagination.isLast()) {
                pagination.next();
                update(player, contents);
            }
        }));
    }

    private void buildCategoriesRow(Player player, InventoryContents contents) {
        List<PlayerWarp> mine = service.listByOwner(player.getUniqueId());

        Set<String> categories = mine.stream()
                .map(PlayerWarp::getCategory)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new)); // sorted

        ItemStack all = new ItemStack(Material.NETHER_STAR);
        ItemMeta allMeta = all.getItemMeta();
        allMeta.setDisplayName("§bAll categories");
        allMeta.setLore(List.of(
                categoryFilter == null ? "§aCurrently selected" : "§7Click to show all"
        ));
        all.setItemMeta(allMeta);

        contents.set(0, 0, ClickableItem.of(all, e -> {
            if (categoryFilter != null) {
                open(player, service, null);
            }
        }));

        int col = 2;
        for (String cat : categories) {
            if (col > 8) break;

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.setDisplayName("§e" + cat);
            List<String> lore = new ArrayList<>();
            if (categoryFilter != null && categoryFilter.equalsIgnoreCase(cat)) {
                lore.add("§aCurrently selected");
            } else {
                lore.add("§7Click to filter by this category");
            }
            meta.setLore(lore);
            paper.setItemMeta(meta);

            contents.set(0, col, ClickableItem.of(paper, e -> {
                if (categoryFilter != null && categoryFilter.equalsIgnoreCase(cat)) return;
                open(player, service, cat);
            }));

            col++;
        }
    }

    private ItemStack buildWarpItem(Player viewer, PlayerWarp warp) {
        ItemStack base = warp.getIcon() != null
                ? warp.getIcon().clone()
                : new ItemStack(Material.OAK_SIGN);

        ItemMeta meta = base.getItemMeta();

        String name = warp.getName();
        String desc = warp.getDescription();
        String category = warp.getCategory();
        double cost = warp.getCost();

        meta.setDisplayName("§a" + name);

        List<String> lore = new ArrayList<>();
        if (category != null && !category.isEmpty()) {
            lore.add("§7Category: §b" + category);
        }
        if (desc != null && !desc.isEmpty()) {
            lore.add("§7Description:");
            for (String line : splitColored(desc, 35)) {
                lore.add("§f" + line);
            }
        }
        if (cost > 0) {
            lore.add("§7Cost: §e" + cost);
        }

        lore.add("");
        if (warp.isLocked()) {
            lore.add("§7Status: §cLocked");
        } else if (warp.isWhitelistEnabled()) {
            lore.add("§7Status: §eWhitelist enabled");
        } else {
            lore.add("§7Status: §aPublic");
        }

        lore.add("");
        lore.add("§a» Click to edit this warp");

        meta.setLore(lore);
        base.setItemMeta(meta);
        return base;
    }

    private List<String> splitColored(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String w : words) {
            if (current.length() + w.length() + 1 > maxLength) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(" ");
            current.append(w);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}