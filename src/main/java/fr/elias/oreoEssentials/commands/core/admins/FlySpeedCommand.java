package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FlySpeedCommand implements TabExecutor {

    private static final List<String> SPEED_SUGGEST =
            IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).collect(Collectors.toList());

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Lang.send(sender, "admin.flyspeed.player-only",
                    "<red>Only players can use /flyspeed (sets your own flight speed).</red>");
            return true;
        }

        if (!sender.hasPermission("oreo.flyspeed")) {
            Lang.send(sender, "admin.flyspeed.no-permission",
                    "<red>You don't have permission (oreo.flyspeed).</red>");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            Lang.send(p, "admin.flyspeed.usage",
                    "<yellow>Usage: <white>/flyspeed <1-10|reset></white></yellow>");
            Lang.send(p, "admin.flyspeed.example",
                    "<gray>Example: <white>/flyspeed 7</white></gray>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            trySetSpeed(p, 0.1f);
            Lang.send(p, "admin.flyspeed.reset",
                    "<green>Fly speed reset to <white>0.1</white> <gray>(= level 1)</gray>.</green>");
            return true;
        }

        Integer level = parseInt(args[0]);
        if (level == null || level < 1 || level > 10) {
            Lang.send(p, "admin.flyspeed.invalid",
                    "<red>Invalid speed. Use a number <white>1-10</white> or <white>reset</white>.</red>");
            return true;
        }

        float speed = level / 10.0f;
        trySetSpeed(p, speed);

        if (p.getAllowFlight()) {
            Lang.send(p, "admin.flyspeed.set",
                    "<green>Fly speed set to <white>%speed%</white> <gray>(<white>level %level%</white>)</gray>.</green>",
                    Map.of("speed", String.valueOf(speed), "level", String.valueOf(level)));
        } else {
            Lang.send(p, "admin.flyspeed.set-not-flying",
                    "<green>Fly speed set to <white>%speed%</white> <gray>(<white>level %level%</white>)</gray>. <dark_gray>(You're not allowed to fly right now.)</dark_gray></green>",
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
            p.setFlySpeed(speed);
        } catch (IllegalArgumentException ex) {
            p.setFlySpeed(Math.max(-1f, Math.min(1f, speed)));
        }
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("oreo.flyspeed")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("reset");
            base.addAll(SPEED_SUGGEST);
            String pref = args[0].toLowerCase(Locale.ROOT);
            return base.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pref))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}