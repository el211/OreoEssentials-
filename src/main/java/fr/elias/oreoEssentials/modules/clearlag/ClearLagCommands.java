package fr.elias.oreoEssentials.modules.clearlag;

import org.bukkit.command.*;

public class ClearLagCommands implements CommandExecutor, TabCompleter {

    private final ClearLagManager mgr;

    public ClearLagCommands(ClearLagManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("""
                §6/olagg help §7- show help
                §6/olagg reload §7- reload clearlag.yml
                §6/olagg clear §7- manual cleanup
                §6/olagg killmobs §7- remove mobs (respects kill-mobs filter)
                """);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("oreo.lag.reload")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                mgr.reloadAndAck(sender);
            }
            case "clear" -> {
                if (!sender.hasPermission("oreo.lag.clear")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                mgr.commandClear(sender);
            }
            case "killmobs" -> {
                if (!sender.hasPermission("oreo.lag.killmobs")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                mgr.commandKillMobs(sender);
            }
            default -> sender.sendMessage("§cUnknown subcommand. Try /olagg help");
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return java.util.List.of("help", "reload", "clear", "killmobs");
        return java.util.Collections.emptyList();
    }
}