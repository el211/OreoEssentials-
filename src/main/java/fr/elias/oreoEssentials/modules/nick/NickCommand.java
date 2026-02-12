package fr.elias.oreoEssentials.modules.nick;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NickCommand implements OreoCommand, TabCompleter {
    private static final int MAX_LEN = 16;
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    @Override public String name() { return "nick"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.nick"; }
    @Override public String usage() { return "<newName> | unnick"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 1) {
            Lang.send(p, "nick.usage",
                    "<yellow>Usage: /%label% <newName>  or  /%label% unnick</yellow>",
                    Map.of("label", label));
            return true;
        }

        if (args[0].equalsIgnoreCase("unnick") || args[0].equalsIgnoreCase("reset")) {
            resetNick(p);
            Lang.send(p, "nick.reset",
                    "<green>Your nickname has been reset.</green>");
            return true;
        }

        String newName = args[0];

        if (newName.length() > MAX_LEN) {
            Lang.send(p, "nick.too-long",
                    "<red>Name too long (max %max% chars).</red>",
                    Map.of("max", String.valueOf(MAX_LEN)));
            return true;
        }

        if (!VALID.matcher(newName).matches()) {
            Lang.send(p, "nick.invalid",
                    "<red>Invalid name. Use letters, numbers, underscore (3–16).</red>");
            return true;
        }

        try { p.setDisplayName(newName); } catch (Throwable ignored) {}
        try { p.setPlayerListName(newName); } catch (Throwable ignored) {}

        Lang.send(p, "nick.set",
                "<green>You are now nicked as <aqua>%nick%</aqua>.</green>",
                Map.of("nick", newName));

        return true;
    }


    private void resetNick(Player p) {
        String real = p.getName();
        try { p.setDisplayName(real); } catch (Throwable ignored) {}
        try { p.setPlayerListName(real); } catch (Throwable ignored) {}
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("unnick", "reset").stream()
                    .filter(opt -> opt.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}