package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class DelHomeCommand implements OreoCommand {
    private final HomeService homes;

    public DelHomeCommand(HomeService homes) { this.homes = homes; }

    @Override public String name() { return "delhome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.delhome"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            // usage via lang with fallback
            Lang.send(sender,
                    "delhome.usage",
                    "§eUsage: /%label% <name>",
                    Map.of("label", label));
            return true;
        }

        Player p = (Player) sender;
        String rawName = args[0];

        if (homes.delHome(p.getUniqueId(), rawName)) {
            Lang.send(p,
                    "delhome.removed",
                    "§aHome §e%name% §ahas been removed.",
                    Map.of("name", rawName));
        } else {
            Lang.send(p,
                    "delhome.not-found",
                    "§cNo home named §e%name%§c.",
                    Map.of("name", rawName));
        }
        return true;
    }
}
