package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class FlyCommand implements OreoCommand {
    @Override public String name() { return "fly"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.fly"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player target;

        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Lang.send(sender, "admin.fly.not-found",
                        "<red>Player not found: <yellow>%target%</yellow></red>",
                        Map.of("target", args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "admin.fly.console-usage",
                        "<red>Usage: /%label% <player></red>",
                        Map.of("label", label));
                return true;
            }
            target = p;
        }

        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);

        // Message to target
        if (enable) {
            Lang.send(target, "admin.fly.enabled",
                    "<green>Flight <aqua>enabled</aqua>.</green>");
        } else {
            Lang.send(target, "admin.fly.disabled",
                    "<red>Flight <aqua>disabled</aqua>.</red>");
        }

        // Message to executor if different
        if (target != sender) {
            Lang.send(sender, "admin.fly.toggled-other",
                    "<green>Flight <aqua>%state%</aqua> for <aqua>%target%</aqua>.</green>",
                    Map.of("target", target.getName(), "state", enable ? "enabled" : "disabled"));
        }

        return true;
    }
}