package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * /recipe — view the crafting recipe(s) for the item in your hand.
 * Opens a SmartInvs GUI showing a 3×3 grid with the ingredients and result.
 */
public class RecipeCommand implements OreoCommand {

    private final OreoEssentials plugin;

    public RecipeCommand(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override public String       name()       { return "recipe"; }
    @Override public List<String> aliases()    { return List.of("recipes"); }
    @Override public String       permission() { return "oreo.recipe"; }
    @Override public String       usage()      { return ""; }
    @Override public boolean      playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            Lang.send(p, "recipe.empty-hand", "<red>Hold an item to view its recipe.</red>");
            return true;
        }

        List<Recipe> recipes = collectRecipes(held);
        if (recipes.isEmpty()) {
            Lang.send(p, "recipe.none",
                    "<red>No crafting recipe found for <white>%item%</white>.</red>",
                    Map.of("item", held.getType().name().toLowerCase().replace('_', ' ')));
            return true;
        }

        SmartInventory.builder()
                .manager(plugin.getInvManager())
                .provider(new RecipeGuiProvider(plugin, recipes, 0))
                .title(ChatColor.DARK_GRAY + "Recipe: " + ChatColor.YELLOW
                        + held.getType().name().toLowerCase().replace('_', ' '))
                .size(4, 9)
                .build()
                .open(p);
        return true;
    }

    /** Gather shaped + shapeless + furnace recipes for the given item. */
    private static List<Recipe> collectRecipes(ItemStack item) {
        List<Recipe> found = new ArrayList<>();
        Iterator<Recipe> it = org.bukkit.Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r.getResult().getType() == item.getType()) {
                if (r instanceof ShapedRecipe || r instanceof ShapelessRecipe || r instanceof FurnaceRecipe) {
                    found.add(r);
                }
            }
        }
        return found;
    }

    // -----------------------------------------------------------------------
    // GUI provider
    // -----------------------------------------------------------------------

    private static final class RecipeGuiProvider implements InventoryProvider {

        private final OreoEssentials plugin;
        private final List<Recipe>   recipes;
        private final int            index;

        RecipeGuiProvider(OreoEssentials plugin, List<Recipe> recipes, int index) {
            this.plugin  = plugin;
            this.recipes = recipes;
            this.index   = Math.max(0, Math.min(index, recipes.size() - 1));
        }

        // Layout (4 rows × 9 cols):
        // Row 0: [pane][G0 ][G1 ][G2 ][pane][ARROW][pane][RESULT][pane]
        // Row 1: [pane][G3 ][G4 ][G5 ][pane][pane ][pane][pane  ][pane]
        // Row 2: [pane][G6 ][G7 ][G8 ][pane][pane ][pane][pane  ][pane]
        // Row 3: [PREV][pane][pane][pane][INFO][pane][pane][pane][NEXT]

        @Override
        public void init(Player p, InventoryContents c) {
            ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

            // Fill everything with pane first
            for (int row = 0; row < 4; row++)
                for (int col = 0; col < 9; col++)
                    c.set(row, col, ClickableItem.empty(pane));

            Recipe recipe = recipes.get(index);
            ItemStack[] grid = extractGrid(recipe);

            // Place grid at cols 1-3, rows 0-2
            for (int i = 0; i < 9; i++) {
                int row = i / 3;
                int col = (i % 3) + 1;
                ItemStack slot = grid[i];
                c.set(row, col, ClickableItem.empty(
                        slot != null ? slot : new ItemBuilder(Material.AIR).build()));
            }

            // Arrow at row 0, col 5
            c.set(0, 5, ClickableItem.empty(
                    new ItemBuilder(Material.ARROW).name("&7Recipe →").build()));

            // Result at row 0, col 7
            ItemStack result = recipe.getResult();
            ItemStack displayResult = result.clone();
            appendLore(displayResult, "&7This is the crafting result.");
            c.set(0, 7, ClickableItem.empty(displayResult));

            // Recipe type badge at row 1, col 5
            String typeLabel = recipeTypeLabel(recipe);
            c.set(1, 5, ClickableItem.empty(
                    new ItemBuilder(Material.BOOK).name("&b" + typeLabel).build()));

            // Navigation row 3
            if (index > 0) {
                c.set(3, 0, ClickableItem.of(
                        new ItemBuilder(Material.ARROW).name("&aPrevious Recipe")
                                .lore("&7Recipe " + index + " / " + recipes.size()).build(),
                        e -> open(p, index - 1)
                ));
            }
            c.set(3, 4, ClickableItem.empty(
                    new ItemBuilder(Material.PAPER)
                            .name("&7Recipe &f" + (index + 1) + " &7/ &f" + recipes.size()).build()));
            if (index < recipes.size() - 1) {
                c.set(3, 8, ClickableItem.of(
                        new ItemBuilder(Material.ARROW).name("&aNext Recipe")
                                .lore("&7Recipe " + (index + 2) + " / " + recipes.size()).build(),
                        e -> open(p, index + 1)
                ));
            }
        }

        @Override public void update(Player player, InventoryContents contents) {}

        private void open(Player p, int newIndex) {
            SmartInventory.builder()
                    .manager(plugin.getInvManager())
                    .provider(new RecipeGuiProvider(plugin, recipes, newIndex))
                    .title(ChatColor.DARK_GRAY + "Recipe " + (newIndex + 1) + "/" + recipes.size())
                    .size(4, 9)
                    .build()
                    .open(p);
        }

        /** Extract a 9-slot grid from the recipe (null = empty slot). */
        private static ItemStack[] extractGrid(Recipe recipe) {
            ItemStack[] grid = new ItemStack[9];
            if (recipe instanceof ShapedRecipe shaped) {
                String[] shape = shaped.getShape();
                Map<Character, ItemStack> ing = shaped.getIngredientMap();
                for (int row = 0; row < shape.length && row < 3; row++) {
                    for (int col = 0; col < shape[row].length() && col < 3; col++) {
                        char ch = shape[row].charAt(col);
                        grid[row * 3 + col] = (ch == ' ') ? null : ing.getOrDefault(ch, null);
                    }
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                List<ItemStack> ing = shapeless.getIngredientList();
                for (int i = 0; i < Math.min(9, ing.size()); i++) {
                    grid[i] = ing.get(i);
                }
            } else if (recipe instanceof FurnaceRecipe furnace) {
                grid[3] = furnace.getInput(); // center-left
                grid[4] = new ItemStack(Material.BLAZE_POWDER); // fuel hint
            }
            return grid;
        }

        private static String recipeTypeLabel(Recipe r) {
            if (r instanceof ShapedRecipe)    return "Shaped Crafting";
            if (r instanceof ShapelessRecipe) return "Shapeless Crafting";
            if (r instanceof FurnaceRecipe)   return "Smelting";
            return "Recipe";
        }

        private static void appendLore(ItemStack item, String line) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
}
