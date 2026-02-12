package fr.elias.oreoEssentials.modules.customcraft;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomCraftingService {
    private final Plugin plugin;
    private final RecipesStorage storage;
    private final Map<String, CustomRecipe> recipes = new ConcurrentHashMap<>();
    private final Map<String, NamespacedKey> keys = new ConcurrentHashMap<>();

    private final boolean iaPresent;
    private final Class<?> iaCustomStackClass;
    private final Method iaByItemStack;
    private final Method iaGetNamespacedID;

    public CustomCraftingService(Plugin plugin) {
        this.plugin = plugin;
        this.storage = new RecipesStorage(plugin);

        boolean found = false;
        Class<?> cs = null;
        Method byIS = null;
        Method getId = null;
        try {
            cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            byIS = cs.getMethod("byItemStack", ItemStack.class);
            getId = cs.getMethod("getNamespacedID");
            found = (cs != null && byIS != null && getId != null);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        this.iaPresent = found;
        this.iaCustomStackClass = cs;
        this.iaByItemStack = byIS;
        this.iaGetNamespacedID = getId;

        if (iaPresent) plugin.getLogger().info("[CustomCraft] ItemsAdder detected; IA-aware matching enabled.");
        else            plugin.getLogger().info("[CustomCraft] ItemsAdder not present; using vanilla matching.");
    }


    public void loadAllAndRegister() {
        recipes.clear();
        recipes.putAll(storage.loadAll());
        unregisterAll();
        recipes.values().forEach(this::registerBukkitRecipe);
        plugin.getLogger().info("[CustomCraft] Loaded " + recipes.size() + " custom recipes.");
    }

    public boolean saveAndRegister(CustomRecipe r) {
        if (!r.isValid()) return false;
        try {
            storage.save(r);
            recipes.put(r.getName(), r);
            unregister(r.getName());
            registerBukkitRecipe(r);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[CustomCraft] Failed saving recipe " + r.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public Optional<CustomRecipe> get(String name) { return Optional.ofNullable(recipes.get(name)); }
    public Set<String> allNames() { return new TreeSet<>(recipes.keySet()); }


    public int getRecipeCount() {
        return recipes.size();
    }

    public boolean delete(String name) {
        try {
            storage.delete(name);
            recipes.remove(name);
            unregister(name);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[CustomCraft] Failed deleting recipe " + name + ": " + e.getMessage());
            return false;
        }
    }


    public Optional<String> getRecipeNameByKey(NamespacedKey key) {
        for (var e : keys.entrySet()) {
            if (e.getValue().equals(key)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    public Optional<String> getPermissionFor(String name) {
        CustomRecipe r = recipes.get(name);
        if (r == null) return Optional.empty();
        String p = r.getPermission();
        return (p == null || p.isBlank()) ? Optional.empty() : Optional.of(p);
    }


    private void registerBukkitRecipe(CustomRecipe r) {
        if (r.getResult() == null || r.getResult().getType().isAir()) return;

        NamespacedKey key = new NamespacedKey(plugin, "oecraft_" + r.getName().toLowerCase(Locale.ROOT));
        Bukkit.removeRecipe(key);

        if (r.isShapeless()) {
            ShapelessRecipe sr = new ShapelessRecipe(key, r.getResult().clone());

            List<ItemStack> items = new ArrayList<>();
            for (ItemStack it : r.getGrid())
                if (it != null && !it.getType().isAir()) items.add(it.clone());

            List<ItemStack> uniques = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (ItemStack it : items) {
                int idx = indexOfSimilar(uniques, it);
                if (idx < 0) { uniques.add(it); counts.add(1); }
                else counts.set(idx, counts.get(idx) + 1);
            }

            for (int i = 0; i < uniques.size(); i++) {
                ItemStack sample = uniques.get(i);
                int times = counts.get(i);
                for (int n = 0; n < times; n++) sr.addIngredient(new RecipeChoice.ExactChoice(sample.clone()));
            }

            Bukkit.addRecipe(sr);
            keys.put(r.getName(), key);
            return;
        }

        CustomRecipe.Bounds b = r.computeBounds();
        if (b.rows() <= 0 || b.cols() <= 0) return;

        List<String> shape = new ArrayList<>();
        Map<Character, RecipeChoice> map = new HashMap<>();
        char nextKey = 'A';

        for (int rr = b.minRow(); rr <= b.maxRow(); rr++) {
            StringBuilder line = new StringBuilder();
            for (int cc = b.minCol(); cc <= b.maxCol(); cc++) {
                int idx = rr * 3 + cc;
                ItemStack it = r.getGrid()[idx];
                if (it == null || it.getType().isAir()) {
                    line.append(' ');
                } else {
                    Character found = null;
                    for (var e : map.entrySet()) {
                        RecipeChoice v = e.getValue();
                        if (v instanceof RecipeChoice.ExactChoice ec &&
                                ec.getChoices().size() == 1 &&
                                similar(it, ec.getChoices().get(0))) {
                            found = e.getKey();
                            break;
                        }
                    }
                    char ch = (found != null) ? found : nextKey++;
                    if (found == null) map.put(ch, new RecipeChoice.ExactChoice(it.clone()));
                    line.append(ch);
                }
            }
            shape.add(line.toString());
        }

        ShapedRecipe sr = new ShapedRecipe(key, r.getResult().clone());
        sr.shape(shape.toArray(new String[0]));
        for (var e : map.entrySet()) sr.setIngredient(e.getKey(), e.getValue());
        Bukkit.addRecipe(sr);
        keys.put(r.getName(), key);
    }


    private int indexOfSimilar(List<ItemStack> list, ItemStack probe) {
        for (int i = 0; i < list.size(); i++) {
            if (similar(probe, list.get(i))) return i;
        }
        return -1;
    }

    private boolean similar(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.isSimilar(b)) return true;

        if (iaPresent) {
            String iaA = iaId(a);
            if (iaA == null) return false;
            String iaB = iaId(b);
            return iaA.equals(iaB);
        }
        return false;
    }

    private String iaId(ItemStack stack) {
        if (!iaPresent || stack == null) return null;
        try {
            Object cs = iaByItemStack.invoke(null, stack); // static
            if (cs == null) return null;
            return (String) iaGetNamespacedID.invoke(cs);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private void unregister(String name) {
        NamespacedKey k = keys.remove(name);
        if (k != null) Bukkit.removeRecipe(k);
    }

    private void unregisterAll() {
        for (NamespacedKey k : keys.values()) Bukkit.removeRecipe(k);
        keys.clear();
    }


    public Plugin getPlugin() {
        return plugin;
    }
}