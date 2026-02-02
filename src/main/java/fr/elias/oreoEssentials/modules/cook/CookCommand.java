package fr.elias.oreoEssentials.modules.cook;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CookCommand implements OreoCommand {

    @Override public String name() { return "cook"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.cook"; }
    @Override public String usage() { return "[amount|max]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            Lang.send(p, "cook.no-item",
                    "<red>Hold the item you want to smelt in your main hand.</red>");
            return true;
        }

        CookingRecipe<?> match = findCookingRecipeFor(hand);
        if (match == null) {
            Lang.send(p, "cook.no-recipe",
                    "<red>No smelting recipe found for that item.</red>");
            return true;
        }

        int request;
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("max") || args[0].equalsIgnoreCase("all")) {
                request = hand.getAmount();
            } else {
                try {
                    request = Math.max(1, Integer.parseInt(args[0]));
                } catch (NumberFormatException ex) {
                    Lang.send(p, "cook.invalid-amount",
                            "<red>Amount must be a number or 'max'.</red>");
                    return true;
                }
            }
        } else {
            request = 1;
        }

        int canCook = Math.min(request, hand.getAmount());
        if (canCook <= 0) {
            Lang.send(p, "cook.nothing-to-smelt",
                    "<red>Nothing to smelt.</red>");
            return true;
        }

        ItemStack resultProto = match.getResult().clone();
        int perItemOut = Math.max(1, resultProto.getAmount());
        int totalOut = perItemOut * canCook;

        hand.setAmount(hand.getAmount() - canCook);
        p.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);

        ItemStack give = resultProto.clone();
        give.setAmount(Math.min(give.getMaxStackSize(), totalOut));
        var leftover = p.getInventory().addItem(give);

        int remaining = totalOut - give.getAmount();
        while (remaining > 0) {
            ItemStack more = resultProto.clone();
            int add = Math.min(more.getMaxStackSize(), remaining);
            more.setAmount(add);
            leftover.putAll(p.getInventory().addItem(more));
            remaining -= add;
            if (!leftover.isEmpty()) break; // inventory full
        }

        if (!leftover.isEmpty()) {
            leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            Lang.send(p, "cook.inventory-full",
                    "<yellow>Your inventory was full; dropped some smelted items at your feet.</yellow>");
        }

        p.playSound(p.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8f, 1.0f);

        String itemName = resultProto.getType().name().toLowerCase().replace('_', ' ');

        Lang.send(p, "cook.success",
                "<green>Smelted <aqua>%input%</aqua> → <aqua>%output%</aqua> <gray>%item%</gray>.</green>",
                Map.of(
                        "input", String.valueOf(canCook),
                        "output", String.valueOf(totalOut),
                        "item", itemName
                ));

        return true;
    }

    private CookingRecipe<?> findCookingRecipeFor(ItemStack input) {
        Iterator<Recipe> it = org.bukkit.Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof CookingRecipe<?> cr) {
                RecipeChoice choice = cr.getInputChoice();
                try {
                    if (choice != null && choice.test(input)) {
                        return cr;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}