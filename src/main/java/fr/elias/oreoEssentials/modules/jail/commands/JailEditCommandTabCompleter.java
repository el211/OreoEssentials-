package fr.elias.oreoEssentials.modules.jail.commands;

import fr.elias.oreoEssentials.modules.jail.JailService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JailEditCommandTabCompleter implements TabCompleter {
    private final JailService service;

    public JailEditCommandTabCompleter(JailService service) {
        this.service = service;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // /jailedit <subcommand>
            suggestions.add("pos1");
            suggestions.add("pos2");
            suggestions.add("create");
            suggestions.add("save");
            suggestions.add("addcell");
            suggestions.add("delete");
            suggestions.add("remove");
        }
        else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            if (sub.equals("create") || sub.equals("save")) {
                // /jailedit create <jailName> - suggest existing jail names for editing
                service.allJails().keySet().forEach(suggestions::add);
                suggestions.add("<new_jail_name>");
            }
            else if (sub.equals("addcell") || sub.equals("delete") || sub.equals("remove")) {
                // /jailedit addcell|delete <jailName>
                service.allJails().keySet().forEach(suggestions::add);
            }
        }
        else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            if (sub.equals("addcell")) {
                // /jailedit addcell <jailName> <cellId>
                String jailName = args[1].toLowerCase(Locale.ROOT);
                var jail = service.allJails().get(jailName);
                if (jail != null) {
                    jail.cells.keySet().forEach(suggestions::add);
                }
                suggestions.add("<new_cell_id>");
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