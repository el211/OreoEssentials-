package fr.elias.oreoEssentials.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OeCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    @Override public String name() { return "oe"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.oe"; }
    @Override public String usage() { return "server <servername>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 2 || !args[0].equalsIgnoreCase("server")) {
            Lang.send(p, "oe.usage",
                    "<yellow>Usage: /%label% server <servername></yellow>",
                    Map.of("label", label));
            return true;
        }

        String targetServer = args[1];

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", out.toByteArray());

            Lang.send(p, "oe.connecting",
                    "<green>Connecting you to <aqua>%server%</aqua>...</green>",
                    Map.of("server", targetServer));
        } catch (Throwable t) {
            Lang.send(p, "oe.failed",
                    "<red>Could not connect you to %server%. Is the proxy/channel configured?</red>",
                    Map.of("server", targetServer));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission(permission())) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return List.of("server");
        }

        return Collections.emptyList();
    }
}