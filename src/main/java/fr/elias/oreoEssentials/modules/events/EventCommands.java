// File: src/main/java/fr/elias/oreoEssentials/events/EventCommands.java
package fr.elias.oreoEssentials.modules.events;

import org.bukkit.command.*;

import java.util.Collections;
import java.util.List;

public final class EventCommands implements TabExecutor {
    private final EventConfig events;
    private final DeathMessageService deaths;

    public EventCommands(EventConfig events, DeathMessageService deaths) {
        this.events = events; this.deaths = deaths;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.events")) {
            sender.sendMessage("§cNo permission (oreo.events).");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("reload")) {
            events.reload();
            deaths.reload();
            sender.sendMessage("§aEvents & death messages reloaded.");
            return true;
        }
        sender.sendMessage("§eUsage: /oevents reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.events")) return Collections.emptyList();
        if (args.length == 1) return List.of("reload");
        return Collections.emptyList();
    }
}
