// File: src/main/java/fr/elias/oreoEssentials/modgui/menu/WorldGamerulesMenu.java
package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class WorldGamerulesMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    public WorldGamerulesMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin;
        this.svc = svc;
        this.world = world;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        // High-value boolean gamerule toggles
        toggleBoolean(c, 1, 2, "doDaylightCycle", GameRule.DO_DAYLIGHT_CYCLE, true);
        toggleBoolean(c, 1, 4, "doWeatherCycle", GameRule.DO_WEATHER_CYCLE, true);
        toggleBoolean(c, 1, 6, "doMobSpawning", GameRule.DO_MOB_SPAWNING, true);
        toggleBoolean(c, 2, 3, "keepInventory", GameRule.KEEP_INVENTORY, false);
        toggleBoolean(c, 2, 5, "doImmediateRespawn", GameRule.DO_IMMEDIATE_RESPAWN, false);

        // Integer cycle (randomTickSpeed)
        // Common values: 0 (frozen), 1 (very slow), 3 (default), 6, 12, 20 (very fast)
        toggleInteger(c, 3, 4, "randomTickSpeed", GameRule.RANDOM_TICK_SPEED, 3,
                new int[]{0, 1, 3, 6, 12, 20});
    }

    /**
     * Create a toggle button for a boolean gamerule.
     * Shows current state, toggles on click, and refreshes GUI.
     */
    private void toggleBoolean(InventoryContents c, int row, int col, String key, GameRule<Boolean> rule, boolean def) {
        Boolean current = world.getGameRuleValue(rule);
        if (current == null) current = def;
        final boolean cur = current;

        Material icon = cur ? Material.LIME_DYE : Material.GRAY_DYE;
        String label = "&b" + key + ": " + (cur ? "&aON" : "&cOFF");

        c.set(row, col, ClickableItem.of(
                new ItemBuilder(icon)
                        .name(label)
                        .lore("&7Click to toggle")
                        .build(),
                e -> {
                    boolean next = !cur;
                    world.setGameRule(rule, next);

                    // Persist to config (optional)
                    try {
                        svc.cfg().setGamerule(world, key, String.valueOf(next));
                    } catch (Throwable ignored) {}

                    // Refresh GUI for the clicking player
                    if (e.getWhoClicked() instanceof Player viewer) {
                        c.inventory().open(viewer);
                    }
                }
        ));
    }

    /**
     * Create a cycle button for an integer gamerule.
     * Shows current value, cycles through predefined values on click.
     */
    private void toggleInteger(InventoryContents c, int row, int col, String key,
                               GameRule<Integer> rule, int def, int[] cycle) {
        Integer current = world.getGameRuleValue(rule);
        if (current == null) current = def;
        final int cur = current;

        c.set(row, col, ClickableItem.of(
                new ItemBuilder(Material.REPEATER)
                        .name("&b" + key + ": &e" + cur)
                        .lore("&7Click to cycle: " + Arrays.toString(cycle))
                        .build(),
                e -> {
                    // Find current value in cycle array
                    int idx = 0;
                    for (int i = 0; i < cycle.length; i++) {
                        if (cycle[i] == cur) {
                            idx = i;
                            break;
                        }
                    }

                    // Advance to next value (wrap around)
                    int next = cycle[(idx + 1) % cycle.length];

                    world.setGameRule(rule, next);

                    // Persist to config (optional)
                    try {
                        svc.cfg().setGamerule(world, key, String.valueOf(next));
                    } catch (Throwable ignored) {}

                    // Refresh GUI
                    if (e.getWhoClicked() instanceof Player viewer) {
                        c.inventory().open(viewer);
                    }
                }
        ));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No periodic updates needed - GUI refreshes on click
    }
}