package fr.elias.oreoEssentials.modules.bossbar;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;


public class BossBarToggleCommand implements OreoCommand {

    private final BossBarService service;

    public BossBarToggleCommand(BossBarService service) {
        this.service = service;
    }

    @Override public String name() { return "bossbar"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.bossbar.toggle"; }
    @Override public String usage() { return "toggle"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            boolean currentlyShown = service.isShown(p);
            if (currentlyShown) {
                service.hide(p);
                p.sendMessage(ChatColor.YELLOW + "BossBar disabled.");
            } else {
                service.show(p);
                p.sendMessage(ChatColor.GREEN + "BossBar enabled.");
            }
            return true;
        }

        p.sendMessage(ChatColor.RED + "Usage: /" + label + " " + usage());
        return true;
    }
}
