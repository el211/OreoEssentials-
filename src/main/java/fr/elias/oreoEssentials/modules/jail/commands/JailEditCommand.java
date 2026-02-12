package fr.elias.oreoEssentials.modules.jail.commands;

import fr.elias.oreoEssentials.modules.jail.JailModels;
import fr.elias.oreoEssentials.modules.jail.JailService;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class JailEditCommand implements CommandExecutor {
    private final JailService service;
    private Location p1, p2; // simple per-executor buffer (admin runs it)

    public JailEditCommand(JailService service) { this.service = service; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        if (a.length < 1) { help(sender); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "pos1" -> {
                p1 = p.getLocation().clone();
                sender.sendMessage("§aSet pos1.");
            }

            case "pos2" -> {
                p2 = p.getLocation().clone();
                sender.sendMessage("§aSet pos2.");
            }

            case "create", "save" -> {
                if (a.length < 2) {
                    sender.sendMessage("§c/jailedit " + a[0].toLowerCase(Locale.ROOT) + " <jailName>");
                    break;
                }
                if (p1 == null || p2 == null) {
                    sender.sendMessage("§cSet pos1 & pos2 first.");
                    break;
                }
                JailModels.Cuboid c = JailModels.Cuboid.of(p1, p2);
                service.createOrUpdateJail(a[1], c, p.getWorld().getName());
                sender.sendMessage("§aSaved jail §f" + a[1] + " §7(world=" + p.getWorld().getName() + ")");
            }

            case "addcell" -> {
                if (a.length < 3) {
                    sender.sendMessage("§c/jailedit addcell <jailName> <cellId>");
                    break;
                }
                boolean ok = service.addCell(a[1], a[2], p.getLocation());
                if (ok) sender.sendMessage("§aAdded cell §f" + a[2] + " §ato jail §f" + a[1]);
                else sender.sendMessage("§cUnknown jail or invalid region.");
            }

            case "delete", "remove" -> {
                if (a.length < 2) {
                    sender.sendMessage("§c/jailedit delete <jailName>");
                    break;
                }
                boolean deleted = service.deleteJail(a[1]);
                if (deleted) {
                    sender.sendMessage("§aDeleted jail §f" + a[1]);
                } else {
                    sender.sendMessage("§cJail §f" + a[1] + " §cdoes not exist.");
                }
            }

            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§e/jailedit pos1 §7– set first corner");
        s.sendMessage("§e/jailedit pos2 §7– set second corner");
        s.sendMessage("§e/jailedit create <jailName> §7– create/update jail (alias: §esave§7)");
        s.sendMessage("§e/jailedit addcell <jailName> <cellId> §7– add a cell at your location");
        s.sendMessage("§e/jailedit delete <jailName> §7– remove a jail (alias: §eremove§7)"); // ✅ NEW
    }
}
