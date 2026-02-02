package fr.elias.oreoEssentials.modules.warps.provider;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.modules.warps.commands.WarpsAdminCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class WarpsAdminProvider implements InventoryProvider {

    private final WarpService warps;

    public WarpsAdminProvider(WarpService warps) {
        this.warps = warps;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        draw(p, contents);
    }

    @Override
    public void update(Player p, InventoryContents contents) {
    }

    private void draw(Player p, InventoryContents contents) {
        contents.fill(ClickableItem.empty(filler()));

        final OreoEssentials plugin = OreoEssentials.get();
        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory dir = plugin.getWarpDirectory();

        List<String> names = new ArrayList<>(safeListWarps());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        contents.set(0, 4, ClickableItem.empty(counterItem(p, names.size())));
        contents.set(0, 8, ClickableItem.of(refreshItem(p), e ->
                contents.inventory().open(p, contents.pagination().getPage())));

        ClickableItem[] items = names.stream().map(displayName -> {
            final String key = displayName.toLowerCase(Locale.ROOT);

            String server = (dir != null ? dir.getWarpServer(key) : localServer);
            if (server == null || server.isBlank()) server = localServer;

            Location loc = null;
            if (server.equalsIgnoreCase(localServer)) {
                try { loc = warps.getWarp(key); } catch (Throwable ignored) {}
            }

            final String currentPerm = (dir == null ? null : dir.getWarpPermission(key));
            final boolean protectedMode = currentPerm != null && !currentPerm.isBlank();

            ItemStack icon = warpAdminItem(p, displayName, server, loc, protectedMode, currentPerm);

            return ClickableItem.of(icon, e -> {
                ClickType type = e.getClick();

                // Quick-teleport convenience
                if (type == ClickType.SHIFT_LEFT || type == ClickType.MIDDLE) {
                    WarpsAdminCommand.crossServerTeleport(warps, p, displayName);
                    return;
                }

                String actionTitle = Lang.msgWithDefault(
                        "warp.admin.gui.title",
                        "<dark_aqua>Warp: <aqua>%warp%</aqua></dark_aqua>",
                        Map.of("warp", displayName),
                        p
                );

                SmartInventory.builder()
                        .id("oreo:warps_admin_actions:" + key)
                        .provider(new WarpAdminActionsProvider(warps, key))
                        .size(4, 9)
                        .title(actionTitle)
                        .manager(OreoEssentials.get().getInvManager())
                        .build()
                        .open(p);
            });
        }).toArray(ClickableItem[]::new);

        Pagination pagination = contents.pagination();
        pagination.setItems(items);
        pagination.setItemsPerPage(28);

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0); it.blacklist(1, 8);
        it.blacklist(2, 0); it.blacklist(2, 8);
        it.blacklist(3, 0); it.blacklist(3, 8);
        it.blacklist(4, 0); it.blacklist(4, 8);
        pagination.addToIterator(it);

        if (!pagination.isFirst()) {
            String prevName = Lang.msgWithDefault(
                    "warp.admin.list.previous",
                    "<yellow>Previous Page</yellow>",
                    p
            );
            contents.set(5, 0, ClickableItem.of(nav(Material.ARROW, prevName),
                    e -> contents.inventory().open(p, pagination.previous().getPage())));
        }

        if (!pagination.isLast()) {
            String nextName = Lang.msgWithDefault(
                    "warp.admin.list.next",
                    "<yellow>Next Page</yellow>",
                    p
            );
            contents.set(5, 8, ClickableItem.of(nav(Material.ARROW, nextName),
                    e -> contents.inventory().open(p, pagination.next().getPage())));
        }
    }


    private Set<String> safeListWarps() {
        try {
            Set<String> s = warps.listWarps();
            return (s == null) ? Collections.emptySet() : s;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack refreshItem(Player p) {
        ItemStack it = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String name = Lang.msgWithDefault(
                    "warp.admin.list.refresh",
                    "<yellow>Refresh</yellow>",
                    p
            );

            String lore = Lang.msgWithDefault(
                    "warp.admin.list.refresh-lore",
                    "<gray>Click to reload warps.</gray>",
                    p
            );

            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack counterItem(Player p, int count) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String name = Lang.msgWithDefault(
                    "warp.admin.list.counter",
                    "<gold>Warps <white>(%count%)</white></gold>",
                    Map.of("count", String.valueOf(count)),
                    p
            );

            String lore1 = Lang.msgWithDefault(
                    "warp.admin.list.counter-lore.0",
                    "<gray>Left/Right-click: <white>Manage warp</white></gray>",
                    p
            );

            String lore2 = Lang.msgWithDefault(
                    "warp.admin.list.counter-lore.1",
                    "<gray>Shift-Left or Middle-click: <white>Quick Teleport</white></gray>",
                    p
            );

            meta.setDisplayName(name);
            meta.setLore(List.of(lore1, lore2));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack nav(Material type, String name) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack warpAdminItem(Player p, String name, String server, Location loc,
                                    boolean protectedMode, String perm) {
        ItemStack it = new ItemStack(Material.LODESTONE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String displayName = Lang.msgWithDefault(
                    "warp.admin.list.warp-name",
                    "<aqua>%name%</aqua>",
                    Map.of("name", name),
                    p
            );

            List<String> lore = new ArrayList<>();

            lore.add(Lang.msgWithDefault(
                    "warp.admin.list.warp-server",
                    "<gray>Server: <yellow>%server%</yellow></gray>",
                    Map.of("server", server),
                    p
            ));

            if (loc != null && loc.getWorld() != null) {
                lore.add(Lang.msgWithDefault(
                        "warp.admin.list.warp-world",
                        "<gray>World: <yellow>%world%</yellow></gray>",
                        Map.of("world", loc.getWorld().getName()),
                        p
                ));

                lore.add(Lang.msgWithDefault(
                        "warp.admin.list.warp-coords",
                        "<gray>XYZ: <yellow>%coords%</yellow></gray>",
                        Map.of("coords", fmt(loc.getX()) + " " + fmt(loc.getY()) + " " + fmt(loc.getZ())),
                        p
                ));
            }

            lore.add(" ");

            if (protectedMode) {
                lore.add(Lang.msgWithDefault(
                        "warp.admin.list.warp-perm-protected",
                        "<gray>Permission: <gold>%perm%</gold></gray>",
                        Map.of("perm", (perm == null || perm.isBlank() ? "(custom)" : perm)),
                        p
                ));
            } else {
                lore.add(Lang.msgWithDefault(
                        "warp.admin.list.warp-perm-public",
                        "<gray>Permission: <green>public</green></gray>",
                        p
                ));
            }

            lore.add(" ");

            lore.add(Lang.msgWithDefault(
                    "warp.admin.list.warp-click",
                    "<green>Click: <white>Open actions</white></green>",
                    p
            ));

            lore.add(Lang.msgWithDefault(
                    "warp.admin.list.warp-quick-tp",
                    "<aqua>Shift-Left/Middle: <white>Quick teleport</white></aqua>",
                    p
            ));

            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private String fmt(double d) {
        return String.valueOf(Math.round(d * 10.0) / 10.0);
    }
}