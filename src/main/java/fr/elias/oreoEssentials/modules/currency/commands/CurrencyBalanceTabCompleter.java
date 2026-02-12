package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer for /currencybalance command
 */
public class CurrencyBalanceTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public CurrencyBalanceTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return plugin.getCurrencyService().getAllCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}