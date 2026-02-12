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
        setupBooleanGamerules(c);
        setupIntegerGamerules(c);
    }

    private void setupBooleanGamerules(InventoryContents c) {
        createBooleanToggle(c, 1, 2, "doDaylightCycle", GameRule.DO_DAYLIGHT_CYCLE, true);
        createBooleanToggle(c, 1, 4, "doWeatherCycle", GameRule.DO_WEATHER_CYCLE, true);
        createBooleanToggle(c, 1, 6, "doMobSpawning", GameRule.DO_MOB_SPAWNING, true);
        createBooleanToggle(c, 2, 3, "keepInventory", GameRule.KEEP_INVENTORY, false);
        createBooleanToggle(c, 2, 5, "doImmediateRespawn", GameRule.DO_IMMEDIATE_RESPAWN, false);
    }

    private void setupIntegerGamerules(InventoryContents c) {
        createIntegerCycle(c, 3, 4, "randomTickSpeed", GameRule.RANDOM_TICK_SPEED, 3,
                new int[]{0, 1, 3, 6, 12, 20});
    }

    private void createBooleanToggle(InventoryContents c, int row, int col, String key,
                                     GameRule<Boolean> rule, boolean defaultValue) {
        Boolean current = world.getGameRuleValue(rule);
        if (current == null) current = defaultValue;
        final boolean value = current;

        Material icon = value ? Material.LIME_DYE : Material.GRAY_DYE;
        String label = "&b" + key + ": " + (value ? "&aON" : "&cOFF");

        c.set(row, col, ClickableItem.of(
                new ItemBuilder(icon)
                        .name(label)
                        .lore("&7Click to toggle")
                        .build(),
                e -> {
                    boolean next = !value;
                    world.setGameRule(rule, next);
                    persistGamerule(key, String.valueOf(next));
                    refreshInventory(e.getWhoClicked(), c);
                }
        ));
    }

    private void createIntegerCycle(InventoryContents c, int row, int col, String key,
                                    GameRule<Integer> rule, int defaultValue, int[] cycleValues) {
        Integer current = world.getGameRuleValue(rule);
        if (current == null) current = defaultValue;
        final int value = current;

        c.set(row, col, ClickableItem.of(
                new ItemBuilder(Material.REPEATER)
                        .name("&b" + key + ": &e" + value)
                        .lore("&7Click to cycle: " + Arrays.toString(cycleValues))
                        .build(),
                e -> {
                    int next = getNextCycleValue(value, cycleValues);
                    world.setGameRule(rule, next);
                    persistGamerule(key, String.valueOf(next));
                    refreshInventory(e.getWhoClicked(), c);
                }
        ));
    }

    private int getNextCycleValue(int current, int[] cycleValues) {
        int currentIndex = 0;
        for (int i = 0; i < cycleValues.length; i++) {
            if (cycleValues[i] == current) {
                currentIndex = i;
                break;
            }
        }
        return cycleValues[(currentIndex + 1) % cycleValues.length];
    }

    private void persistGamerule(String key, String value) {
        try {
            svc.cfg().setGamerule(world, key, value);
        } catch (Throwable ignored) {
        }
    }

    private void refreshInventory(org.bukkit.entity.HumanEntity entity, InventoryContents c) {
        if (entity instanceof Player viewer) {
            c.inventory().open(viewer);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}