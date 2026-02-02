package fr.elias.oreoEssentials.modules.back.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.modules.back.service.BackService;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class BackCommand implements OreoCommand {

    private final BackService backService;
    private final TeleportService teleportService;
    private final OreoEssentials plugin;

    public BackCommand(BackService backService, TeleportService teleportService, OreoEssentials plugin) {
        this.backService = backService;
        this.teleportService = teleportService;
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "back";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.back";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        UUID uuid = p.getUniqueId();

        // Get the last back location
        BackLocation last = backService.getLastRaw(uuid);
        if (last == null) {
            p.sendMessage("§cNo last location recorded.");
            return true;
        }

        String localServer = plugin.getConfigService().serverName();

        if (last.getServer().equalsIgnoreCase(localServer)) {
            Location loc = last.toLocalLocation();
            if (loc == null) {
                p.sendMessage("§cLast location world is not available.");
                return true;
            }
            p.teleport(loc);
            p.sendMessage("§aTeleported back.");
            return true;
        }

        var broker = plugin.getBackBroker();
        if (broker == null) {
            p.sendMessage("§cBack cross-server system is not available.");
            return true;
        }

        p.sendMessage("§aTeleporting back to §e" + last.getServer() + "§a...");

        // Now request the cross-server back (this will kick player to other server)
        broker.requestCrossServerBack(p, last);

        return true;
    }
}