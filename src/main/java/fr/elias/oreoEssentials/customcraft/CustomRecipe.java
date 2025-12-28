// File: src/main/java/fr/elias/oreoEssentials/customcraft/CustomRecipe.java
package fr.elias.oreoEssentials.customcraft;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CustomRecipe {
    private final ItemStack[] grid;     // 9 items, row-major (0..8)
    private final ItemStack result;
    private final String name;
    private final boolean shapeless;
    private final String permission;

    /* ---------------- Constructors ---------------- */

    public CustomRecipe(String name, ItemStack[] grid, ItemStack result) {
        this(name, grid, result, false, null);
    }

    public CustomRecipe(String name, ItemStack[] grid, ItemStack result, boolean shapeless) {
        this(name, grid, result, shapeless, null);
    }

    public CustomRecipe(String name, ItemStack[] grid, ItemStack result, boolean shapeless, String permission) {
        if (grid == null || grid.length != 9) throw new IllegalArgumentException("grid must be length 9");
        this.name = Objects.requireNonNull(name, "name");
        this.grid = Arrays.copyOf(grid, 9);
        this.result = result;
        this.shapeless = shapeless;
        this.permission = (permission != null && !permission.isBlank()) ? permission : null;
    }

    /* ---------------- Getters ---------------- */

    public String getName() { return name; }

    public ItemStack[] getGrid() { return Arrays.copyOf(grid, 9); }

    public ItemStack getResult() { return result == null ? null : result.clone(); }

    public boolean isShapeless() { return shapeless; }

    /** Returns the exact permission node required to craft, or null if public. */
    public String getPermission() { return permission; }

    /* ---------------- Logic ---------------- */

    public boolean isValid() {
        if (result == null || result.getType().isAir()) return false;
        for (ItemStack it : grid) {
            if (it != null && !it.getType().isAir()) return true;
        }
        return false;
    }

    /** For shaped recipes: compute minimal bounding box containing all non-air cells. */
    public Bounds computeBounds() {
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack it = grid[i];
            if (it != null && !it.getType().isAir()) {
                int r = i / 3, c = i % 3;
                if (r < minR) minR = r;
                if (r > maxR) maxR = r;
                if (c < minC) minC = c;
                if (c > maxC) maxC = c;
            }
        }
        if (maxR == -1) return new Bounds(0, 0, 0, 0);
        return new Bounds(minR, maxR, minC, maxC);
    }

    public record Bounds(int minRow, int maxRow, int minCol, int maxCol) {
        public int rows() { return maxRow - minRow + 1; }
        public int cols() { return maxCol - minCol + 1; }
    }

    public List<ItemStack> asList() { return Arrays.asList(getGrid()); }
}
