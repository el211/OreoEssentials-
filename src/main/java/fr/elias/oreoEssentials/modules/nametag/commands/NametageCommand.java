package fr.elias.oreoEssentials.modules.nametag.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.nametag.NametageToggleStore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /nametag toggle [player]
 *   toggle       — toggle your own nametag visibility to others
 *   toggle <p>   — (admin) toggle another player's nametag
 */
public final class NametageCommand implements OreoCommand {

    private final OreoEssentials plugin;
    private final NametageToggleStore store;

    public NametageCommand(OreoEssentials plugin, NametageToggleStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override public String name()        { return "nametag"; }
    @Override public List<String> aliases() { return List.of("nt"); }
    @Override public String permission()  { return "oe.nametag.use"; }
    @Override public String usage()       { return "toggle [player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle" -> {
                if (args.length == 1) {
                    // Toggle own nametag
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("§cOnly players can toggle their own nametag.");
                        return true;
                    }
                    boolean nowOff = store.toggle(p.getUniqueId());
                    refreshPlayer(p);
                    if (nowOff) {
                        p.sendMessage("§7Your nametag is now §chidden§7 from other players.");
                    } else {
                        p.sendMessage("§7Your nametag is now §avisible§7 to other players.");
                    }
                } else {
                    // Admin toggle another player
                    if (!sender.hasPermission("oe.nametag.admin")) {
                        sender.sendMessage("§cNo permission.");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("§cPlayer §e" + args[1] + "§c not found.");
                        return true;
                    }
                    boolean nowOff = store.toggle(target.getUniqueId());
                    refreshPlayer(target);
                    String state = nowOff ? "§chidden" : "§avisible";
                    sender.sendMessage("§7Nametag for §e" + target.getName() + "§7 is now " + state + "§7.");
                    target.sendMessage("§7Your nametag was toggled " + state + "§7 by an admin.");
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) return List.of("toggle");
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission("oe.nametag.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private void refreshPlayer(Player player) {
        var nm = plugin.getNametagManager();
        if (nm != null) nm.updateNametag(player);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6/nametag toggle §7— Toggle your nametag visibility");
        if (sender.hasPermission("oe.nametag.admin")) {
            sender.sendMessage("§6/nametag toggle <player> §7— Toggle another player's nametag");
        }
    }
}
