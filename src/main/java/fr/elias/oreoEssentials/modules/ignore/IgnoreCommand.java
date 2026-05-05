package fr.elias.oreoEssentials.modules.ignore;

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

public class IgnoreCommand implements OreoCommand, TabCompleter {

    private final IgnoreService ignoreService;

    public IgnoreCommand(IgnoreService ignoreService) {
        this.ignoreService = ignoreService;
    }

    @Override public String       name()       { return "ignore"; }
    @Override public List<String> aliases()    { return List.of(); }
    @Override public String       permission() { return "oreo.ignore"; }
    @Override public String       usage()      { return "<player>|list|clear"; }
    @Override public boolean      playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length == 0) {
            Lang.send(p, "ignore.usage",
                    "<yellow>/ignore <player> — toggle ignore\n/ignore list — show ignored players\n/ignore clear — clear all</yellow>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                Set<UUID> list = ignoreService.getIgnoredBy(p.getUniqueId());
                if (list.isEmpty()) {
                    Lang.send(p, "ignore.list.empty", "<gray>Your ignore list is empty.</gray>");
                    return true;
                }
                Lang.send(p, "ignore.list.header", "<gold>Ignored players:</gold>");
                for (UUID uuid : list) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String name = op.getName() != null ? op.getName() : uuid.toString();
                    p.sendMessage("  §7• §e" + name);
                }
            }
            case "clear" -> {
                ignoreService.clearIgnoreList(p.getUniqueId());
                Lang.send(p, "ignore.clear", "<green>Your ignore list has been cleared.</green>");
            }
            default -> {
                // Toggle ignore for the named player
                String targetName = args[0];
                OfflinePlayer target = resolveOffline(targetName);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    Lang.send(p, "ignore.not-found",
                            "<red>Player <white>%player%</white> not found.</red>",
                            Map.of("player", targetName));
                    return true;
                }
                if (target.getUniqueId().equals(p.getUniqueId())) {
                    Lang.send(p, "ignore.self", "<red>You cannot ignore yourself.</red>");
                    return true;
                }
                String displayName = target.getName() != null ? target.getName() : targetName;
                boolean added = ignoreService.ignore(p.getUniqueId(), target.getUniqueId());
                if (added) {
                    Lang.send(p, "ignore.added",
                            "<green>Now ignoring <aqua>%player%</aqua>. They cannot send you private messages.</green>",
                            Map.of("player", displayName));
                } else {
                    // already ignored → unignore (toggle)
                    ignoreService.unignore(p.getUniqueId(), target.getUniqueId());
                    Lang.send(p, "ignore.removed",
                            "<yellow>No longer ignoring <aqua>%player%</aqua>.</yellow>",
                            Map.of("player", displayName));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            List<String> opts = new ArrayList<>(List.of("list", "clear"));
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .forEach(opts::add);
            return opts.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }
}
