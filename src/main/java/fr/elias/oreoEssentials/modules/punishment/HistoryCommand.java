package fr.elias.oreoEssentials.modules.punishment;

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
 * /history <player> [page] — view a player's punishment history.
 */
public class HistoryCommand implements OreoCommand, TabCompleter {

    private static final int PAGE_SIZE = 8;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final PunishmentLogger logger;

    public HistoryCommand(PunishmentLogger logger) {
        this.logger = logger;
    }

    @Override public String       name()       { return "history"; }
    @Override public List<String> aliases()    { return List.of("punishhistory", "phist"); }
    @Override public String       permission() { return "oreo.history"; }
    @Override public String       usage()      { return "<player> [page]"; }
    @Override public boolean      playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "history.usage", "<red>Usage: /history <player> [page]</red>");
            return true;
        }

        String targetName = args[0];
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); }
            catch (NumberFormatException ignored) {}
        }

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
                Lang.send(sender, "history.not-found",
                        "<red>Player <white>%player%</white> not found.</red>",
                        Map.of("player", targetName));
                return true;
            }
            targetUuid  = op.getUniqueId();
            displayName = op.getName() != null ? op.getName() : targetName;
        }

        List<PunishmentLogger.PunishEntry> all = logger.getHistory(targetUuid);
        if (all.isEmpty()) {
            Lang.send(sender, "history.empty",
                    "<green><aqua>%player%</aqua> has no punishment history.</green>",
                    Map.of("player", displayName));
            return true;
        }

        // Newest first
        List<PunishmentLogger.PunishEntry> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> Long.compare(b.issuedAt(), a.issuedAt()));

        int total = (int) Math.ceil((double) sorted.size() / PAGE_SIZE);
        page = Math.min(page, total);
        int from = (page - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, sorted.size());

        Lang.send(sender, "history.header",
                "<gold>Punishment history for <aqua>%player%</aqua> — Page <yellow>%page%/%total%</yellow></gold>",
                Map.of("player", displayName,
                       "page",  String.valueOf(page),
                       "total", String.valueOf(total)));

        for (PunishmentLogger.PunishEntry e : sorted.subList(from, to)) {
            String date    = SDF.format(new Date(e.issuedAt()));
            String typeTag = typeColor(e.type()) + e.type().name();
            String expiry  = "";
            if (e.expiresAt() != -1) {
                expiry = " §7until §f" + SDF.format(new Date(e.expiresAt()));
            }
            sender.sendMessage(Lang.color(
                    "§7[§f" + date + "§7] " + typeTag + " §7by §b" + e.staffName()
                    + expiry + "\n  §7Reason: §f" + e.reason()));
        }

        if (page < total) {
            sender.sendMessage(Lang.color("§7Use §f/" + label + " " + displayName + " " + (page + 1) + " §7for next page."));
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
        return Collections.emptyList();
    }

    private static String typeColor(PunishmentLogger.PunishType type) {
        return switch (type) {
            case BAN, TEMPBAN  -> "§c";
            case UNBAN         -> "§a";
            case KICK          -> "§e";
            case MUTE, TEMPMUTE -> "§6";
            case UNMUTE        -> "§a";
            case WARN          -> "§e";
            case UNWARN        -> "§a";
        };
    }

    private static OfflinePlayer resolveOffline(String name) {
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }
}
