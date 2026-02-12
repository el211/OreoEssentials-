package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.Map;

public class PerfToolsMenu implements InventoryProvider {

    private final OreoEssentials plugin;

    public PerfToolsMenu(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        setupKillMobsButton(p, contents);
        setupClearItemsButton(p, contents);
        setupPurgeTntButton(p, contents);
    }

    private void setupKillMobsButton(Player p, InventoryContents contents) {
        contents.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.IRON_SWORD)
                        .name("&cKill all hostile mobs")
                        .lore("&7Removes all monsters in all worlds.")
                        .build(),
                e -> {
                    int removed = killHostileMobs();
                    Lang.send(p, "modgui.perf.kill-mobs",
                            "<green>Removed <yellow>%count%</yellow> monsters from all worlds.</green>",
                            Map.of("count", String.valueOf(removed)));
                }
        ));
    }

    private void setupClearItemsButton(Player p, InventoryContents contents) {
        contents.set(1, 5, ClickableItem.of(
                new ItemBuilder(Material.HOPPER)
                        .name("&6Clear dropped items")
                        .lore("&7Removes all item entities in all worlds.")
                        .build(),
                e -> {
                    int removed = clearDroppedItems();
                    Lang.send(p, "modgui.perf.clear-items",
                            "<green>Removed <yellow>%count%</yellow> dropped items from all worlds.</green>",
                            Map.of("count", String.valueOf(removed)));
                }
        ));
    }

    private void setupPurgeTntButton(Player p, InventoryContents contents) {
        contents.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.TNT)
                        .name("&4Purge primed TNT")
                        .lore("&7Removes all primed TNT entities.")
                        .build(),
                e -> {
                    int removed = clearPrimedTnt();
                    Lang.send(p, "modgui.perf.clear-tnt",
                            "<green>Removed <yellow>%count%</yellow> primed TNT from all worlds.</green>",
                            Map.of("count", String.valueOf(removed)));
                }
        ));
    }

    private int killHostileMobs() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Monster) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    private int clearDroppedItems() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    private int clearPrimedTnt() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TNTPrimed) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}