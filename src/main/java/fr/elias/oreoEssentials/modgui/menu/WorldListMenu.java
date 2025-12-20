package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Comparator;

public class WorldListMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;

    public WorldListMenu(OreoEssentials plugin, ModGuiService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        var worlds = Bukkit.getWorlds().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int row = 1, col = 1;
        for (World w : worlds) {
            Material icon = switch (w.getEnvironment()) {
                case NORMAL -> Material.GRASS_BLOCK;
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.MAP;
            };

            c.set(row, col, ClickableItem.of(
                    new ItemBuilder(icon)
                            .name("&a" + w.getName())
                            .lore("&7Click to manage this world")
                            .build(),
                    e -> SmartInventory.builder()
                            .manager(plugin.getInvManager()) //  IMPORTANT: set manager
                            .provider(new WorldActionsMenu(plugin, svc, w))
                            .title("§8World: " + w.getName())
                            .size(6, 9)
                            .build()
                            .open(p)
            ));

            if (++col >= 8) { col = 1; row++; if (row >= 5) break; }
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}
