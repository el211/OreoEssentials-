package fr.elias.oreoEssentials.modules.near;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NearGuiProvider implements InventoryProvider {

    private final OreoEssentials plugin;
    private final int radius;

    public NearGuiProvider(OreoEssentials plugin, int radius) {
        this.plugin = plugin;
        this.radius = radius;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        // Fill border with glass panes
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int col = 0; col < 9; col++) {
            c.set(0, col, ClickableItem.empty(pane));
            c.set(5, col, ClickableItem.empty(pane));
        }
        for (int row = 1; row < 5; row++) {
            c.set(row, 0, ClickableItem.empty(pane));
            c.set(row, 8, ClickableItem.empty(pane));
        }

        Location me = p.getLocation();
        List<Player> nearby = new ArrayList<>();
        for (Player other : p.getWorld().getPlayers()) {
            if (other == p) continue;
            if (other.getLocation().distance(me) <= radius) nearby.add(other);
        }
        nearby.sort(Comparator.comparingDouble(o -> o.getLocation().distance(me)));

        // Info header
        c.set(0, 4, ClickableItem.empty(
                new ItemBuilder(Material.COMPASS)
                        .name("&6Nearby Players &8(&e" + nearby.size() + "&8)")
                        .lore("&7Radius: &e" + radius + " blocks")
                        .build()
        ));

        if (nearby.isEmpty()) {
            c.set(2, 4, ClickableItem.empty(
                    new ItemBuilder(Material.BARRIER)
                            .name("&cNo players nearby")
                            .lore("&7Nobody within &e" + radius + " &7blocks.")
                            .build()
            ));
            return;
        }

        // Build clickable items
        List<ClickableItem> items = new ArrayList<>();
        for (Player other : nearby) {
            double dist = Math.round(other.getLocation().distance(me) * 10) / 10.0;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(other);
                meta.setDisplayName("§b" + other.getName());
                meta.setLore(List.of(
                        "§7Distance: §e" + dist + "m",
                        "§7World: §f" + other.getWorld().getName(),
                        "§7X§8: §f" + other.getLocation().getBlockX()
                        + " §7Y§8: §f" + other.getLocation().getBlockY()
                        + " §7Z§8: §f" + other.getLocation().getBlockZ(),
                        "",
                        "§7Left-click to §atelepath to this player"
                ));
                skull.setItemMeta(meta);
            }

            items.add(ClickableItem.of(skull, e -> {
                if (!p.hasPermission("oreo.tp")) return;
                p.teleport(other.getLocation());
                p.sendMessage("§aTeleported to §b" + other.getName() + "§a.");
            }));
        }

        Pagination pagination = c.pagination();
        pagination.setItems(items.toArray(new ClickableItem[0]));
        pagination.setItemsPerPage(28); // 4 rows × 7 cols (cols 1-7, rows 1-4)

        SlotIterator it = c.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0).blacklist(1, 8).blacklist(2, 0).blacklist(2, 8)
          .blacklist(3, 0).blacklist(3, 8).blacklist(4, 0).blacklist(4, 8);
        pagination.addToIterator(it);

        // Nav
        if (!pagination.isFirst()) {
            c.set(5, 0, ClickableItem.of(
                    new ItemBuilder(Material.ARROW).name("&aPrevious").build(),
                    e -> openPage(p, pagination.previous().getPage())
            ));
        }
        if (!pagination.isLast()) {
            c.set(5, 8, ClickableItem.of(
                    new ItemBuilder(Material.ARROW).name("&aNext").build(),
                    e -> openPage(p, pagination.next().getPage())
            ));
        }
        c.set(5, 4, ClickableItem.empty(
                new ItemBuilder(Material.PAPER)
                        .name("&7Page &f" + (pagination.getPage() + 1)).build()
        ));
    }

    @Override public void update(Player p, InventoryContents c) {}

    private void openPage(Player p, int page) {
        fr.minuskube.inv.SmartInventory.builder()
                .manager(plugin.getInvManager())
                .provider(new NearGuiProvider(plugin, radius))
                .title("§6Nearby Players §7(radius: " + radius + ")")
                .size(6, 9)
                .build()
                .open(p);
    }
}
