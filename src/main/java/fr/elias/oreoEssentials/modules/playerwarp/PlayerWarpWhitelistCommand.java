package fr.elias.oreoEssentials.modules.playerwarp;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerWarpWhitelistCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("add", "remove");

    private final PlayerWarpService service;

    public PlayerWarpWhitelistCommand(PlayerWarpService service) {
        this.service = service;
    }

    // ---------------------- EXECUTE ----------------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Lang.send(sender, "playerwarps.whitelist.player-only",
                    "<red>Only players may use this command.</red>");
            return true;
        }

        if (args.length < 3) {
            Lang.send(player, "playerwarps.whitelist.usage",
                    "<yellow>Usage: /%label% <add|remove> <warpname> <player></yellow>",
                    Map.of("label", label));
            return true;
        }

        String action     = args[0].toLowerCase(Locale.ROOT);
        String warpName   = args[1];
        String targetName = args[2];

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        switch (action) {
            case "add" -> service.addToWhitelist(player, warpName, target);
            case "remove", "rem", "del" -> service.removeFromWhitelist(player, warpName, target);
            default -> Lang.send(player, "playerwarps.whitelist.invalid-action",
                    "<red>Invalid action. Use <yellow>add</yellow> or <yellow>remove</yellow>.</red>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return ACTIONS.stream()
                    .filter(a -> a.startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);

            return service.listByOwner(player.getUniqueId()).stream()
                    .map(PlayerWarp::getName)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .distinct()
                    .filter(name -> name.startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String action = args[0].toLowerCase(Locale.ROOT);
            String warpNameRaw = args[1];
            String partial = args[2].toLowerCase(Locale.ROOT);

            if (action.equals("add")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(Objects::nonNull)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }

            if (action.equals("remove") || action.equals("rem") || action.equals("del")) {
                PlayerWarp warp = service.findByOwnerAndName(
                        player.getUniqueId(),
                        warpNameRaw.toLowerCase(Locale.ROOT)
                );
                if (warp == null || warp.getWhitelist() == null || warp.getWhitelist().isEmpty()) {
                    return Collections.emptyList();
                }

                return warp.getWhitelist().stream()
                        .map(uuid -> {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            return op != null ? op.getName() : null;
                        })
                        .filter(Objects::nonNull)
                        .distinct()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}