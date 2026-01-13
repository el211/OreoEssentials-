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

public class EnchantCommand implements OreoCommand {

    @Override public String name() { return "enchant"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.enchant"; }
    @Override public String usage() { return "<enchantment> [level] [unsafe] [ignoreConflicts]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 1) {
            Lang.send(p, "admin.enchant.usage",
                    "<yellow>Usage: /%label% <enchantment> [level] [unsafe] [ignoreConflicts]</yellow>",
                    Map.of("label", label));
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Lang.send(p, "admin.enchant.hold-item",
                    "<red>You must hold an item in your main hand.</red>");
            return true;
        }

        Enchantment ench = resolve(args[0]);
        if (ench == null) {
            Lang.send(p, "admin.enchant.unknown",
                    "<red>Unknown enchantment: <yellow>%input%</yellow></red>",
                    Map.of("input", args[0]));
            return true;
        }

        int level = 1;
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        boolean unsafe = args.length >= 3 && args[2].equalsIgnoreCase("unsafe");
        boolean ignore = args.length >= 4 && args[3].equalsIgnoreCase("ignoreConflicts");

        int max = ench.getMaxLevel();
        if (level < 1) level = 1;

        if (level > max && !(unsafe || sender.hasPermission("oreo.enchant.unsafe"))) {
            Lang.send(p, "admin.enchant.too-high",
                    "<red>Level <yellow>%level%</yellow> is too high for <aqua>%ench%</aqua> (max: <white>%max%</white>). Use 'unsafe' parameter or oreo.enchant.unsafe permission.</red>",
                    Map.of("ench", enchKey(ench), "max", String.valueOf(max), "level", String.valueOf(level)));
            return true;
        }

        if (!ignore && !sender.hasPermission("oreo.enchant.ignoreconflicts")) {
            for (Enchantment ex : item.getEnchantments().keySet()) {
                if (ex.conflictsWith(ench)) {
                    Lang.send(p, "admin.enchant.conflict",
                            "<red><aqua>%ench%</aqua> conflicts with existing <aqua>%with%</aqua>. Use 'ignoreConflicts' parameter or oreo.enchant.ignoreconflicts permission.</red>",
                            Map.of("ench", enchKey(ench), "with", enchKey(ex)));
                    return true;
                }
            }
        }

        try {
            if (unsafe || sender.hasPermission("oreo.enchant.unsafe")) {
                item.addUnsafeEnchantment(ench, level);
            } else {
                item.addEnchantment(ench, Math.min(level, max));
            }
            Lang.send(p, "admin.enchant.applied",
                    "<green>Applied <aqua>%ench%</aqua> <white>%level%</white> to your item.</green>",
                    Map.of("ench", enchKey(ench), "level", String.valueOf(level)));
        } catch (Exception ex) {
            Lang.send(p, "admin.enchant.failed",
                    "<red>Failed to apply enchantment: <yellow>%reason%</yellow></red>",
                    Map.of("reason", ex.getMessage() == null ? "unknown" : ex.getMessage()));
        }

        return true;
    }

    private static String enchKey(Enchantment e) {
        return e.getKey().toString();
    }

    private static Enchantment resolve(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();

        try {
            NamespacedKey key = NamespacedKey.fromString(s);
            if (key != null) {
                Enchantment byKey = Enchantment.getByKey(key);
                if (byKey != null) return byKey;
            }
        } catch (Exception ignored) {}

        Enchantment byMc = Enchantment.getByKey(NamespacedKey.minecraft(s));
        if (byMc != null) return byMc;

        for (Enchantment e : Enchantment.values()) {
            String simple = e.getKey().getKey();
            if (simple.equalsIgnoreCase(s) || e.getKey().toString().equalsIgnoreCase(s)) {
                return e;
            }
        }

        return null;
    }
}