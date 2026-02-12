package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class HomeTabCompleter implements TabCompleter {
    private final HomeService homeService;

    public HomeTabCompleter(HomeService homeService) {
        this.homeService = homeService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        String partial = args[0].toLowerCase(Locale.ROOT);
        Set<String> names = fetchHomeNamesReflective(p.getUniqueId());

        return names.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Set<String> fetchHomeNamesReflective(UUID uuid) {
        List<String> methodNames = List.of("listHomes", "getHomes", "homesOf", "list", "listNames", "getNames", "allHomeNames");
        for (String mName : methodNames) {
            try {
                Method m = homeService.getClass().getMethod(mName, UUID.class);
                Object result = m.invoke(homeService, uuid);
                if (result instanceof Collection<?> col) {
                    Set<String> out = new HashSet<>();
                    for (Object o : col) if (o != null) out.add(String.valueOf(o));
                    return out;
                } else if (result instanceof Set<?>) {
                    Set<String> out = new HashSet<>();
                    for (Object o : (Set<?>) result) if (o != null) out.add(String.valueOf(o));
                    return out;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return Collections.emptySet();
    }
}