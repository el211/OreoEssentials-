package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;


public class WorldWhitelistMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    public WorldWhitelistMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin;
        this.svc = svc;
        this.world = world;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        boolean enabled = svc.cfg().worldWhitelistEnabled(world);

        c.set(1, 4, ClickableItem.of(
                new ItemBuilder(enabled ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name("&fWhitelist: " + (enabled ? "&aENABLED" : "&cDISABLED"))
                        .lore("&7Click to " + (enabled ? "disable" : "enable"))
                        .build(),
                e -> {
                    svc.cfg().setWorldWhitelistEnabled(world, !enabled);
                    if (e.getWhoClicked() instanceof Player viewer) {
                        c.inventory().open(viewer); // Refresh
                    }
                }
        ));

        c.set(5, 8, ClickableItem.of(
                new ItemBuilder(Material.ARROW)
                        .name("&7Back")
                        .lore("&7Return to world actions")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldActionsMenu(plugin, svc, world))
                        .title(Lang.color(Lang.get("modgui.world-whitelist.back-title", "&8World: %world%")
                                .replace("%world%", world.getName())))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        int row = 3, col = 1;

        Set<java.util.UUID> allowed = svc.cfg().worldWhitelist(world);
        if (allowed == null) allowed = new HashSet<>();

        var online = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (Player t : online) {
            boolean whitelisted = allowed.contains(t.getUniqueId());

            c.set(row, col, ClickableItem.of(
                    new ItemBuilder(whitelisted ? Material.WHITE_WOOL : Material.BLACK_WOOL)
                            .name((whitelisted ? "&a" : "&7") + t.getName())
                            .lore("&7Click to " + (whitelisted ? "remove from" : "add to") + " whitelist")
                            .build(),
                    e -> {
                        if (whitelisted) {
                            svc.cfg().removeWorldWhitelist(world, t.getUniqueId());
                        } else {
                            svc.cfg().addWorldWhitelist(world, t.getUniqueId());
                        }
                        if (e.getWhoClicked() instanceof Player viewer) {
                            c.inventory().open(viewer); // Refresh
                        }
                    }
            ));

            if (++col >= 8) {
                col = 1;
                row++;
                if (row >= 5) break;
            }
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}