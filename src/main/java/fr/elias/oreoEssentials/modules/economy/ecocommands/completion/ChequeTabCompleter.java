package fr.elias.oreoEssentials.modules.economy.ecocommands.completion;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChequeTabCompleter implements TabCompleter {

    private static final List<String> COMMON_AMOUNTS = List.of("100", "500", "1000", "5000", "10000");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        boolean canCreate = sender.hasPermission("oreo.cheque.create") || sender.hasPermission("rabbiteconomy.cheque.create");
        boolean canRedeem = sender.hasPermission("oreo.cheque.redeem") || sender.hasPermission("rabbiteconomy.cheque.redeem");

        if (args.length == 1) {
            if (canCreate) {
                completions.add("create");
                completions.addAll(COMMON_AMOUNTS);
                if (!args[0].isBlank() && args[0].matches("\\d+")) {
                    completions.add(args[0]);
                }
            }
            if (canRedeem) {
                completions.add("redeem");
            }
            return filter(completions, args[0]);
        }

        if (args.length == 2 && canCreate && args[0].equalsIgnoreCase("create")) {
            completions.addAll(COMMON_AMOUNTS);
            if (!args[1].isBlank() && args[1].matches("\\d+")) {
                completions.add(args[1]);
            }
            return filter(completions, args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> candidates, String partial) {
        String needle = partial.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle))
                .distinct()
                .toList();
    }
}
