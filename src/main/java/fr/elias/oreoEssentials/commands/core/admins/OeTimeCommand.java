package fr.elias.oreoEssentials.commands.core.admins;


import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OeTimeCommand implements TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.time")) {
            sender.sendMessage("§cYou don't have permission (oreo.time).");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUsage: §f/oetime <day|night|hour(1-23)> [world|all]");
            sender.sendMessage("§7Examples: §f/oetime day   §8|  §f/oetime night all  §8|  §f/oetime 18 world_nether");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        Long ticks = null;

        if (mode.equals("day"))      ticks = 1000L;
        else if (mode.equals("night")) ticks = 13000L;
        else {
            try {
                int h = Integer.parseInt(mode);
                if (h < 1 || h > 23) throw new NumberFormatException();
                ticks = (h * 1000L) % 24000L;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid time: §f" + mode + " §7(use §fday§7, §fnight§7, or hour §f1-23§7).");
                return true;
            }
        }

        List<World> targets;
        if (args.length >= 2) {
            String w = args[1];
            if (w.equalsIgnoreCase("all")) {
                targets = Bukkit.getWorlds();
            } else {
                World world = Bukkit.getWorld(w);
                if (world == null) {
                    sender.sendMessage("§cWorld not found: §f" + w);
                    return true;
                }
                targets = Collections.singletonList(world);
            }
        } else {
            if (sender instanceof Player) {
                targets = Collections.singletonList(((Player) sender).getWorld());
            } else {
                targets = Bukkit.getWorlds();
            }
        }

        for (World world : targets) {
            world.setTime(ticks);
        }

        String worldLabel = labelForWorlds(targets);
        sender.sendMessage("§aTime set to §f" + mode + " §7(" + ticks + " ticks) §7in §f" + worldLabel + "§7.");
        return true;
    }

    private static String labelForWorlds(List<World> ws) {
        if (ws.size() == 1) return ws.get(0).getName();
        return "multiple worlds (" + ws.size() + ")";
    }

    // ---- Tab complete ----
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.time")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("day"); base.add("night");
            base.addAll(IntStream.rangeClosed(1, 23).mapToObj(String::valueOf).collect(Collectors.toList()));
            return filter(base, args[0]);
        }
        if (args.length == 2) {
            List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
            worlds.add("all");
            return filter(worlds, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> pool, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return pool.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}

