package fr.elias.oreoEssentials.modules.nick;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class RealNameCommand implements OreoCommand {
    @Override public String name() { return "realname"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.realname"; }
    @Override public String usage() { return "<nickname>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "realname.usage",
                    "<red>Usage: /realname <nickname></red>");
            return true;
        }

        String nick = ChatColor.stripColor(args[0]);

        for (Player online : Bukkit.getOnlinePlayers()) {
            String disp = ChatColor.stripColor(online.getDisplayName());
            if (disp != null && disp.equalsIgnoreCase(nick)) {
                Lang.send(sender, "realname.found",
                        "<gold>%nickname%</gold> <gray>is</gray> <aqua>%realname%</aqua>",
                        Map.of("nickname", nick, "realname", online.getName()));
                return true;
            }
        }

        OfflinePlayer op = Bukkit.getOfflinePlayer(nick);
        if (op != null && op.getName() != null) {
            Lang.send(sender, "realname.found",
                    "<gold>%nickname%</gold> <gray>is</gray> <aqua>%realname%</aqua>",
                    Map.of("nickname", nick, "realname", op.getName()));
            return true;
        }

        Lang.send(sender, "realname.not-found",
                "<red>No player found with that nickname.</red>");
        return true;
    }
}