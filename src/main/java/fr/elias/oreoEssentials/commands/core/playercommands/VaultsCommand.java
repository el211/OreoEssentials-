// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/VaultsCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class VaultsCommand implements OreoCommand {

    @Override public String name() { return "oevault"; }
    @Override public List<String> aliases() { return List.of("vault", "vaults", "pv", "pvault"); }
    @Override public String permission() { return "oreo.vault.menu"; }
    @Override public String usage() { return "[menu|<id>]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Lang.send(sender, "vaults.player-only",
                    "<red>Players only.</red>");
            return true;
        }

        var svc = OreoEssentials.get().getPlayervaultsService();
        if (svc == null || !svc.enabled()) {
            Lang.send(p, "vaults.disabled",
                    "<red>PlayerVaults are disabled.</red>");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            svc.openMenu(p);
            return true;
        }

        try {
            int id = Integer.parseInt(args[0]);
            if (id <= 0) throw new NumberFormatException();
            svc.openVault(p, id);
        } catch (NumberFormatException ex) {
            Lang.send(p, "vaults.usage",
                    "<red>Usage: /%label% %usage%</red>",
                    Map.of("label", label, "usage", usage()));
        }

        return true;
    }
}