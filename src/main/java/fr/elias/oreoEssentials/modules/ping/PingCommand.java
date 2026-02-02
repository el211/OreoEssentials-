// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/PingCommand.java
package fr.elias.oreoEssentials.modules.ping;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class PingCommand implements OreoCommand {
    @Override public String name() { return "ping"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ping"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        int ping = p.getPing(); // 1.21 API

        Lang.send(p, "ping.self",
                "<green>Your ping: <yellow>%ping%</yellow>ms</green>",
                Map.of("ping", String.valueOf(ping)));

        return true;
    }
}