package fr.elias.oreoEssentials.modules.warnings;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /warnings [player] — view active warnings for self or others.
 */
public class WarningsCommand implements OreoCommand, TabCompleter {

    private final WarnService warnService;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public WarningsCommand(WarnService warnService) {
        this.warnService = warnService;
    }

    @Override public String       name()       { return "warnings"; }
    @Override public List<String> aliases()    { return List.of("checkwarns"); }
    @Override public String       permission() { return "oreo.warnings"; }
    @Override public String       usage()      { return "[player]"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        UUID targetUuid;
        String displayName;

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "warnings.console-needs-player", "<red>Specify a player name.</red>");
                return true;
            }
            targetUuid  = p.getUniqueId();
            displayName = p.getName();
        } else {
            if (!sender.hasPermission("oreo.warnings.others")) {
                Lang.send(sender, "warnings.no-perm-others", "<red>You don't have permission to check others' warnings.</red>");
                return true;
            }
            String name = args[0];
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                targetUuid  = online.getUniqueId();
                displayName = online.getName();
            } else {
                OfflinePlayer op = resolveOffline(name);
                if (op == null || !op.hasPlayedBefore()) {
                    Lang.send(sender, "warnings.not-found",
                            "<red>Player <white>%player%</white> not found.</red>",
                            Map.of("player", name));
                    return true;
                }
                targetUuid  = op.getUniqueId();
                displayName = op.getName() != null ? op.getName() : name;
            }
        }

        List<WarnService.WarnEntry> active = warnService.getActive(targetUuid);
        int max = warnService.getMaxWarnings();

        if (active.isEmpty()) {
            Lang.send(sender, "warnings.none",
                    "<green><aqua>%player%</aqua> has no active warnings.</green>",
                    Map.of("player", displayName));
            return true;
        }

        Lang.send(sender, "warnings.header",
                "<gold>Warnings for <aqua>%player%</aqua>: <yellow>%count%/%max%</yellow></gold>",
                Map.of("player", displayName,
                       "count", String.valueOf(active.size()),
                       "max",   String.valueOf(max)));

        for (int i = 0; i < active.size(); i++) {
            WarnService.WarnEntry e = active.get(i);
            String date = SDF.format(new Date(e.issuedAt()));
            String expiry = e.expiresAt() == -1 ? "never" : SDF.format(new Date(e.expiresAt()));
            sender.sendMessage(Lang.color(
                    "§7#" + (i + 1) + " §e[" + e.id() + "]§7 by §b" + e.issuerName()
                    + "§7 on §f" + date + "§7 (expires: §f" + expiry + "§7)\n  §fReason: §c" + e.reason()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("oreo.warnings.others")) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
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
