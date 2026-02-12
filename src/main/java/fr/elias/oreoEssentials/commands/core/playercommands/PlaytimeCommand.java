package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private static String fmt(long seconds) {
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long mins = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)  sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0)  sb.append(mins).append("m ");
        sb.append(secs).append("s");
        return sb.toString().trim();
    }

    private static long vanillaPlaytimeSeconds(Player p) {
        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Math.max(0L, ticks / 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                Lang.send(sender, "playtime.console-usage",
                        "<red>Console must specify a player: /%label% <player></red>",
                        Map.of("label", label));
                return true;
            }

            long secs = vanillaPlaytimeSeconds(self);
            String time = fmt(secs);

            Lang.send(self, "playtime.self",
                    "<green>Your playtime: <yellow>%time%</yellow></green>",
                    Map.of("time", time));
            return true;
        }

        if (!sender.hasPermission("oreo.playtime.others")) {
            Lang.send(sender, "playtime.no-permission",
                    "<red>You don't have permission to view other players' playtime.</red>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(sender, "playtime.player-not-found",
                    "<red>Player not found.</red>");
            return true;
        }

        long secs = vanillaPlaytimeSeconds(target);
        String time = fmt(secs);

        Lang.send(sender, "playtime.other",
                "<aqua>%player%</aqua><gray>'s playtime:</gray> <yellow>%time%</yellow>",
                Map.of("player", target.getName(), "time", time));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("oreo.playtime.others")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}