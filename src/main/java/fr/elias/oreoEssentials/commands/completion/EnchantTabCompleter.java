package fr.elias.oreoEssentials.commands.completion;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EnchantTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 1) {
            String cur = a[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (Enchantment e : Enchantment.values()) {
                NamespacedKey k = e.getKey();
                list.add(k.getKey());
                list.add(k.toString());
            }
            return list.stream().distinct().filter(s -> s.toLowerCase().startsWith(cur)).limit(100).toList();
        }
        if (a.length == 2) {
            // suggest some common levels
            return Arrays.asList("1","2","3","4","5","10","255");
        }
        if (a.length == 3) return List.of("unsafe");
        if (a.length == 4) return List.of("ignoreConflicts");
        return List.of();
    }
}
