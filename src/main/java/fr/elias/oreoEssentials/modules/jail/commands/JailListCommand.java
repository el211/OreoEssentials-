package fr.elias.oreoEssentials.modules.jail.commands;

import fr.elias.oreoEssentials.modules.jail.JailModels;
import fr.elias.oreoEssentials.modules.jail.JailService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;

import java.util.Map;

public final class JailListCommand implements CommandExecutor {
    private final JailService service;

    public JailListCommand(JailService service) { this.service = service; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            sender.sendMessage("§eJails:");
            for (Map.Entry<String, JailModels.Jail> e : service.allJails().entrySet()) {
                sender.sendMessage("§7- §f" + e.getKey() + " §8(world=" + e.getValue().world + ", cells=" + e.getValue().cells.size() + ")");
            }
            return true;
        }
        String jail = a[0].toLowerCase();
        JailModels.Jail j = service.allJails().get(jail);
        if (j == null) { sender.sendMessage("§cUnknown jail."); return true; }

        if (a.length == 1) {
            sender.sendMessage("§eCells in §f" + jail + "§e:");
            for (String id : j.cells.keySet())
                sender.sendMessage("§7- §f" + id);
            return true;
        }

        String cell = a[1];
        sender.sendMessage("§eInmates in §f" + jail + " §e(cell §f" + cell + "§e):");
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    var snt = service.sentence(p.getUniqueId());
                    return snt != null && snt.jailName.equalsIgnoreCase(jail)
                            && (cell.equalsIgnoreCase("*") || cell.equalsIgnoreCase(snt.cellId));
                })
                .forEach(p -> sender.sendMessage("§7- §f" + p.getName()));
        return true;
    }
}
