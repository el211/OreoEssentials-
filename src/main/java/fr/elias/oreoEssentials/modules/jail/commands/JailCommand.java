package fr.elias.oreoEssentials.modules.jail.commands;

import fr.elias.oreoEssentials.modules.jail.JailService;
import fr.elias.oreoEssentials.util.TimeText;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class JailCommand implements CommandExecutor {
    private final JailService service;

    public JailCommand(JailService service) { this.service = service; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /jail <player> (time) (jailName) (cellId) (-s) (r:reason...)");
            sender.sendMessage("§e      /jail release <player>   §7to free a player");
            return true;
        }

        if (args[0].equalsIgnoreCase("release")) {
            if (args.length < 2) { sender.sendMessage("§cUsage: /jail release <player>"); return true; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || op.getUniqueId() == null) { sender.sendMessage("§cUnknown player."); return true; }
            if (service.release(op.getUniqueId())) sender.sendMessage("§aReleased.");
            else sender.sendMessage("§cPlayer is not jailed.");
            return true;
        }

        // jail <player> (time) (jailName) (cellId) (-s) (r:reason...)
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
        if (op == null || op.getUniqueId() == null) { sender.sendMessage("§cUnknown player."); return true; }

        long timeMs = 0;
        String jailName = null, cellId = null, reason = "";
        boolean silent = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("-s")) { silent = true; continue; }
            if (a.startsWith("r:")) { reason = a.substring(2); continue; }
            if (timeMs == 0) { timeMs = TimeText.parseToMillis(a); if (timeMs != 0 || a.equalsIgnoreCase("perm")) continue; }
            if (jailName == null) { jailName = a; continue; }
            if (cellId == null) { cellId = a; continue; }
            // anything else gets appended to reason
            reason = (reason.isEmpty() ? a : (reason + " " + a));
        }

        if (jailName == null) { sender.sendMessage("§cMissing jail name."); return true; }

        boolean ok = service.jail(op.getUniqueId(), jailName, cellId, timeMs, reason, sender.getName());
        if (!ok) { sender.sendMessage("§cFailed: invalid jail/cell, or missing cell spawn."); return true; }

        if (!silent) {
            Bukkit.broadcast("§c" + op.getName() + " was jailed" +
                    (timeMs > 0 ? " for §f" + TimeText.format(timeMs) : " permanently") +
                    (reason.isEmpty() ? "" : " §7(Reason: §f" + reason + "§7)"), "oreo.jail.announce");
        } else {
            sender.sendMessage("§aJailed silently.");
        }
        return true;
    }
}
