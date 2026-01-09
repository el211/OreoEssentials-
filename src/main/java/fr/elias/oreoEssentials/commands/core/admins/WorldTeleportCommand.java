package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WorldTeleportCommand implements TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.world")) {
            Lang.send(sender, "admin.world.no-permission",
                    "<red>You don't have permission (oreo.world).</red>");
            return true;
        }

        if (args.length < 1) {
            Lang.send(sender, "admin.world.usage",
                    "<yellow>Usage: <white>/world <n|normal|nether|end|index> [playerName] [-s]</white></yellow>");
            Lang.send(sender, "admin.world.examples",
                    "<gray>Examples: <white>/world world_nether</white>  <dark_gray>|</dark_gray>  <white>/world nether</white>  <dark_gray>|</dark_gray>  <white>/world 2</white>  <dark_gray>|</dark_gray>  <white>/world end Steve -s</white></gray>");
            return true;
        }

        boolean silent = hasFlag(args, "-s");

        Player target = null;
        if (args.length >= 2) {
            if (!(sender instanceof Player)) {
                String candidate = firstNonFlag(args, 1);
                if (candidate == null) {
                    Lang.send(sender, "admin.world.console-needs-player",
                            "<red>Console usage needs a target player: <white>/world <n> <playerName> [-s]</white></red>");
                    return true;
                }
                target = Bukkit.getPlayerExact(candidate);
                if (target == null) {
                    Lang.send(sender, "admin.world.player-not-found",
                            "<red>Player not found: <white>%player%</white></red>",
                            Map.of("player", candidate));
                    return true;
                }
            } else {
                // Player sender: optional target if an online name is provided
                String candidate = firstNonFlag(args, 1);
                if (candidate != null) {
                    target = Bukkit.getPlayerExact(candidate);
                    if (target == null) {
                        Lang.send(sender, "admin.world.player-not-found",
                                "<red>Player not found: <white>%player%</white></red>",
                                Map.of("player", candidate));
                        return true;
                    }
                }
            }
        }

        if (target == null) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                Lang.send(sender, "admin.world.console-must-specify",
                        "<red>Console must specify a target player: <white>/world <n> <playerName> [-s]</white></red>");
                return true;
            }
        }

        World dest = resolveWorld(args[0], target);
        if (dest == null) {
            Lang.send(sender, "admin.world.world-not-found",
                    "<red>World not found / unsupported: <white>%world%</white></red>",
                    Map.of("world", args[0]));
            return true;
        }

        String perWorldPerm = "cmi.command.world." + dest.getName();
        if (!sender.hasPermission(perWorldPerm)) {
            Lang.send(sender, "admin.world.no-world-permission",
                    "<red>You lack permission: <white>%permission%</white></red>",
                    Map.of("permission", perWorldPerm));
            return true;
        }

        Location loc = safeSpawn(dest);

        boolean ok = target.teleport(loc);
        if (!ok) {
            Lang.send(sender, "admin.world.teleport-failed",
                    "<red>Teleport failed.</red>");
            return true;
        }

        if (!silent) {
            if (sender != target) {
                Lang.send(target, "admin.world.target-notified",
                        "<green>You were teleported to <white>%world%</white> by <white>%staff%</white></green>",
                        Map.of("world", dest.getName(), "staff", sender.getName()));
            } else {
                Lang.send(target, "admin.world.teleported-self",
                        "<green>Teleported to <white>%world%</white></green>",
                        Map.of("world", dest.getName()));
            }
        }

        Lang.send(sender, "admin.world.teleported-other",
                "<green>Teleported <white>%player%</white> to <white>%world%</white></green>",
                Map.of("player", target.getName(), "world", dest.getName()));

        return true;
    }


    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    private static String firstNonFlag(String[] args, int startIdx) {
        for (int i = startIdx; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) continue;
            return a;
        }
        return null;
    }

    private static Location safeSpawn(World w) {
        Location spawn = w.getSpawnLocation().clone();
        try {
            int x = spawn.getBlockX();
            int z = spawn.getBlockZ();
            int y = w.getHighestBlockYAt(x, z);
            if (y > 0) spawn.setY(y + 1);
        } catch (Throwable ignored) {}
        return spawn;
    }

    private static World resolveWorld(String token, Player contextPlayer) {
        World byName = Bukkit.getWorld(token);
        if (byName != null) return byName;

        String low = token.toLowerCase(Locale.ROOT);

        if (low.matches("\\d+")) {
            int idx = Integer.parseInt(low);
            List<World> all = Bukkit.getWorlds();
            if (idx >= 1 && idx <= all.size()) return all.get(idx - 1);
        }
        World base = (contextPlayer != null
                ? contextPlayer.getWorld()
                : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0)));
        if (base == null) return null;

        String baseName = stripDimSuffix(base.getName());

        switch (low) {
            case "normal":
            case "overworld":
            case "daylight": {
                World w = Bukkit.getWorld(baseName);
                if (w != null) return w;
            }
            break;

            case "nether": {
                World w = Bukkit.getWorld(baseName + "_nether");
                if (w == null) w = firstByEnvironment(Environment.NETHER);
                if (w != null) return w;
            }
            break;

            case "end":
            case "the_end": {
                World w = Bukkit.getWorld(baseName + "_the_end");
                if (w == null) w = firstByEnvironment(Environment.THE_END);
                if (w != null) return w;
            }
            break;
        }

        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equalsIgnoreCase(token)) return w;
        }

        return null;
    }

    private static String stripDimSuffix(String name) {
        if (name.endsWith("_nether")) {
            return name.substring(0, name.length() - "_nether".length());
        }
        if (name.endsWith("_the_end")) {
            return name.substring(0, name.length() - "_the_end".length());
        }
        return name;
    }

    private static World firstByEnvironment(Environment env) {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == env) return w;
        }
        return null;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.world")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> names = Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .collect(Collectors.toList());
            names.addAll(Arrays.asList("normal", "nether", "end"));

            names.addAll(IntStream.rangeClosed(1, Math.max(3, Bukkit.getWorlds().size()))
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toList()));
            return filter(names, args[0]);
        }

        if (args.length == 2) {
            List<String> pool = new ArrayList<>();
            pool.add("-s");
            pool.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            return filter(pool, args[1]);
        }

        if (args.length == 3) {
            return filter(Collections.singletonList("-s"), args[2]);
        }

        return Collections.emptyList();
    }

    private static List<String> filter(List<String> pool, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return pool.stream()
                .distinct()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }
}