package fr.elias.oreoEssentials.modules.homes.home;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            try {
                Set<String> h = homes.homes(p.getUniqueId());
                if (h == null) return List.of();
                return h.stream()
                        .filter(n -> n.startsWith(prefix))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            } catch (Throwable t) {
                return Collections.emptyList();
            }
        }
        return List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Permission "oreo.delhome" is enforced by the CommandManager framework via permission()
        // before execute() is invoked; no explicit sender.hasPermission() check is needed here.
        if (args.length < 1) {
            Lang.send(sender,
                    "delhome.usage",
                    "<yellow>Usage: /%label% <name></yellow>",
                    Map.of("label", label));
            return true;
        }

        Player p = (Player) sender;
        String rawName = args[0];

        // If dialog confirm is enabled and this isn't already the confirmed execution, show dialog
        boolean isConfirmed = args.length >= 2 && args[1].equals("--confirmed");
        if (!isConfirmed) {
            var dm = OreoEssentials.get().getDialogManager();
            if (dm != null && dm.confirmDelHome(p, rawName)) {
                return true;
            }
        }

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