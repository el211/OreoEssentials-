package fr.elias.oreoEssentials.modules.warps;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class WarpTabCompleter implements TabCompleter {
    private final WarpService warpService;

    public WarpTabCompleter(WarpService warpService) {
        this.warpService = warpService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        Set<String> names = fetchWarpNamesReflective();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }

    private Set<String> fetchWarpNamesReflective() {
        List<String> methodNames = List.of("listWarps", "getWarps", "warps", "list", "listNames", "getNames");
        for (String mName : methodNames) {
            try {
                Method m = warpService.getClass().getMethod(mName);
                Object result = m.invoke(warpService);
                if (result instanceof Collection<?> col) {
                    Set<String> out = new HashSet<>();
                    for (Object o : col) if (o != null) out.add(String.valueOf(o));
                    return out;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return Collections.emptySet();
    }
}
