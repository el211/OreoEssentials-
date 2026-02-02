package fr.elias.oreoEssentials.modules.jail.commands;

import fr.elias.oreoEssentials.modules.jail.JailService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JailListCommandTabCompleter implements TabCompleter {
    private final JailService service;

    public JailListCommandTabCompleter(JailService service) {
        this.service = service;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // /jaillist <jailName>
            service.allJails().keySet().forEach(suggestions::add);
        }
        else if (args.length == 2) {
            // /jaillist <jailName> <cellId>
            String jailName = args[0].toLowerCase(Locale.ROOT);
            var jail = service.allJails().get(jailName);
            if (jail != null) {
                jail.cells.keySet().forEach(suggestions::add);
                suggestions.add("*"); // Show all cells
            }
        }

        // Filter by what user is typing
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}