// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/KillCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
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
        // /kill -> self kill
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            // Self kill permission
            if (!p.hasPermission("oreo.kill")) {
                Lang.send(p, "kill.self.no-permission",
                        null,
                        Collections.emptyMap()
                );
                return true;
            }

            p.setHealth(0.0);
            Lang.send(p, "kill.self.slain",
                    null,
                    Collections.emptyMap()
            );
            return true;
        }

        // /kill <player> -> kill others
        if (!sender.hasPermission("oreo.kill.others")) {
            if (sender instanceof Player p) {
                Lang.send(p, "kill.others.no-permission",
                        null,
                        Collections.emptyMap()
                );
            } else {
                sender.sendMessage("You lack permission to kill others.");
            }
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            if (sender instanceof Player p) {
                Lang.send(p, "kill.target-not-found",
                        null,
                        Collections.emptyMap()
                );
            } else {
                sender.sendMessage("Player not found.");
            }
            return true;
        }

        target.setHealth(0.0);

        if (sender instanceof Player p) {
            Lang.send(p, "kill.others.killed",
                    null,
                    Map.of("player", target.getName())
            );
        } else {
            sender.sendMessage("Killed " + target.getName() + ".");
        }
        return true;
    }
}
