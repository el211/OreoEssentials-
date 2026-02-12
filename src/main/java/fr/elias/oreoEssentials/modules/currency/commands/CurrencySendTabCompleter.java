package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tab completer for /currencysend command
 * Usage: /currencysend <player> <currency> <amount>
 */
public class CurrencySendTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public CurrencySendTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            Set<String> suggestions = new HashSet<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                suggestions.add(p.getName());
            }

            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                suggestions.addAll(dir.suggestOnlineNames(prefix, 80));
            }

            if (sender instanceof Player sp) {
                suggestions.remove(sp.getName());
            }

            return suggestions.stream()
                    .filter(n -> n != null && !n.isBlank())
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase(Locale.ROOT);

            return plugin.getCurrencyService().getAllCurrencies().stream()
                    .filter(Objects::nonNull)
                    .filter(Currency::isTradeable)
                    .map(Currency::getId)
                    .filter(Objects::nonNull)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String input = args[2];
            return Arrays.asList("1", "10", "50", "100", "500", "1000").stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
