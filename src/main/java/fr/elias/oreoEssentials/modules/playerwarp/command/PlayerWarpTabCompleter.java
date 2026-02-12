package fr.elias.oreoEssentials.modules.playerwarp.command;

import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerWarpTabCompleter implements TabCompleter {

    private final PlayerWarpService playerWarpService;

    public PlayerWarpTabCompleter(PlayerWarpService playerWarpService) {
        this.playerWarpService = playerWarpService;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        String cmdName = command.getName();
        if (!cmdName.equalsIgnoreCase("pw")
                && !cmdName.equalsIgnoreCase("playerwarp")
                && !cmdName.equalsIgnoreCase("pwarp")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            List<String> base = new ArrayList<>(Arrays.asList(
                    "help",
                    "set",
                    "remove",
                    "list",
                    "amount",
                    "rtp",
                    "near",
                    "reset",
                    "rename",
                    "setowner",
                    "removeall",
                    "reload",
                    "addwarps",

                    // meta / extras
                    "desc",
                    "open",
                    "icon",
                    "category",
                    "rate",
                    "lock",
                    "cost",
                    "password",
                    "whitelist",
                    "ban",
                    "managers",
                    "favourite",
                    "gui",
                    "mywarps",


                    "use"
            ));

            for (PlayerWarp warp : safeListAll()) {
                if (warp.getName() != null && !warp.getName().isBlank()) {
                    base.add(warp.getName());
                }
            }

            return base.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "remove", "reset", "rename" -> {
                    if (!(sender instanceof Player player)) {
                        return Collections.emptyList();
                    }
                    return safeListOwned(player).stream()
                            .map(PlayerWarp::getName)
                            .filter(Objects::nonNull)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                }

                case "whitelist" -> {
                    List<String> actions = Arrays.asList("enable", "disable", "list", "set", "remove");
                    return actions.stream()
                            .filter(a -> a.startsWith(prefix))
                            .collect(Collectors.toList());
                }

                case "use" -> {
                    return safeListAll().stream()
                            .map(PlayerWarp::getName)
                            .filter(Objects::nonNull)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                }


                case "setowner",
                     "desc",
                     "icon",
                     "category",
                     "rate",
                     "lock",
                     "cost",
                     "password",
                     "ban",
                     "managers",
                     "favourite" -> {
                    return safeListAll().stream()
                            .map(PlayerWarp::getName)
                            .filter(Objects::nonNull)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                }

                case "list",
                     "amount",
                     "removeall",
                     "addwarps" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                }

                case "near" -> {
                    List<String> pages = Arrays.asList("1", "2", "3", "4", "5");
                    return pages.stream()
                            .filter(p -> p.startsWith(prefix))
                            .collect(Collectors.toList());
                }

                default -> {
                    return Collections.emptyList();
                }
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[2].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "whitelist" -> {
                    String action = args[1].toLowerCase(Locale.ROOT);
                    if (!Arrays.asList("enable", "disable", "list", "set", "remove").contains(action)) {
                        return Collections.emptyList();
                    }

                    return safeListAll().stream()
                            .map(PlayerWarp::getName)
                            .filter(Objects::nonNull)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                }

                case "setowner",
                     "ban",
                     "managers",
                     "favourite" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                }

                case "addwarps" -> {
                    List<String> amounts = Arrays.asList("1", "3", "5", "10", "20");
                    return amounts.stream()
                            .filter(a -> a.startsWith(prefix))
                            .collect(Collectors.toList());
                }

                case "use" -> {
                    return Collections.emptyList();
                }

                default -> {
                    return Collections.emptyList();
                }
            }
        }

        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String action = args[1].toLowerCase(Locale.ROOT);
            String prefix = args[3].toLowerCase(Locale.ROOT);

            if ("whitelist".equals(sub) && (action.equals("set") || action.equals("remove"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }

            return Collections.emptyList();
        }

        return Collections.emptyList();
    }


    private List<PlayerWarp> safeListAll() {
        try {
            List<PlayerWarp> list = playerWarpService.listAll();
            return (list != null) ? list : Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private List<PlayerWarp> safeListOwned(Player player) {
        try {
            List<PlayerWarp> list = playerWarpService.listByOwner(player.getUniqueId());
            return (list != null) ? list : Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }
}
