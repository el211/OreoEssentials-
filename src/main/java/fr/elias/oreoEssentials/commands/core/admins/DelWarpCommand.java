// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/DelWarpCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.WarpService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class DelWarpCommand implements OreoCommand {
    private final WarpService warps;

    public DelWarpCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "delwarp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.delwarp"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "warps.usage.delwarp", null, Map.of("label", label));
            return true;
        }
        String name = args[0].toLowerCase();
        if (warps.delWarp(name)) {
            Lang.send(sender, "warps.deleted", null, Map.of("warp", name));
        } else {
            Lang.send(sender, "warps.not-found", null, Map.of("warp", name));
        }
        return true;
    }
}
