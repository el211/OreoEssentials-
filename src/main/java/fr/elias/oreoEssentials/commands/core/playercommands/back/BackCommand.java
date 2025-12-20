package fr.elias.oreoEssentials.commands.core.playercommands.back;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.TeleportService;
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

        // ✅ Use the new raw cross-server API
        BackLocation last = backService.getLastRaw(uuid);
        if (last == null) {
            p.sendMessage("§cNo last location recorded.");
            return true;
        }

        String localServer = plugin.getConfigService().serverName();

        // 📍 Same server → normal Bukkit teleport
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

// 🌐 Other server → cross-server broker (packet + server switch handled by broker)
        var broker = plugin.getBackBroker();
        if (broker == null) {
            p.sendMessage("§cBack cross-server broker not available.");
            return true;
        }

// IMPORTANT: the broker method takes 2 args in your project
        broker.requestCrossServerBack(p, last);

        p.sendMessage("§aTeleported back.");
        return true;



    }
}
