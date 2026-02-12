package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.ProxyMessenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ServerProxyCommand implements OreoCommand, org.bukkit.command.TabCompleter {
    private final ProxyMessenger proxy;

    public ServerProxyCommand(ProxyMessenger proxy) {
        this.proxy = proxy;
    }

    @Override public String name() { return "oeserver"; }
    @Override public List<String> aliases() { return List.of("server"); }
    @Override public String permission() { return "oreo.server"; }
    @Override public String usage() { return "<server-name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 1) {
            Lang.send(sender, "server.usage",
                    "<yellow>Usage: /%label% <server-name></yellow>",
                    Map.of("label", label));
            return true;
        }

        String target = args[0];

        proxy.connect(p, target);

        Lang.send(sender, "server.connecting",
                "<gray>Connecting to <aqua>%server%</aqua>...</gray>",
                Map.of("server", target));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();

        if (args.length == 1) {
            // Kick off a refresh (async response updates cache)
            proxy.requestServers();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return proxy.getCachedServers().stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}