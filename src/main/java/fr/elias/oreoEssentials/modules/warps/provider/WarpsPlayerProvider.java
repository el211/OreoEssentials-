package fr.elias.oreoEssentials.modules.warps.provider;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.modules.warps.commands.WarpsAdminCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class WarpsPlayerProvider implements InventoryProvider {

    private final WarpService warps;

    public WarpsPlayerProvider(WarpService warps) {
        this.warps = warps;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        draw(p, contents);
    }

    @Override
    public void update(Player p, InventoryContents contents) {}

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
                try {
                    loc = warps.getWarp(key);
                } catch (Throwable ignored) {}
            }

            final boolean allowed = warps.canUse(p, key);
            ItemStack icon = warpPlayerItem(p, displayName, server, loc, allowed);

            return ClickableItem.of(icon, e -> {
                if (!allowed) {
                    Lang.send(p, "warp.player.no-permission",
                            "<red>You don't have permission for this warp.</red>");
                    return;
                }
                WarpsAdminCommand.crossServerTeleport(warps, p, displayName);
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
                    "warp.player.list.previous",
                    "<yellow>Previous Page</yellow>",
                    p
            );
            contents.set(5, 0, ClickableItem.of(nav(Material.ARROW, prevName),
                    e -> contents.inventory().open(p, pagination.previous().getPage())));
        }

        if (!pagination.isLast()) {
            String nextName = Lang.msgWithDefault(
                    "warp.player.list.next",
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
                    "warp.player.list.refresh",
                    "<yellow>Refresh</yellow>",
                    p
            );

            String lore = Lang.msgWithDefault(
                    "warp.player.list.refresh-lore",
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
                    "warp.player.list.counter",
                    "<gold>Warps <white>(%count%)</white></gold>",
                    Map.of("count", String.valueOf(count)),
                    p
            );

            String lore = Lang.msgWithDefault(
                    "warp.player.list.counter-lore",
                    "<gray>Left-click: <white>Teleport</white></gray>",
                    p
            );

            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
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

    private ItemStack warpPlayerItem(Player p, String name, String server, Location loc, boolean allowed) {
        ItemStack it = new ItemStack(allowed ? Material.LODESTONE : Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String displayName;
            if (allowed) {
                displayName = Lang.msgWithDefault(
                        "warp.player.list.warp-name-allowed",
                        "<aqua>%name%</aqua>",
                        Map.of("name", name),
                        p
                );
            } else {
                displayName = Lang.msgWithDefault(
                        "warp.player.list.warp-name-locked",
                        "<dark_red>%name%</dark_red>",
                        Map.of("name", name),
                        p
                );
            }

            List<String> lore = new ArrayList<>();

            // Server line
            lore.add(Lang.msgWithDefault(
                    "warp.player.list.warp-server",
                    "<gray>Server: <yellow>%server%</yellow></gray>",
                    Map.of("server", server),
                    p
            ));

            // World and coordinates (if available)
            if (loc != null) {
                lore.add(Lang.msgWithDefault(
                        "warp.player.list.warp-world",
                        "<gray>World: <yellow>%world%</yellow></gray>",
                        Map.of("world", loc.getWorld().getName()),
                        p
                ));

                lore.add(Lang.msgWithDefault(
                        "warp.player.list.warp-coords",
                        "<gray>XYZ: <yellow>%coords%</yellow></gray>",
                        Map.of("coords", fmt(loc.getX()) + " " + fmt(loc.getY()) + " " + fmt(loc.getZ())),
                        p
                ));
            }

            lore.add(" ");

            // Action hint
            if (allowed) {
                lore.add(Lang.msgWithDefault(
                        "warp.player.list.warp-click",
                        "<green>Left-Click: <white>Teleport</white></green>",
                        p
                ));
            } else {
                lore.add(Lang.msgWithDefault(
                        "warp.player.list.warp-locked",
                        "<red>You don't have access.</red>",
                        p
                ));
            }

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