package fr.elias.oreoEssentials.modules.portals.gui;

import fr.elias.oreoEssentials.modules.portals.PortalsManager;
import fr.elias.oreoEssentials.modules.portals.PortalsManager.Portal;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-row GUI listing all portals. Click a portal entry to open PortalEditGUI.
 */
public final class PortalListGUI implements InventoryProvider {

    private final PortalsManager manager;

    private PortalListGUI(PortalsManager manager) {
        this.manager = manager;
    }

    public static SmartInventory getInventory(PortalsManager manager) {
        return SmartInventory.builder()
                .id("oe_portal_list")
                .provider(new PortalListGUI(manager))
                .manager(manager.getPlugin().getInvManager())
                .size(6, 9)
                .title(ChatColor.GOLD + "" + ChatColor.BOLD + "Portals")
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Border
        ItemStack border = glass(Material.GRAY_STAINED_GLASS_PANE);
        contents.fillBorders(ClickableItem.empty(border));

        Pagination pg = contents.pagination();

        List<String> names = new ArrayList<>(manager.listNames());
        ClickableItem[] items = names.stream()
                .map(name -> {
                    Portal portal = manager.get(name);
                    if (portal == null) return ClickableItem.empty(new ItemStack(Material.AIR));
                    return ClickableItem.of(portalItem(portal), e ->
                            PortalEditGUI.getInventory(manager, portal.name).open(player));
                })
                .toArray(ClickableItem[]::new);

        pg.setItems(items);
        pg.setItemsPerPage(28);

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 8).blacklist(4, 1);
        pg.addToIterator(it);

        // Prev / next
        if (!pg.isFirst()) {
            contents.set(5, 3, ClickableItem.of(named(Material.ARROW, "&e&lPrevious"),
                    e -> getInventory(manager).open(player, pg.previous().getPage())));
        }
        if (!pg.isLast()) {
            contents.set(5, 5, ClickableItem.of(named(Material.ARROW, "&e&lNext"),
                    e -> getInventory(manager).open(player, pg.next().getPage())));
        }

        // Page indicator
        contents.set(5, 4, ClickableItem.empty(named(Material.PAPER,
                "&ePage " + (pg.getPage() + 1))));

        // Empty state
        if (names.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER, "&c&lNo Portals Configured")));
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack portalItem(Portal portal) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(c("&b&l" + portal.name));
        List<String> lore = new ArrayList<>();
        lore.add(c("&7World: &f" + portal.world.getName()));
        if (portal.destWarp != null && !portal.destWarp.isEmpty())
            lore.add(c("&7Dest Warp: &a" + portal.destWarp));
        else
            lore.add(c("&7Destination: &f" + shortLoc(portal.destination)));
        if (portal.destServer != null && !portal.destServer.isEmpty())
            lore.add(c("&7Server: &a" + portal.destServer));
        if (portal.permission != null && !portal.permission.isEmpty())
            lore.add(c("&7Permission: &e" + portal.permission));
        lore.add(c("&7Keep Yaw: &f" + (portal.keepYawPitch ? "Yes" : "No")));
        lore.add("");
        lore.add(c("&eClick to edit"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(c(name)); item.setItemMeta(meta); }
        return item;
    }

    private static ItemStack glass(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String shortLoc(org.bukkit.Location l) {
        return l.getWorld().getName() + " ("
                + (int) l.getX() + ", " + (int) l.getY() + ", " + (int) l.getZ() + ")";
    }
}
