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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Displays ALL world gamerules in a paginated SmartInvs GUI.
 * Boolean gamerules: GREEN_WOOL (true) / RED_WOOL (false) — click to toggle.
 * Integer gamerules: REPEATER — L+1, R-1, Shift+L+10, Shift+R-10.
 */
public class WorldGamerulesMenu implements InventoryProvider {

    /** How many gamerule items fit per page (rows 0-3, 9 cols each). */
    private static final int ITEMS_PER_PAGE = 36;

    private final OreoEssentials plugin;
    private final ModGuiService  svc;
    private final World          world;

    /** Current page index (0-based). Mutated on prev/next click. */
    private int page = 0;

    /** Ordered list: boolean rules A-Z, then integer rules A-Z. Built once. */
    private final List<GameRule<?>> allRules;

    public WorldGamerulesMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin   = plugin;
        this.svc      = svc;
        this.world    = world;
        this.allRules = buildRuleList();
    }

    // -----------------------------------------------------------------------
    // InventoryProvider
    // -----------------------------------------------------------------------

    @Override
    public void init(Player p, InventoryContents c) {
        int totalPages = Math.max(1, (int) Math.ceil(allRules.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // Fill content area (rows 0-3)
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, allRules.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            int row  = slot / 9;
            int col  = slot % 9;
            c.set(row, col, buildItem(p, c, allRules.get(i)));
        }

        // Row 4: separator
        ItemStack sep = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int col = 0; col < 9; col++) {
            c.set(4, col, ClickableItem.empty(sep));
        }

        // Row 5: prev | info | next
        buildNavRow(p, c, totalPages);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    // -----------------------------------------------------------------------
    // Item builders
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ClickableItem buildItem(Player p, InventoryContents c, GameRule<?> rule) {
        if (rule.getType() == Boolean.class) {
            return buildBoolItem(p, c, (GameRule<Boolean>) rule);
        } else {
            return buildIntItem(p, c, (GameRule<Integer>) rule);
        }
    }

    private ClickableItem buildBoolItem(Player p, InventoryContents c, GameRule<Boolean> rule) {
        Boolean raw     = world.getGameRuleValue(rule);
        boolean value   = raw != null ? raw : true;
        Material icon   = value ? Material.GREEN_WOOL : Material.RED_WOOL;
        String   status = value ? "&aON" : "&cOFF";

        return ClickableItem.of(
                new ItemBuilder(icon)
                        .name("&f" + pretty(rule.getName()) + " &8» " + status)
                        .lore(
                                "&8" + rule.getName(),
                                "",
                                "&7State: " + status,
                                "",
                                "&7Left-click to &etoggle"
                        )
                        .build(),
                e -> {
                    world.setGameRule(rule, !value);
                    persist(rule.getName(), String.valueOf(!value));
                    c.inventory().open(p);
                }
        );
    }

    private ClickableItem buildIntItem(Player p, InventoryContents c, GameRule<Integer> rule) {
        Integer raw   = world.getGameRuleValue(rule);
        int     value = raw != null ? raw : 0;

        return ClickableItem.of(
                new ItemBuilder(Material.REPEATER)
                        .name("&b" + pretty(rule.getName()) + " &8» &e" + value)
                        .lore(
                                "&8" + rule.getName(),
                                "",
                                "&7Value: &e" + value,
                                "",
                                "&7&aLeft-click&7: +1",
                                "&7&cRight-click&7: -1",
                                "&7&aShift+Left&7: +10",
                                "&7&cShift+Right&7: -10"
                        )
                        .build(),
                e -> {
                    int delta = switch (e.getClick()) {
                        case SHIFT_LEFT  ->  10;
                        case SHIFT_RIGHT -> -10;
                        case RIGHT       ->  -1;
                        default          ->   1;
                    };
                    int next = Math.max(0, value + delta);
                    world.setGameRule(rule, next);
                    persist(rule.getName(), String.valueOf(next));
                    c.inventory().open(p);
                }
        );
    }

    // -----------------------------------------------------------------------
    // Navigation row
    // -----------------------------------------------------------------------

    private void buildNavRow(Player p, InventoryContents c, int totalPages) {
        ItemStack blank = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

        // Prev
        if (page > 0) {
            c.set(5, 0, ClickableItem.of(
                    new ItemBuilder(Material.ARROW)
                            .name("&a« Previous")
                            .lore("&7Go to page &f" + page + " &7/ &f" + totalPages)
                            .build(),
                    e -> { page--; c.inventory().open(p); }
            ));
        } else {
            c.set(5, 0, ClickableItem.empty(blank));
        }

        // Spacers
        for (int col = 1; col <= 3; col++) c.set(5, col, ClickableItem.empty(blank));
        for (int col = 5; col <= 7; col++) c.set(5, col, ClickableItem.empty(blank));

        // Page indicator
        c.set(5, 4, ClickableItem.empty(
                new ItemBuilder(Material.PAPER)
                        .name("&7Page &f" + (page + 1) + " &8/ &f" + totalPages)
                        .lore(
                                "&7World: &e" + world.getName(),
                                "&7Gamerules: &f" + allRules.size()
                        )
                        .build()
        ));

        // Next
        if (page < totalPages - 1) {
            c.set(5, 8, ClickableItem.of(
                    new ItemBuilder(Material.ARROW)
                            .name("&aNext »")
                            .lore("&7Go to page &f" + (page + 2) + " &7/ &f" + totalPages)
                            .build(),
                    e -> { page++; c.inventory().open(p); }
            ));
        } else {
            c.set(5, 8, ClickableItem.empty(blank));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Build the sorted master list: boolean rules A-Z, then integer rules A-Z. */
    @SuppressWarnings("unchecked")
    private static List<GameRule<?>> buildRuleList() {
        List<GameRule<Boolean>> bools = new ArrayList<>();
        List<GameRule<Integer>> ints  = new ArrayList<>();

        for (GameRule<?> r : GameRule.values()) {
            if      (r.getType() == Boolean.class) bools.add((GameRule<Boolean>) r);
            else if (r.getType() == Integer.class) ints.add((GameRule<Integer>)  r);
        }

        Comparator<GameRule<?>> byName =
                Comparator.comparing(GameRule::getName, String.CASE_INSENSITIVE_ORDER);
        bools.sort(byName);
        ints.sort(byName);

        List<GameRule<?>> all = new ArrayList<>(bools);
        all.addAll(ints);
        return Collections.unmodifiableList(all);
    }

    /** camelCase → "Camel Case" for display. */
    private static String pretty(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(ch));
            } else if (Character.isUpperCase(ch)) {
                sb.append(' ').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private void persist(String key, String value) {
        try { svc.cfg().setGamerule(world, key, value); } catch (Throwable ignored) {}
    }
}
