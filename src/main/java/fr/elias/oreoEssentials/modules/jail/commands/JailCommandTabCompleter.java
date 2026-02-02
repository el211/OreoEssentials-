package fr.elias.oreoEssentials.modules.jail.commands;

import fr.elias.oreoEssentials.modules.jail.JailService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JailCommandTabCompleter implements TabCompleter {
    private final JailService service;

    public JailCommandTabCompleter(JailService service) {
        this.service = service;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // /jail <player|release>
            suggestions.add("release");
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            // /jail release <player>
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
        }
        else if (args.length == 3) {
            // /jail <player> <time> <jailName>
            service.allJails().keySet().forEach(suggestions::add);
        }
        else if (args.length == 4) {
            // /jail <player> <time> <jailName> <cellId>
            String jailName = args[2].toLowerCase(Locale.ROOT);
            var jail = service.allJails().get(jailName);
            if (jail != null) {
                jail.cells.keySet().forEach(suggestions::add);
            }
        }
        else if (args.length >= 5) {
            // /jail <player> <time> <jailName> <cellId> [-s] [r:reason]
            suggestions.add("-s");
            suggestions.add("r:");
        }

        // Filter by what user is typing
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}