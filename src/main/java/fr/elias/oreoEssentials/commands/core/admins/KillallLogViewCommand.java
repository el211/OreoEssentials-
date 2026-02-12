package fr.elias.oreoEssentials.commands.core.admins;


import fr.elias.oreoEssentials.util.KillallLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class KillallLogViewCommand implements CommandExecutor {

    private final KillallLogger logger;

    public KillallLogViewCommand(KillallLogger logger) {
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.killall.view")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        int limit = 10;
        if (args.length >= 1) {
            try { limit = Math.max(1, Math.min(100, Integer.parseInt(args[0]))); } catch (Exception ignored) {}
        }
        var lines = logger.tail(limit);
        if (lines.isEmpty()) {
            sender.sendMessage("§7No records yet.");
            return true;
        }
        sender.sendMessage("§8—— §7Last §e" + limit + " §7killall records §8——");
        for (String l : lines) sender.sendMessage("§7" + l);
        return true;
    }
}
