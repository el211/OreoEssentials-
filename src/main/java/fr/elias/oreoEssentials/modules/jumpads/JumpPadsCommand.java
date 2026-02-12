// File: src/main/java/fr/elias/oreoEssentials/jumpads/JumpPadsCommand.java
package fr.elias.oreoEssentials.modules.jumpads;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


public class JumpPadsCommand implements CommandExecutor, TabCompleter {
    private final JumpPadsManager mgr;

    public JumpPadsCommand(JumpPadsManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        // Basic permission gate
        if (!sender.hasPermission("oreo.jumpad")) {
            Lang.send(sender, "jumpads.no-permission",
                    "<red>You don't have permission.</red>",
                    Map.of());
            return true;
        }

        if (a.length == 0) {
            // Show help
            String names = String.join(", ", mgr.listNames());
            Lang.send(sender, "jumpads.list-inline",
                    "<gold>JumpPads:</gold> <gray>%names%</gray>",
                    Map.of("names", names.isEmpty() ? "(none)" : names));

            Lang.send(sender, "jumpads.help.header",
                    "<yellow>Usage:</yellow>",
                    Map.of());
            Lang.send(sender, "jumpads.help.create",
                    "<yellow>  /%label% create <name> [power] [upward] [useLookDir]</yellow>  <gray>(block under you)</gray>",
                    Map.of("label", label));
            Lang.send(sender, "jumpads.help.remove",
                    "<yellow>  /%label% remove <name></yellow>",
                    Map.of("label", label));
            Lang.send(sender, "jumpads.help.list",
                    "<yellow>  /%label% list</yellow>",
                    Map.of("label", label));
            Lang.send(sender, "jumpads.help.info",
                    "<yellow>  /%label% info <name></yellow>",
                    Map.of("label", label));
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player p)) {
                    Lang.send(sender, "jumpads.player-only",
                            "<red>Players only.</red>",
                            Map.of());
                    return true;
                }
                if (a.length < 2) {
                    Lang.send(sender, "jumpads.usage-create",
                            "<red>Usage: /%label% create <name> [power] [upward] [useLookDir]</red>",
                            Map.of("label", label));
                    Lang.send(sender, "jumpads.defaults",
                            "<gray>Defaults → power=%power%, upward=%upward%, useLookDir=%look%</gray>",
                            Map.of(
                                    "power", String.valueOf(mgr.defaultPower),
                                    "upward", String.valueOf(mgr.defaultUpward),
                                    "look", String.valueOf(mgr.defaultUseLookDir)
                            ));
                    return true;
                }
                String name = a[1];

                // Use manager defaults (config-backed) when args omitted
                double power  = (a.length >= 3) ? parseDouble(a[2], mgr.defaultPower) : mgr.defaultPower;
                double upward = (a.length >= 4) ? parseDouble(a[3], mgr.defaultUpward) : mgr.defaultUpward;
                boolean look  = (a.length >= 5) ? parseBool(a[4], mgr.defaultUseLookDir) : mgr.defaultUseLookDir;

                Location under = p.getLocation().clone().subtract(0, 1, 0);
                boolean ok = mgr.create(name, under, power, upward, look);
                if (ok) {
                    Lang.send(sender, "jumpads.created",
                            "<green>JumpPad '<aqua>%name%</aqua>' created at <aqua>%x%,%y%,%z%</aqua> (power=%power%, upward=%upward%, look=%look%)</green>",
                            Map.of(
                                    "name", name,
                                    "x", String.valueOf(under.getBlockX()),
                                    "y", String.valueOf(under.getBlockY()),
                                    "z", String.valueOf(under.getBlockZ()),
                                    "power", String.valueOf(power),
                                    "upward", String.valueOf(upward),
                                    "look", String.valueOf(look)
                            ));
                } else {
                    Lang.send(sender, "jumpads.create-failed",
                            "<red>Failed to create (invalid location or name).</red>",
                            Map.of());
                }
                return true;
            }

            case "remove" -> {
                if (a.length < 2) {
                    Lang.send(sender, "jumpads.usage-remove",
                            "<red>Usage: /%label% remove <name></red>",
                            Map.of("label", label));
                    return true;
                }
                boolean ok = mgr.remove(a[1]);
                if (ok) {
                    Lang.send(sender, "jumpads.removed",
                            "<green>JumpPad '<aqua>%name%</aqua>' removed.</green>",
                            Map.of("name", a[1]));
                } else {
                    Lang.send(sender, "jumpads.not-found",
                            "<red>JumpPad '<yellow>%name%</yellow>' not found.</red>",
                            Map.of("name", a[1]));
                }
                return true;
            }

            case "list" -> {
                String names = String.join(", ", mgr.listNames());
                Lang.send(sender, "jumpads.list",
                        "<gold>JumpPads (%count%):</gold> <gray>%names%</gray>",
                        Map.of(
                                "count", String.valueOf(mgr.listNames().size()),
                                "names", names.isEmpty() ? "(none)" : names
                        ));
                return true;
            }

            case "info" -> {
                if (a.length < 2) {
                    Lang.send(sender, "jumpads.usage-info",
                            "<red>Usage: /%label% info <name></red>",
                            Map.of("label", label));
                    return true;
                }
                JumpPadsManager.JumpPad jp = mgr.getByName(a[1]);
                if (jp == null) {
                    Lang.send(sender, "jumpads.not-found",
                            "<red>JumpPad '<yellow>%name%</yellow>' not found.</red>",
                            Map.of("name", a[1]));
                    return true;
                }

                Lang.send(sender, "jumpads.info.name",
                        "<aqua>Name:</aqua> <white>%name%</white>",
                        Map.of("name", jp.name));
                Lang.send(sender, "jumpads.info.location",
                        "<aqua>World:</aqua> <white>%world%</white>  <aqua>XYZ:</aqua> <white>%x% %y% %z%</white>",
                        Map.of(
                                "world", jp.world.getName(),
                                "x", String.valueOf(jp.x),
                                "y", String.valueOf(jp.y),
                                "z", String.valueOf(jp.z)
                        ));
                Lang.send(sender, "jumpads.info.settings",
                        "<aqua>Power:</aqua> <white>%power%</white>  <aqua>Upward:</aqua> <white>%upward%</white>  <aqua>UseLookDir:</aqua> <white>%look%</white>",
                        Map.of(
                                "power", String.valueOf(jp.power),
                                "upward", String.valueOf(jp.upward),
                                "look", String.valueOf(jp.useLookDir)
                        ));
                return true;
            }

            default -> {
                Lang.send(sender, "jumpads.unknown-subcommand",
                        "<red>Unknown subcommand. Try /%label%</red>",
                        Map.of("label", label));
                return true;
            }
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("y");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("oreo.jumpad")) return java.util.Collections.emptyList();

        if (a.length == 1) return java.util.List.of("create", "remove", "list", "info");

        if (a.length == 2) {
            if (a[0].equalsIgnoreCase("remove") || a[0].equalsIgnoreCase("info")) {
                String prefix = a[1].toLowerCase(Locale.ROOT);
                return mgr.listNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        // Suggest booleans for useLookDir
        if (a.length == 5 && a[0].equalsIgnoreCase("create")) {
            return new ArrayList<>(java.util.List.of("true", "false"));
        }

        return java.util.Collections.emptyList();
    }
}