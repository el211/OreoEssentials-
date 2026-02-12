// File: src/main/java/fr/elias/oreoEssentials/kits/KitCommands.java
package fr.elias.oreoEssentials.modules.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class KitCommands implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final KitsManager manager;

    public KitCommands(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        if (plugin.getCommand("kits") != null) {
            plugin.getCommand("kits").setExecutor(this);
            plugin.getCommand("kits").setTabCompleter(this);
        }
        if (plugin.getCommand("kit") != null) {
            plugin.getCommand("kit").setExecutor(this);
            plugin.getCommand("kit").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("kits")) {
            // /kits toggle - Admin command
            if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
                if (!sender.hasPermission("oreo.kits.admin")) {
                    Lang.send(sender, "kits.no-permission",
                            "<red>You don't have permission.</red>",
                            Map.of());
                    return true;
                }

                boolean now = manager.toggleEnabled();
                if (now) {
                    Lang.send(sender, "kits.toggle-enabled",
                            "<green>Kits feature is now <white>ENABLED</white>.</green>",
                            Map.of());
                } else {
                    Lang.send(sender, "kits.toggle-disabled",
                            "<yellow>Kits feature is now <white>DISABLED</white>.</yellow>",
                            Map.of());
                }
                return true;
            }

            if (!(sender instanceof Player p)) {
                Lang.send(sender, "kits.player-only",
                        "<red>Only players can use this command.</red>",
                        Map.of());
                return true;
            }

            if (!p.hasPermission("oreo.kits.open")) {
                Lang.send(p, "kits.no-permission",
                        "<red>You don't have permission.</red>",
                        Map.of());
                return true;
            }

            if (!manager.isEnabled()) {
                Lang.send(p, "kits.disabled",
                        "<red>Kits are currently disabled.</red>",
                        Map.of());
                if (p.hasPermission("oreo.kits.admin")) {
                    Lang.send(p, "kits.disabled.hint",
                            "<gray>Use <white>%cmd%</white> to enable it.</gray>",
                            Map.of("cmd", "/kits toggle"));
                }
                return true;
            }

            // Open SmartInvs menu
            KitsMenuSI.open(plugin, manager, p);
            return true;
        }

        if (cmd.equals("kit")) {
            // /kit <name> - Claim kit directly
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "kits.player-only",
                        "<red>Only players can use this command.</red>",
                        Map.of());
                return true;
            }

            if (args.length < 1) {
                Lang.send(p, "kits.usage-kit",
                        "<yellow>Usage: /kit <n></yellow>",
                        Map.of());
                return true;
            }

            if (!manager.isEnabled()) {
                Lang.send(p, "kits.disabled",
                        "<red>Kits are currently disabled.</red>",
                        Map.of());
                if (p.hasPermission("oreo.kits.admin")) {
                    Lang.send(p, "kits.disabled.hint",
                            "<gray>Use <white>%cmd%</white> to enable it.</gray>",
                            Map.of("cmd", "/kits toggle"));
                }
                return true;
            }

            boolean handled = manager.claim(p, args[0]);
            if (!handled) {
                Lang.send(p, "kits.unknown-kit",
                        "<red>Unknown kit: <yellow>%kit_id%</yellow></red>",
                        Map.of("kit_id", args[0]));
            }
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("kits")) {
            if (args.length == 1 && sender.hasPermission("oreo.kits.admin")) {
                List<String> out = new ArrayList<>();
                if ("toggle".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("toggle");
                }
                return out;
            }
            return List.of();
        }

        if (name.equals("kit")) {
            if (args.length == 1) {
                List<String> out = new ArrayList<>();
                String start = args[0].toLowerCase(Locale.ROOT);
                for (String id : manager.getKits().keySet()) {
                    if (id.startsWith(start)) out.add(id);
                }
                return out;
            }
            return List.of();
        }

        return List.of();
    }
}