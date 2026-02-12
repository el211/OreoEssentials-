package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Tab completer for /currencytop command
 */
public class CurrencyTopTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public CurrencyTopTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null) return List.of();

        if (args.length == 1) {
            String input = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);

            return plugin.getCurrencyService().getAllCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id != null && id.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String input = args[1] == null ? "" : args[1].toLowerCase(Locale.ROOT);

            return Arrays.asList("1", "2", "3", "4", "5").stream()
                    .filter(p -> p.startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
