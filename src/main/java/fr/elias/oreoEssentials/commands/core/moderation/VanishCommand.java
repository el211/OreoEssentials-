package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.VanishService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class VanishCommand implements OreoCommand {
    private final VanishService service;

    public VanishCommand(VanishService service) {
        this.service = service;
    }

    @Override public String name() { return "vanish"; }
    @Override public List<String> aliases() { return List.of("v"); }
    @Override public String permission() { return "oreo.vanish"; }
    @Override public String usage() { return "[on|off|toggle]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("on", "off", "toggle").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        boolean nowVanished;
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            nowVanished = service.toggle(p);
        } else if (args[0].equalsIgnoreCase("on")) {
            service.setVanished(p, true);
            nowVanished = true;
        } else if (args[0].equalsIgnoreCase("off")) {
            service.setVanished(p, false);
            nowVanished = false;
        } else {
            return false;
        }

        if (nowVanished) {
            Lang.send(p, "moderation.vanish.enabled",
                    "<green>You are now vanished.</green>");
        } else {
            Lang.send(p, "moderation.vanish.disabled",
                    "<yellow>You are now visible.</yellow>");
        }

        return true;
    }
}
