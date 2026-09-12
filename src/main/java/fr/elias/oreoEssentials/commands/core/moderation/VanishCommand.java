package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.VanishService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class VanishCommand implements OreoCommand {
    private final VanishService service;

    public VanishCommand(VanishService service) {
        this.service = service;
    }

    @Override public String name() { return "vanish"; }
    @Override public List<String> aliases() { return List.of("v"); }
    @Override public String permission() { return "oreo.vanish"; }
    @Override public String usage() { return "[on|off|toggle|list] [player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("on", "off", "toggle", "list", "clearall").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {

        // /vanish clearall  — wipe all persisted vanish state (fixes mass-contamination)
        if (args.length >= 1 && args[0].equalsIgnoreCase("clearall")) {
            if (!sender.hasPermission("oreo.vanish.admin")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            service.clearAllPersisted();
            sender.sendMessage("§a[Vanish] All persisted vanish states cleared. Every player is now visible.");
            return true;
        }

        // /vanish list  — show who is currently in the vanished set
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("oreo.vanish.admin")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            Set<UUID> snap = service.getVanishedSnapshot();
            if (snap.isEmpty()) {
                sender.sendMessage("§a[Vanish] No players are currently vanished.");
                return true;
            }
            sender.sendMessage("§6[Vanish] Currently vanished (" + snap.size() + "):");
            for (UUID id : snap) {
                Player online = Bukkit.getPlayer(id);
                String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(id).getName();
                String status = online != null ? "§aonline" : "§7offline";
                sender.sendMessage("  §f" + (name != null ? name : id.toString()) + " §8(" + id + "§8) [" + status + "§8]");
            }
            return true;
        }

        // /vanish [on|off|toggle] <player>  — target another player (admin only)
        if (args.length >= 2 || (args.length >= 1
                && !args[0].equalsIgnoreCase("on")
                && !args[0].equalsIgnoreCase("off")
                && !args[0].equalsIgnoreCase("toggle")
                && !(sender instanceof Player))) {

            // Determine sub and target
            String sub = "toggle";
            String targetName = null;
            if (args.length == 1) {
                targetName = args[0]; // /vanish <player>
            } else {
                // args[0] = sub, args[1] = player  OR  args[0] = player (no sub)
                if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("toggle")) {
                    sub = args[0].toLowerCase();
                    targetName = args[1];
                } else {
                    targetName = args[0];
                    if (args.length >= 2) sub = args[1].toLowerCase();
                }
            }

            if (!sender.hasPermission("oreo.vanish.admin")) {
                sender.sendMessage("§cYou don't have permission to vanish other players.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage("§cPlayer §f" + targetName + " §cis not online.");
                return true;
            }

            boolean nowVanished;
            switch (sub) {
                case "on"  -> { service.setVanished(target, true);  nowVanished = true; }
                case "off" -> { service.setVanished(target, false); nowVanished = false; }
                default    -> nowVanished = service.toggle(target);
            }

            sender.sendMessage("§6[Vanish] §f" + target.getName() + " is now " + (nowVanished ? "§cvanished" : "§avisible") + "§f.");
            if (nowVanished) {
                Lang.send(target, "moderation.vanish.enabled", "<green>You are now vanished.</green>");
            } else {
                Lang.send(target, "moderation.vanish.disabled", "<yellow>You are now visible.</yellow>");
            }
            return true;
        }

        // Self — must be a player
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cConsole must specify a player: /vanish <player>");
            return true;
        }

        boolean nowVanished;
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            nowVanished = service.toggle(p);
        } else if (args[0].equalsIgnoreCase("on")) {
            service.setVanished(p, true);
            nowVanished = true;
        } else if (args[0].equalsIgnoreCase("off")) {
            service.setVanished(p, false);
            nowVanished = false;
        } else {
            return false;
        }

        if (nowVanished) {
            Lang.send(p, "moderation.vanish.enabled",
                    "<green>You are now vanished.</green>");
        } else {
            Lang.send(p, "moderation.vanish.disabled",
                    "<yellow>You are now visible.</yellow>");
        }

        return true;
    }
}
