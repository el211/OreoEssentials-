package fr.elias.oreoEssentials.modules.homes.home;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.homes.ConfirmDeleteGui;
import fr.elias.oreoEssentials.modules.homes.home.HomeService.StoredHome;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class HomesGuiProvider implements InventoryProvider {

    private final HomeService homes;


    public HomesGuiProvider(HomeService homes) {
        this.homes = homes;
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

        Map<String, StoredHome> data = safeListHomes(p.getUniqueId());
        List<String> names = new ArrayList<>(data.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        int used = names.size();
        int max = guessMaxHomes(p);

        contents.set(0, 4, ClickableItem.empty(counterItem(p, used, max)));

        contents.set(0, 8, ClickableItem.of(refreshItem(p), e -> {
            contents.inventory().open(p, contents.pagination().getPage());
        }));

        Pagination pagination = contents.pagination();
        int pageSize = 28;

        ClickableItem[] items = names.stream().map(name -> {
            StoredHome sh = data.get(name);
            String server = (sh != null && sh.getServer() != null) ? sh.getServer() : homes.localServer();
            ItemStack it = homeItem(p, name, server, sh);
            return ClickableItem.of(it, e -> {
                switch (e.getClick()) {
                    case LEFT -> {
                        HomesGuiCommand.crossServerTeleport(homes, p, name);
                    }
                    case RIGHT -> {
                        contents.inventory().close(p);
                        ConfirmDeleteGui.open(p, homes, name, () -> {
                            reopenHomesGui(p, pagination.getPage());
                        }, () -> {
                            reopenHomesGui(p, pagination.getPage());
                        });
                    }
                    default -> {}
                }
            });
        }).toArray(ClickableItem[]::new);

        pagination.setItems(items);
        pagination.setItemsPerPage(pageSize);

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0); it.blacklist(1, 8);
        it.blacklist(2, 0); it.blacklist(2, 8);
        it.blacklist(3, 0); it.blacklist(3, 8);
        it.blacklist(4, 0); it.blacklist(4, 8);
        pagination.addToIterator(it);

        if (!pagination.isFirst()) {
            contents.set(5, 0, ClickableItem.of(navItem(p, Material.ARROW, "homes.gui.nav.previous"), e ->
                    contents.inventory().open(p, pagination.previous().getPage())));
        }
        if (!pagination.isLast()) {
            contents.set(5, 8, ClickableItem.of(navItem(p, Material.ARROW, "homes.gui.nav.next"), e ->
                    contents.inventory().open(p, pagination.next().getPage())));
        }
    }


    private Map<String, StoredHome> safeListHomes(UUID owner) {
        try {
            Map<String, StoredHome> m = homes.listHomes(owner);
            return (m == null) ? Collections.emptyMap() : m;
        } catch (Throwable t) {
            return Collections.emptyMap();
        }
    }

    private int guessMaxHomes(Player p) {
        try {
            var cfg = OreoEssentials.get().getConfigService();
            if (cfg != null) {
                try {
                    var m = cfg.getClass().getMethod("getMaxHomesFor", Player.class);
                    Object r = m.invoke(cfg, p);
                    if (r instanceof Number n) return n.intValue();
                } catch (NoSuchMethodException ignore) {}
                try {
                    var m2 = cfg.getClass().getMethod("defaultMaxHomes");
                    Object r2 = m2.invoke(cfg);
                    if (r2 instanceof Number n2) return n2.intValue();
                } catch (NoSuchMethodException ignore) {}
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private void reopenHomesGui(Player p, int page) {
        String title = Lang.msgLegacy("homes.gui.title",
                "<dark_green>Your Homes</dark_green>",
                p);

        SmartInventory.builder()
                .id("oreo:homes")
                .provider(new HomesGuiProvider(homes))
                .size(6, 9)
                .title(title)
                .manager(OreoEssentials.get().getInvManager())
                .build()
                .open(p, page);
    }


    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
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
            meta.setDisplayName(Lang.msgLegacy("homes.gui.refresh.title",
                    "<yellow>Refresh</yellow>", p));
            meta.setLore(List.of(
                    Lang.msgLegacy("homes.gui.refresh.lore",
                            "<gray>Click to reload homes.</gray>", p)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack counterItem(Player p, int used, int max) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        String cap = (max > 0) ? used + "/" + max : used + "/?";
        if (meta != null) {
            meta.setDisplayName(Lang.msgLegacy("homes.gui.counter.title",
                    "<gold>Homes <white>(%count%)</white></gold>",
                    Map.of("count", cap),
                    p));
            meta.setLore(List.of(
                    Lang.msgLegacy("homes.gui.counter.lore.0",
                            "<gray>Left-click a home to teleport.</gray>", p),
                    Lang.msgLegacy("homes.gui.counter.lore.1",
                            "<gray>Right-click a home to delete.</gray>", p)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack navItem(Player p, Material type, String langKey) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String name = Lang.msgLegacy(langKey,
                    "<yellow>Page</yellow>",
                    p);
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack homeItem(Player p, String name, String server, StoredHome sh) {
        ItemStack it = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.msgLegacy("homes.gui.home.title",
                    "<aqua>%name%</aqua>",
                    Map.of("name", name),
                    p));

            List<String> lore = new ArrayList<>();
            lore.add(Lang.msgLegacy("homes.gui.home.lore.server",
                    "<gray>Server: <yellow>%server%</yellow></gray>",
                    Map.of("server", server),
                    p));

            if (sh != null) {
                lore.add(Lang.msgLegacy("homes.gui.home.lore.world",
                        "<gray>World: <yellow>%world%</yellow></gray>",
                        Map.of("world", sh.getWorld()),
                        p));

                String coords = fmt(sh.getX()) + " " + fmt(sh.getY()) + " " + fmt(sh.getZ());
                lore.add(Lang.msgLegacy("homes.gui.home.lore.coords",
                        "<gray>XYZ: <yellow>%coords%</yellow></gray>",
                        Map.of("coords", coords),
                        p));
            }

            lore.add(" ");
            lore.add(Lang.msgLegacy("homes.gui.home.lore.left-click",
                    "<green>Left-Click: <white>Teleport</white></green>", p));
            lore.add(Lang.msgLegacy("homes.gui.home.lore.right-click",
                    "<red>Right-Click: <white>Delete</white></red>", p));

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