package fr.elias.oreoEssentials.modules.economy.ecocommands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class PayTabCompleter implements TabCompleter {
    private final OreoEssentials plugin;

    public PayTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            Set<String> suggestions = new HashSet<>();

            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));

            var dir = plugin.getPlayerDirectory(); // <-- whatever getter you use
            if (dir != null) {
                suggestions.addAll(dir.suggestOnlineNames(prefix, 80));
            }

            return suggestions.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2) {
            return List.of("10", "50", "100", "250", "1000").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }

        return List.of();
    }


}
