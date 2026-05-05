package fr.elias.oreoEssentials.modules.warnings;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /unwarn <player> <warn-id|all>
 */
public class UnwarnCommand implements OreoCommand, TabCompleter {

    private final WarnService warnService;

    public UnwarnCommand(WarnService warnService) {
        this.warnService = warnService;
    }

    @Override public String       name()       { return "unwarn"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.unwarn"; }
    @Override public String       usage()      { return "<player> <warn-id|all>"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            Lang.send(sender, "unwarn.usage", "<red>Usage: /unwarn <player> <warn-id|all></red>");
            return true;
        }

        String targetName = args[0];
        String warnId     = args[1];

        // Resolve player
        UUID targetUuid;
        String displayName;
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            targetUuid  = online.getUniqueId();
            displayName = online.getName();
        } else {
            OfflinePlayer op = resolveOffline(targetName);
            if (op == null || !op.hasPlayedBefore()) {
                Lang.send(sender, "unwarn.not-found",
                        "<red>Player <white>%player%</white> not found.</red>",
                        Map.of("player", targetName));
                return true;
            }
            targetUuid  = op.getUniqueId();
            displayName = op.getName() != null ? op.getName() : targetName;
        }

        if (warnId.equalsIgnoreCase("all")) {
            warnService.clearAll(targetUuid);
            Lang.send(sender, "unwarn.cleared",
                    "<green>All warnings cleared for <aqua>%player%</aqua>.</green>",
                    Map.of("player", displayName));
        } else {
            boolean removed = warnService.removeById(targetUuid, warnId);
            if (removed) {
                Lang.send(sender, "unwarn.removed",
                        "<green>Warning <yellow>%id%</yellow> removed from <aqua>%player%</aqua>.</green>",
                        Map.of("id", warnId, "player", displayName));
            } else {
                Lang.send(sender, "unwarn.not-found-id",
                        "<red>No warning with id <yellow>%id%</yellow> found for <aqua>%player%</aqua>.</red>",
                        Map.of("id", warnId, "player", displayName));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            // Suggest warn ids for the given player + "all"
            Player online = Bukkit.getPlayerExact(args[0]);
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            if (online != null) {
                warnService.getActive(online.getUniqueId()).stream()
                        .map(WarnService.WarnEntry::id)
                        .forEach(suggestions::add);
            }
            String typed = args[1].toLowerCase(Locale.ROOT);
            return suggestions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static OfflinePlayer resolveOffline(String name) {
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }
}
