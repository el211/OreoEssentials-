// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/DelHomeCommand.java
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

    public DelHomeCommand(HomeService homes) {
        this.homes = homes;
    }

    @Override public String name() { return "delhome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.delhome"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            // Show usage message with proper MiniMessage format
            Lang.send(sender,
                    "delhome.usage",
                    "<yellow>Usage: /%label% <name></yellow>",
                    Map.of("label", label));
            return true;
        }

        Player p = (Player) sender;
        String rawName = args[0];

        // Attempt to delete the home
        if (homes.delHome(p.getUniqueId(), rawName)) {
            Lang.send(p,
                    "delhome.removed",
                    "<green>Home <yellow>%name%</yellow> has been removed.</green>",
                    Map.of("name", rawName));
        } else {
            Lang.send(p,
                    "delhome.not-found",
                    "<red>No home named <yellow>%name%</yellow>.</red>",
                    Map.of("name", rawName));
        }

        return true;
    }
}