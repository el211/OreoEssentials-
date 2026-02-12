package fr.elias.oreoEssentials.modules.currency.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.currency.Currency;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;


public class CurrencyCommandTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public CurrencyCommandTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                    "create", "delete", "list", "info",
                    "give", "take", "set", "reload"
            ));
            return filter(completions, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "create" -> {
                if (args.length == 2) {
                    return List.of();
                }
                if (args.length == 3) {
                    completions.add("<name>");
                }
                if (args.length == 4) {
                    completions.addAll(Arrays.asList("M", "$", "€", "¥", "G", "T"));
                }
            }

            case "delete", "info" -> {
                if (args.length == 2) {
                    completions.addAll(getCurrencyIds());
                }
            }

            case "give", "take", "set" -> {
                if (args.length == 2) {
                    completions.addAll(getNetworkPlayerNames(sender, args[1]));
                } else if (args.length == 3) {
                    completions.addAll(getCurrencyIds());
                } else if (args.length == 4) {
                    completions.addAll(Arrays.asList("1", "10", "100", "1000"));
                }
            }
        }

        return filter(completions, args[args.length - 1]);
    }


    private List<String> getCurrencyIds() {
        return plugin.getCurrencyService()
                .getAllCurrencies()
                .stream()
                .filter(Objects::nonNull)
                .map(Currency::getId)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }


    private Collection<String> getNetworkPlayerNames(CommandSender sender, String prefixRaw) {
        String prefix = (prefixRaw == null) ? "" : prefixRaw.toLowerCase(Locale.ROOT);

        Set<String> suggestions = new HashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            suggestions.add(p.getName());
        }

        PlayerDirectory dir = null;
        try {
            dir = plugin.getPlayerDirectory();
        } catch (Throwable ignored) {}

        if (dir != null) {
            try {
                suggestions.addAll(dir.suggestOnlineNames(prefix, 80));
            } catch (Throwable ignored) {}
        }


        return suggestions.stream()
                .filter(Objects::nonNull)
                .filter(n -> !n.isBlank())
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String input) {
        String lower = (input == null) ? "" : input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(Objects::nonNull)
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
