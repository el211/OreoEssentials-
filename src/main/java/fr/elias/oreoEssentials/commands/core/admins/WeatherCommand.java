package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class WeatherCommand implements TabExecutor {

    enum WType { SUN, RAIN, STORM }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.weather")) {
            Lang.send(sender, "admin.weather.no-permission",
                    "<red>You don't have permission (oreo.weather).</red>");
            return true;
        }

        WType type;
        List<String> list = new ArrayList<>(Arrays.asList(args));
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "sun":
                type = WType.SUN;
                break;
            case "rain":
                type = WType.RAIN;
                break;
            case "storm":
                type = WType.STORM;
                break;
            default:
                if (args.length == 0) {
                    help(sender, label);
                    return true;
                }
                String t = args[0].toLowerCase(Locale.ROOT);
                if (t.equals("sun")) type = WType.SUN;
                else if (t.equals("rain")) type = WType.RAIN;
                else if (t.equals("storm")) type = WType.STORM;
                else {
                    help(sender, label);
                    return true;
                }
                list.remove(0);
        }

        // Parse optional arg2 = "lock" | <seconds> | world
        boolean lock = false;
        Integer seconds = null;
        String worldArg = null;

        if (!list.isEmpty()) {
            String a = list.get(0);
            if (a.equalsIgnoreCase("lock")) {
                lock = true;
                list.remove(0);
            } else {
                Integer s = tryParseInt(a);
                if (s != null && s > 0) {
                    seconds = s;
                    list.remove(0);
                }
            }
        }
        if (!list.isEmpty()) {
            worldArg = list.get(0);
        }

        List<World> targets = resolveWorlds(sender, worldArg);
        if (targets.isEmpty()) {
            Lang.send(sender, "admin.weather.no-worlds",
                    "<red>No target worlds resolved.</red>");
            return true;
        }

        int ticks = seconds == null ? 0 : Math.max(1, seconds) * 20;
        int changed = 0;

        for (World w : targets) {
            // Apply weather state
            switch (type) {
                case SUN:
                    w.setStorm(false);
                    w.setThundering(false);
                    break;
                case RAIN:
                    w.setStorm(true);
                    w.setThundering(false);
                    break;
                case STORM:
                    w.setStorm(true);
                    w.setThundering(true);
                    break;
            }

            // Duration handling
            if (seconds != null) {
                w.setWeatherDuration(ticks);
                if (type == WType.STORM) {
                    w.setThunderDuration(ticks);
                }
            }

            // Lock handling via gamerule
            if (lock) {
                try {
                    w.setGameRuleValue("doWeatherCycle", "false");
                } catch (Throwable ignored) {}
            }

            changed++;
        }

        // Success message
        String worldLabel = targets.size() == 1
                ? targets.get(0).getName()
                : "multiple worlds (" + targets.size() + ")";

        Map<String, String> vars = new HashMap<>();
        vars.put("type", type.name().toLowerCase(Locale.ROOT));
        vars.put("world", worldLabel);
        vars.put("seconds", seconds != null ? String.valueOf(seconds) : "");
        vars.put("locked", lock ? "locked" : "");

        if (seconds != null && lock) {
            Lang.send(sender, "admin.weather.success-duration-locked",
                    "<green>Weather set to <white>%type%</white> for <white>%seconds%</white>s <dark_gray>(locked)</dark_gray> in <white>%world%</white>.</green>",
                    vars);
        } else if (seconds != null) {
            Lang.send(sender, "admin.weather.success-duration",
                    "<green>Weather set to <white>%type%</white> for <white>%seconds%</white>s in <white>%world%</white>.</green>",
                    vars);
        } else if (lock) {
            Lang.send(sender, "admin.weather.success-locked",
                    "<green>Weather set to <white>%type%</white> <dark_gray>(locked)</dark_gray> in <white>%world%</white>.</green>",
                    vars);
        } else {
            Lang.send(sender, "admin.weather.success",
                    "<green>Weather set to <white>%type%</white> in <white>%world%</white>.</green>",
                    vars);
        }

        // Tip for unlock
        if (lock && targets.size() == 1) {
            Lang.send(sender, "admin.weather.tip-unlock",
                    "<dark_gray>Tip: /weather %type% unlock %world%</dark_gray>",
                    Map.of("type", type.name().toLowerCase(Locale.ROOT), "world", targets.get(0).getName()));
        }

        return true;
    }

    private static void help(CommandSender s, String label) {
        Lang.send(s, "admin.weather.usage.header",
                "<yellow>Usage:</yellow>");
        Lang.send(s, "admin.weather.usage.format",
                "<white>/%label% <sun|rain|storm> [lock|seconds] [world|all]</white>",
                Map.of("label", label));
        Lang.send(s, "admin.weather.usage.examples",
                "<gray>Examples: <white>/sun</white> <dark_gray>|</dark_gray> <white>/rain</white> <dark_gray>|</dark_gray> <white>/storm</white> <dark_gray>|</dark_gray> <white>/sun lock</white> <dark_gray>|</dark_gray> <white>/sun 120</white> <dark_gray>|</dark_gray> <white>/sun world</white></gray>");
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<World> resolveWorlds(CommandSender sender, String arg) {
        if (arg == null || arg.isEmpty()) {
            if (sender instanceof Player) {
                return Collections.singletonList(((Player) sender).getWorld());
            }
            return Bukkit.getWorlds(); // console -> all
        }
        if (arg.equalsIgnoreCase("all")) {
            return Bukkit.getWorlds();
        }
        World w = Bukkit.getWorld(arg);
        return w == null ? Collections.emptyList() : Collections.singletonList(w);
    }

    /* ---------------- Tab Complete ---------------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.weather")) {
            return Collections.emptyList();
        }

        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("weather")) {
            if (args.length == 1) {
                return filter(Arrays.asList("sun", "rain", "storm"), args[0]);
            }
            if (args.length == 2) {
                // Could be "lock" | seconds | world
                List<String> pool = new ArrayList<>();
                pool.add("lock");
                pool.addAll(Arrays.asList("60", "120", "300", "600"));
                pool.addAll(worldsPlusAll());
                return filter(pool, args[1]);
            }
            if (args.length == 3) {
                return filter(worldsPlusAll(), args[2]);
            }
            return Collections.emptyList();
        } else {
            // /sun /rain /storm
            if (args.length == 1) {
                List<String> pool = new ArrayList<>();
                pool.add("lock");
                pool.addAll(Arrays.asList("60", "120", "300", "600"));
                pool.addAll(worldsPlusAll());
                return filter(pool, args[0]);
            }
            if (args.length == 2) {
                return filter(worldsPlusAll(), args[1]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> worldsPlusAll() {
        List<String> ws = Bukkit.getWorlds().stream()
                .map(World::getName)
                .collect(Collectors.toList());
        ws.add("all");
        return ws;
    }

    private static List<String> filter(List<String> pool, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return pool.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }
}