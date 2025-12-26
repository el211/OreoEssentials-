// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/FeedCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class FeedCommand implements OreoCommand {
    @Override public String name() { return "feed"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.feed"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player target;

        // Determine target player
        if (args.length >= 1) {
            // Feed another player
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Lang.send(sender, "feed.not-found",
                        "<red>Player not found.</red>");
                return true;
            }
        } else {
            // Feed self
            if (!(sender instanceof Player)) {
                Lang.send(sender, "feed.console-self",
                        "<red>Console cannot feed itself. Specify a player.</red>");
                return true;
            }
            target = (Player) sender;
        }

        // Restore hunger and saturation
        target.setFoodLevel(20);
        target.setSaturation(20f);

        // Notify target
        Lang.send(target, "feed.fed",
                "<green>Fed.</green>");

        // Notify sender if feeding someone else
        if (target != sender) {
            Lang.send(sender, "feed.fed-other",
                    "<yellow>Fed <aqua>%player%</aqua>.</yellow>",
                    Map.of("player", target.getName()));
        }

        return true;
    }
}