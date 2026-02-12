package fr.elias.oreoEssentials.modules.aliases;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

final class DynamicAliasTabCompleter implements TabCompleter {

    private final AliasService service;
    private final String alias;

    DynamicAliasTabCompleter(AliasService service, String alias) {
        this.service = service;
        this.alias = alias.toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        AliasService.AliasDef def = service.get(alias);
        if (def == null || !def.addTabs) return List.of();

        int argIndex = Math.max(0, args.length - 1);
        if (argIndex >= def.customTabs.size()) return List.of();

        String token = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> group = def.customTabs.get(argIndex);
        if (group == null || group.isEmpty()) return List.of();
        return group.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(token))
                .sorted()
                .collect(Collectors.toList());
    }
}
