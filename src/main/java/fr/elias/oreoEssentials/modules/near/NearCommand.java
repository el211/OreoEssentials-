package fr.elias.oreoEssentials.modules.near;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NearCommand implements OreoCommand {
    @Override public String name() { return "near"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.near"; }
    @Override public String usage() { return "[radius]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        int radius = 100;
        if (args.length >= 1) {
            try {
                radius = Math.max(1, Math.min(1000, Integer.parseInt(args[0])));
            } catch (NumberFormatException e) {
                Lang.send(p, "near.radius-not-number",
                        "<red>Radius must be a number. You entered: <yellow>%input%</yellow></red>",
                        Map.of("input", args[0]));
                return true;
            }
        }

        Location me = p.getLocation();
        int finalRadius = radius;

        var list = Bukkit.getOnlinePlayers().stream()
                .filter(other -> other != p && other.getWorld().equals(p.getWorld()))
                .map(other -> new Entry(other.getName(), other.getLocation().distance(me)))
                .filter(e -> e.dist <= finalRadius)
                .sorted(Comparator.comparingDouble(e -> e.dist))
                .collect(Collectors.toList());

        if (list.isEmpty()) {
            Lang.send(p, "near.none",
                    "<yellow>No players within <aqua>%radius%</aqua> blocks.</yellow>",
                    Map.of("radius", String.valueOf(radius)));
            return true;
        }

        String formattedList = list.stream()
                .map(e -> Lang.msgWithDefault("near.entry.format",
                        "<aqua>%name%</aqua> <gray>(<yellow>%distance%</yellow>m)</gray>",
                        Map.of("name", e.name, "distance", String.format("%.1f", e.dist)),
                        p))
                .collect(Collectors.joining(
                        Lang.msgWithDefault("near.entry.separator", "<gray>, </gray>", p)));

        Lang.send(p, "near.list",
                "<gray>Players within <aqua>%radius%</aqua> blocks:</gray> %list%",
                Map.of("radius", String.valueOf(radius), "list", formattedList));

        return true;
    }

    private static final class Entry {
        final String name;
        final double dist;

        Entry(String n, double d) {
            name = n;
            dist = d;
        }
    }
}