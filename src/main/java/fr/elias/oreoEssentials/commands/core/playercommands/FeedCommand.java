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

        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Lang.send(sender, "feed.not-found",
                        "<red>Player not found.</red>");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Lang.send(sender, "feed.console-self",
                        "<red>Console cannot feed itself. Specify a player.</red>");
                return true;
            }
            target = (Player) sender;
        }

        target.setFoodLevel(20);
        target.setSaturation(20f);

        Lang.send(target, "feed.fed",
                "<green>Fed.</green>");

        if (target != sender) {
            Lang.send(sender, "feed.fed-other",
                    "<yellow>Fed <aqua>%player%</aqua>.</yellow>",
                    Map.of("player", target.getName()));
        }

        return true;
    }
}