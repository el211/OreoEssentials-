package fr.elias.oreoEssentials.modules.portals;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class PortalsCommand implements CommandExecutor, TabCompleter {

    private final PortalsManager manager;

    public PortalsCommand(PortalsManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1" -> {
                if (!(sender instanceof Player p)) {
                    Lang.send(sender, "portals.player-only",
                            "<red>Players only.</red>");
                    return true;
                }
                if (!sender.hasPermission("oreo.portals.create")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                manager.setPos1(p, p.getLocation());
                Lang.send(p, "portals.pos1-set",
                        "<green>Portal pos1 set at <aqua>%location%</aqua></green>",
                        Map.of("location", locStr(p.getLocation())));
                return true;
            }
            case "pos2" -> {
                if (!(sender instanceof Player p)) {
                    Lang.send(sender, "portals.player-only",
                            "<red>Players only.</red>");
                    return true;
                }
                if (!sender.hasPermission("oreo.portals.create")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                manager.setPos2(p, p.getLocation());
                Lang.send(p, "portals.pos2-set",
                        "<green>Portal pos2 set at <aqua>%location%</aqua></green>",
                        Map.of("location", locStr(p.getLocation())));
                return true;
            }
            case "create" -> {
                if (!(sender instanceof Player p)) {
                    Lang.send(sender, "portals.player-only",
                            "<red>Players only.</red>");
                    return true;
                }
                if (!sender.hasPermission("oreo.portals.create")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                if (a.length < 6) {
                    Lang.send(sender, "portals.create.usage",
                            "<yellow>Usage: /%label% create <name> <world> <x> <y> <z> [keepYaw] [permission]</yellow>",
                            Map.of("label", label));
                    return true;
                }

                String name = a[1];
                World w = Bukkit.getWorld(a[2]);
                if (w == null) {
                    Lang.send(sender, "portals.create.world-not-found",
                            "<red>World not found: <yellow>%world%</yellow></red>",
                            Map.of("world", a[2]));
                    return true;
                }

                double x, y, z;
                try {
                    x = Double.parseDouble(a[3]);
                    y = Double.parseDouble(a[4]);
                    z = Double.parseDouble(a[5]);
                } catch (NumberFormatException ex) {
                    Lang.send(sender, "portals.create.invalid-coords",
                            "<red>Invalid coordinates.</red>");
                    return true;
                }

                boolean keepYaw = a.length >= 7 && (a[6].equalsIgnoreCase("true") || a[6].equalsIgnoreCase("yes"));
                String permission = a.length >= 8 ? a[7] : null;

                Location dest = new Location(w, x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch());

                String error = manager.create(name, p, dest, keepYaw, permission);
                if (error != null) {
                    Lang.send(sender, "portals.create.failed",
                            "<red>Failed: <yellow>%error%</yellow></red>",
                            Map.of("error", error));
                } else {
                    Lang.send(sender, "portals.create.success",
                            "<green>Created portal <aqua>%name%</aqua> → <white>%location%</white></green>",
                            Map.of("name", name, "location", locStr(dest)));
                }
                return true;
            }
            case "remove", "delete" -> {
                if (!sender.hasPermission("oreo.portals.remove")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                if (a.length < 2) {
                    Lang.send(sender, "portals.remove.usage",
                            "<yellow>Usage: /%label% remove <name></yellow>",
                            Map.of("label", label));
                    return true;
                }
                boolean ok = manager.remove(a[1]);
                if (ok) {
                    Lang.send(sender, "portals.remove.success",
                            "<green>Portal <aqua>%name%</aqua> removed.</green>",
                            Map.of("name", a[1]));
                } else {
                    Lang.send(sender, "portals.remove.not-found",
                            "<red>Portal not found: <yellow>%name%</yellow></red>",
                            Map.of("name", a[1]));
                }
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("oreo.portals.list")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                var names = manager.listNames();
                if (names.isEmpty()) {
                    Lang.send(sender, "portals.list.empty",
                            "<gray>No portals configured.</gray>");
                } else {
                    Lang.send(sender, "portals.list.header",
                            "<gold>Portals (%count%):</gold>",
                            Map.of("count", String.valueOf(names.size())));
                    for (String n : names) {
                        var portal = manager.get(n);
                        if (portal != null) {
                            Lang.send(sender, "portals.list.entry",
                                    " <dark_gray>-</dark_gray> <aqua>%name%</aqua> <gray>→ <white>%dest%</white></gray>",
                                    Map.of("name", n, "dest", shortLocStr(portal.destination)));
                        }
                    }
                }
                return true;
            }
            case "info" -> {
                if (!sender.hasPermission("oreo.portals.info")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                if (a.length < 2) {
                    Lang.send(sender, "portals.info.usage",
                            "<yellow>Usage: /%label% info <name></yellow>",
                            Map.of("label", label));
                    return true;
                }
                var portal = manager.get(a[1]);
                if (portal == null) {
                    Lang.send(sender, "portals.info.not-found",
                            "<red>Portal not found: <yellow>%name%</yellow></red>",
                            Map.of("name", a[1]));
                    return true;
                }

                Lang.send(sender, "portals.info.header",
                        "<gold>Portal Info: <aqua>%name%</aqua></gold>",
                        Map.of("name", portal.name));
                Lang.send(sender, "portals.info.world",
                        " <gray>World:</gray> <white>%world%</white>",
                        Map.of("world", portal.world.getName()));
                Lang.send(sender, "portals.info.destination",
                        " <gray>Destination:</gray> <white>%dest%</white>",
                        Map.of("dest", locStr(portal.destination)));
                Lang.send(sender, "portals.info.keep-yaw",
                        " <gray>Keep Yaw/Pitch:</gray> <white>%keep%</white>",
                        Map.of("keep", portal.keepYawPitch ? "Yes" : "No"));
                if (portal.permission != null && !portal.permission.isEmpty()) {
                    Lang.send(sender, "portals.info.permission",
                            " <gray>Permission:</gray> <white>%perm%</white>",
                            Map.of("perm", portal.permission));
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("oreo.portals.reload")) {
                    Lang.send(sender, "portals.no-permission",
                            "<red>You don't have permission.</red>");
                    return true;
                }
                manager.loadAll();
                Lang.send(sender, "portals.reload.success",
                        "<green>Portals reloaded. <white>%count%</white> portal(s) loaded.</green>",
                        Map.of("count", String.valueOf(manager.listNames().size())));
                return true;
            }
            case "help", "?" -> {
                sendHelp(sender, label);
                return true;
            }
            default -> {
                Lang.send(sender, "portals.unknown-subcommand",
                        "<red>Unknown subcommand. Use <yellow>/%label% help</yellow></red>",
                        Map.of("label", label));
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        Lang.send(sender, "portals.help.header",
                "<gold><bold>Portals Help</bold></gold>");
        Lang.send(sender, "portals.help.pos1",
                " <yellow>/%label% pos1</yellow> <gray>- Set portal corner 1</gray>",
                Map.of("label", label));
        Lang.send(sender, "portals.help.pos2",
                " <yellow>/%label% pos2</yellow> <gray>- Set portal corner 2</gray>",
                Map.of("label", label));
        Lang.send(sender, "portals.help.create",
                " <yellow>/%label% create <name> <world> <x> <y> <z> [keepYaw] [perm]</yellow>",
                Map.of("label", label));
        Lang.send(sender, "portals.help.remove",
                " <yellow>/%label% remove <name></yellow> <gray>- Delete a portal</gray>",
                Map.of("label", label));
        Lang.send(sender, "portals.help.list",
                " <yellow>/%label% list</yellow> <gray>- List all portals</gray>",
                Map.of("label", label));
        Lang.send(sender, "portals.help.info",
                " <yellow>/%label% info <name></yellow> <gray>- View portal details</gray>",
                Map.of("label", label));
        Lang.send(sender, "portals.help.reload",
                " <yellow>/%label% reload</yellow> <gray>- Reload portals.yml</gray>",
                Map.of("label", label));
    }

    private String locStr(Location l) {
        return l.getWorld().getName() + " (" +
                String.format("%.1f", l.getX()) + ", " +
                String.format("%.1f", l.getY()) + ", " +
                String.format("%.1f", l.getZ()) + ")";
    }

    private String shortLocStr(Location l) {
        return l.getWorld().getName() + " (" +
                String.format("%.0f", l.getX()) + ", " +
                String.format("%.0f", l.getY()) + ", " +
                String.format("%.0f", l.getZ()) + ")";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("oreo.portals.create")) {
                subs.add("pos1");
                subs.add("pos2");
                subs.add("create");
            }
            if (sender.hasPermission("oreo.portals.remove")) subs.add("remove");
            if (sender.hasPermission("oreo.portals.list")) subs.add("list");
            if (sender.hasPermission("oreo.portals.info")) subs.add("info");
            if (sender.hasPermission("oreo.portals.reload")) subs.add("reload");
            subs.add("help");

            String prefix = a[0].toLowerCase(Locale.ROOT);
            return subs.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if ((sub.equals("remove") || sub.equals("delete") || sub.equals("info")) && sender.hasPermission("oreo.portals." + sub)) {
                return manager.listNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        if (a.length == 3 && a[0].equalsIgnoreCase("create") && sender.hasPermission("oreo.portals.create")) {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(a[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (a.length == 7 && a[0].equalsIgnoreCase("create") && sender.hasPermission("oreo.portals.create")) {
            return List.of("true", "false", "yes", "no");
        }

        return List.of();
    }
}