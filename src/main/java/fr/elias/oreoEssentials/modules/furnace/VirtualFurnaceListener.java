package fr.elias.oreoEssentials.modules.furnace;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VirtualFurnaceListener implements Listener {

    private static final Map<UUID, OreTask> tasks = new HashMap<>();
    private static final Map<UUID, Integer> progress = new HashMap<>();

    private static final int COOK_TIME = 200;

    public static void startTask(OreoEssentials plugin, Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        stopTask(uuid);
        progress.put(uuid, 0);

        OreTask task = OreScheduler.runTimer(plugin, () -> {
            if (!player.isOnline()
                    || player.getOpenInventory().getTopInventory().getType() != InventoryType.FURNACE
                    || !player.getOpenInventory().getTopInventory().equals(inv)) {
                stopTask(uuid);
                return;
            }

            ItemStack ingredient = inv.getItem(0);
            ItemStack fuel       = inv.getItem(1);
            ItemStack result     = inv.getItem(2);

            if (ingredient == null || ingredient.getType().isAir()
                    || fuel == null || fuel.getType().isAir()) {
                progress.put(uuid, 0);
                updateFurnaceAnimation(player, 0, COOK_TIME, 0, COOK_TIME);
                return;
            }

            ItemStack smeltResult = getSmeltResult(plugin, ingredient);
            if (smeltResult == null) {
                progress.put(uuid, 0);
                updateFurnaceAnimation(player, 0, COOK_TIME, 0, COOK_TIME);
                return;
            }

            int currentProgress = progress.getOrDefault(uuid, 0) + 1;


            updateFurnaceAnimation(player, currentProgress, COOK_TIME, COOK_TIME, COOK_TIME);

            if (currentProgress >= COOK_TIME) {
                if (result == null || result.getType().isAir()) {
                    inv.setItem(2, smeltResult.clone());
                } else if (result.isSimilar(smeltResult)
                        && result.getAmount() < result.getMaxStackSize()) {
                    result.setAmount(result.getAmount() + smeltResult.getAmount());
                    inv.setItem(2, result);
                } else {

                    return;
                }

                if (ingredient.getAmount() > 1) {
                    ingredient.setAmount(ingredient.getAmount() - 1);
                    inv.setItem(0, ingredient);
                } else {
                    inv.setItem(0, null);
                }

                if (fuel.getAmount() > 1) {
                    fuel.setAmount(fuel.getAmount() - 1);
                    inv.setItem(1, fuel);
                } else {
                    inv.setItem(1, null);
                }

                progress.put(uuid, 0);

                ItemStack newFuel = inv.getItem(1);
                if (newFuel == null || newFuel.getType().isAir()) {
                    updateFurnaceAnimation(player, 0, COOK_TIME, 0, COOK_TIME);
                }

            } else {
                progress.put(uuid, currentProgress);
            }
        }, 1L, 1L);

        tasks.put(uuid, task);
    }

    private static void updateFurnaceAnimation(Player player,
                                               int cookProgress, int cookDuration,
                                               int burnTime, int burnTimeTotal) {
        try {
            InventoryView view = player.getOpenInventory();
            if (view == null) return;

            view.setProperty(InventoryView.Property.BURN_TIME, burnTime);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_FUEL, burnTimeTotal);

            view.setProperty(InventoryView.Property.COOK_TIME, cookProgress);
            view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_SMELTING, cookDuration);
        } catch (Throwable ignored) {}
    }

    public static void stopTask(UUID uuid) {
        OreTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
        progress.remove(uuid);
    }

    private static ItemStack getSmeltResult(OreoEssentials plugin, ItemStack ingredient) {
        ItemStack single = ingredient.clone();
        single.setAmount(1);


        int totalRecipes = 0;
        int furnaceRecipes = 0;

        var iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            var recipe = iterator.next();
            totalRecipes++;

            if (!(recipe instanceof FurnaceRecipe furnaceRecipe)) continue;
            furnaceRecipes++;

            boolean matches = furnaceRecipe.getInputChoice().test(single);
            if (matches) {
                return furnaceRecipe.getResult().clone();
            }
        }

        return null;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.FURNACE) return;
        if (event.getInventory().getHolder() != player) return;

        stopTask(player.getUniqueId());

        Inventory inv = event.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item).values()
                        .forEach(leftover -> player.getWorld()
                                .dropItemNaturally(player.getLocation(), leftover));
                inv.setItem(slot, null);
            }
        }
    }
}