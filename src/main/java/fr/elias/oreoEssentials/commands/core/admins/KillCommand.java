package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class KillCommand implements OreoCommand {
    @Override public String name() { return "kill"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.kill"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "kill.console-usage",
                        "<red>Usage: /%label% <player></red>",
                        Map.of("label", label));
                return true;
            }

            if (!p.hasPermission("oreo.kill")) {
                Lang.send(p, "kill.self.no-permission",
                        "<red>You don't have permission to kill yourself.</red>");
                return true;
            }

            p.setHealth(0.0);
            Lang.send(p, "kill.self.slain",
                    "<gray>You have been slain.</gray>");
            return true;
        }

        if (!sender.hasPermission("oreo.kill.others")) {
            Lang.send(sender, "kill.others.no-permission",
                    "<red>You lack permission to kill others.</red>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(sender, "kill.target-not-found",
                    "<red>Player not found.</red>");
            return true;
        }

        target.setHealth(0.0);

        Lang.send(sender, "kill.others.killed",
                "<gray>Killed <yellow>%player%</yellow>.</gray>",
                Map.of("player", target.getName()));

        return true;
    }
}