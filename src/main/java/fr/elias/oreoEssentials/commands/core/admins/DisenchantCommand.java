// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/DisenchantCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DisenchantCommand implements OreoCommand {

    @Override public String name() { return "disenchant"; }
    @Override public List<String> aliases() { return List.of("unenchant", "deench"); }
    @Override public String permission() { return "oreo.disenchant"; }
    @Override public String usage() { return "<enchantment|all> [levels-to-remove]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 1) {
            Lang.send(p, "admin.disenchant.usage", null, Map.of("label", label));
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Lang.send(p, "admin.enchant.hold-item", null, null);
            return true;
        }

        if (item.getEnchantments().isEmpty()) {
            Lang.send(p, "admin.disenchant.none", null, null);
            return true;
        }

        String target = args[0].toLowerCase(Locale.ROOT).trim();

        // Remove ALL enchantments
        if (target.equals("all") || target.equals("*")) {
            item.getEnchantments().keySet().forEach(item::removeEnchantment);
            Lang.send(p, "admin.disenchant.all", null, null);
            return true;
        }

        Enchantment ench = resolve(target);
        if (ench == null) {
            Lang.send(p, "admin.enchant.unknown", null, Map.of("input", args[0]));
            return true;
        }

        if (!item.getEnchantments().containsKey(ench)) {
            Lang.send(p, "admin.disenchant.not-present", null, Map.of("ench", enchKey(ench)));
            return true;
        }

        int current = item.getEnchantments().get(ench);
        int remove = 1;

        if (args.length >= 2) {
            try {
                remove = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                // why: ignore invalid numbers, default to 1
            }
        }

        int newLevel = current - remove;

        if (newLevel > 0) {
            // reapply at reduced level
            item.addUnsafeEnchantment(ench, newLevel);
            Lang.send(p, "admin.disenchant.partial", null, Map.of(
                    "ench", enchKey(ench),
                    "removed", String.valueOf(remove),
                    "remaining", String.valueOf(newLevel)
            ));
        } else {
            item.removeEnchantment(ench);
            Lang.send(p, "admin.disenchant.removed", null, Map.of("ench", enchKey(ench)));
        }

        return true;
    }

    private static String enchKey(Enchantment e) {
        // prefer namespaced key string (e.g., minecraft:sharpness or plugin:custom)
        return e.getKey().toString();
    }

    private static Enchantment resolve(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();

        // Try exact namespaced key (plugin:ench or minecraft:ench)
        try {
            NamespacedKey key = NamespacedKey.fromString(s);
            if (key != null) {
                Enchantment byKey = Enchantment.getByKey(key);
                if (byKey != null) return byKey;
            }
        } catch (Exception ignored) {}

        // Try minecraft:<s>
        Enchantment byMc = Enchantment.getByKey(NamespacedKey.minecraft(s));
        if (byMc != null) return byMc;

        // Try matching by key key only (simple id)
        for (Enchantment e : Enchantment.values()) {
            String simple = e.getKey().getKey(); // non-deprecated
            if (simple.equalsIgnoreCase(s) || e.getKey().toString().equalsIgnoreCase(s)) {
                return e;
            }
        }
        return null;
    }
}
