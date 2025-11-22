// src/main/java/fr/elias/oreoEssentials/commands/completion/PlayerWarpTabCompleter.java
package fr.elias.oreoEssentials.commands.completion;

import fr.elias.oreoEssentials.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.playerwarp.PlayerWarpService;
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
        // /pw
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            // Base subcommands
            List<String> base = new ArrayList<>(List.of("help", "set", "remove", "list"));

            // Add ALL warp names (global) so "/pw <tab>" shows every warp
            for (PlayerWarp warp : safeListAll()) {
                if (warp.getName() != null && !warp.getName().isBlank()) {
                    base.add(warp.getName());
                }
            }

            return base.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        // /pw remove <warp>
        if (args.length == 2 && "remove".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                return Collections.emptyList();
            }

            String prefix = args[1].toLowerCase(Locale.ROOT);

            // Only show warps owned by the player
            return playerWarpService.listByOwner(player.getUniqueId()).stream()
                    .map(PlayerWarp::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        // /pw list <player>
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);

            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    // -------- Helpers --------

    private List<PlayerWarp> safeListAll() {
        try {
            List<PlayerWarp> list = playerWarpService.listAll();
            return (list != null) ? list : Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }
}
