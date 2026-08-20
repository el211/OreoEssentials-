package fr.elias.oreoEssentials.modules.aliases;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class DynamicAliasTabCompleter implements TabCompleter {

    private final AliasService service;
    private final String alias;
    /** Source of currently online players. Injected to allow testing without a live server. */
    private final Supplier<Collection<? extends Player>> playerSource;

    DynamicAliasTabCompleter(AliasService service, String alias) {
        this(service, alias, Bukkit::getOnlinePlayers);
    }

    /** Package-private overload used by unit tests to inject a mock player source. */
    DynamicAliasTabCompleter(AliasService service, String alias,
                              Supplier<Collection<? extends Player>> playerSource) {
        this.service = Objects.requireNonNull(service, "service");
        this.alias = alias.toLowerCase(Locale.ROOT);
        this.playerSource = Objects.requireNonNull(playerSource, "playerSource");
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

        // Expand dynamic tokens then filter + deduplicate + sort.
        // [players] (and its legacy alias [playerName]) expand to currently
        // online player names at completion time — never at config-load time.
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : group) {
            if (entry == null) continue;
            if (entry.equalsIgnoreCase("[players]") || entry.equalsIgnoreCase("[playerName]")) {
                for (Player p : playerSource.get()) {
                    seen.add(p.getName());
                }
            } else {
                seen.add(entry);
            }
        }

        return seen.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(token))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
