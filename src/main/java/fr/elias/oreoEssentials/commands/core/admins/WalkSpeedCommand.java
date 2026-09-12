package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WalkSpeedCommand implements TabExecutor {

    private static final List<String> SPEED_SUGGEST =
            IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).collect(Collectors.toList());

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Lang.send(sender, "admin.walkspeed.player-only",
                    "<red>Only players can use /walkspeed.</red>");
            return true;
        }

        boolean canSelf   = sender.hasPermission("oreo.walkspeed");
        boolean canOthers = sender.hasPermission("oreo.walkspeed.others");

        if (!canSelf && !canOthers) {
            Lang.send(sender, "admin.walkspeed.no-permission",
                    "<red>You don't have permission (oreo.walkspeed).</red>");
            return true;
        }

        // /walkspeed <player> <speed|reset>
        if (args.length >= 2) {
            if (!canOthers) {
                Lang.send(sender, "admin.walkspeed.no-permission-others",
                        "<red>You don't have permission to change other players' walk speed.</red>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Lang.send(sender, "admin.walkspeed.player-not-found",
                        "<red>Player <white>%player%</white> not found or offline.</red>",
                        Map.of("player", args[0]));
                return true;
            }
            return applySpeed(sender, target, args[1], true);
        }

        // /walkspeed (no args) — show usage
        if (args.length == 0) {
            if (canOthers) {
                Lang.send(p, "admin.walkspeed.usage",
                        "<yellow>Usage: <white>/walkspeed <1-10|reset> [player]</white></yellow>");
            } else {
                Lang.send(p, "admin.walkspeed.usage",
                        "<yellow>Usage: <white>/walkspeed <1-10|reset></white></yellow>");
            }
            return true;
        }

        // /walkspeed <speed|reset>
        if (!canSelf) {
            Lang.send(sender, "admin.walkspeed.no-permission",
                    "<red>You don't have permission (oreo.walkspeed).</red>");
            return true;
        }
        return applySpeed(sender, p, args[0], false);
    }

    private boolean applySpeed(CommandSender sender, Player target, String speedArg, boolean isOther) {
        if (speedArg.equalsIgnoreCase("reset")) {
            trySetSpeed(target, 0.2f);
            if (isOther) {
                Lang.send(sender, "admin.walkspeed.reset-other",
                        "<green>Reset <white>%player%</white>'s walk speed to default.</green>",
                        Map.of("player", target.getName()));
                Lang.send(target, "admin.walkspeed.reset-by-admin",
                        "<yellow>Your walk speed was reset to default by an admin.</yellow>");
            } else {
                Lang.send(sender, "admin.walkspeed.reset",
                        "<green>Walk speed reset to <white>0.2</white> <gray>(= default)</gray>.</green>");
            }
            return true;
        }

        Integer level = parseInt(speedArg);
        if (level == null || level < 1 || level > 10) {
            Lang.send(sender, "admin.walkspeed.invalid",
                    "<red>Invalid speed. Use a number <white>1-10</white> or <white>reset</white>.</red>");
            return true;
        }

        float speed = level / 10.0f;
        trySetSpeed(target, speed);

        if (isOther) {
            Lang.send(sender, "admin.walkspeed.set-other",
                    "<green>Set <white>%player%</white>'s walk speed to <white>%speed%</white> <gray>(level %level%)</gray>.</green>",
                    Map.of("player", target.getName(), "speed", String.valueOf(speed), "level", String.valueOf(level)));
            Lang.send(target, "admin.walkspeed.set-by-admin",
                    "<yellow>Your walk speed was set to <white>%speed%</white> <gray>(level %level%)</gray> by an admin.</yellow>",
                    Map.of("speed", String.valueOf(speed), "level", String.valueOf(level)));
        } else {
            Lang.send(sender, "admin.walkspeed.set",
                    "<green>Walk speed set to <white>%speed%</white> <gray>(<white>level %level%</white>)</gray>.</green>",
                    Map.of("speed", String.valueOf(speed), "level", String.valueOf(level)));
        }

        return true;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void trySetSpeed(Player p, float speed) {
        try {
            p.setWalkSpeed(speed);
        } catch (IllegalArgumentException ex) {
            p.setWalkSpeed(Math.max(-1f, Math.min(1f, speed)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        boolean canSelf   = sender.hasPermission("oreo.walkspeed");
        boolean canOthers = sender.hasPermission("oreo.walkspeed.others");

        if (!canSelf && !canOthers) return Collections.emptyList();

        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();

            // Player names first (if canOthers)
            if (canOthers) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(pref)) {
                        suggestions.add(p.getName());
                    }
                }
            }

            // Speed values
            if (canSelf) {
                List<String> speeds = new ArrayList<>();
                speeds.add("reset");
                speeds.addAll(SPEED_SUGGEST);
                speeds.stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pref))
                        .forEach(suggestions::add);
            }

            return suggestions.stream().distinct().collect(Collectors.toList());
        }

        // args.length == 2: arg[0] is a player name, suggest speed values
        if (args.length == 2 && canOthers) {
            String pref = args[1].toLowerCase(Locale.ROOT);
            List<String> base = new ArrayList<>();
            base.add("reset");
            base.addAll(SPEED_SUGGEST);
            return base.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pref))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
