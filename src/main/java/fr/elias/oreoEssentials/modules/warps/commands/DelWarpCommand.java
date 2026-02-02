package fr.elias.oreoEssentials.modules.warps.commands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class DelWarpCommand implements OreoCommand {
    private final WarpService warps;

    public DelWarpCommand(WarpService warps) {
        this.warps = warps;
    }

    @Override public String name() { return "delwarp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.delwarp"; }
    @Override public String usage() { return "<n>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "admin.delwarp.usage",
                    "<yellow>Usage: /%label% <n></yellow>",
                    Map.of("label", label));
            return true;
        }

        String name = args[0].toLowerCase();

        if (warps.delWarp(name)) {
            Lang.send(sender, "admin.delwarp.deleted",
                    "<green>Warp <aqua>%warp%</aqua> has been deleted.</green>",
                    Map.of("warp", name));
        } else {
            Lang.send(sender, "admin.delwarp.not-found",
                    "<red>Warp not found: <yellow>%warp%</yellow></red>",
                    Map.of("warp", name));
        }

        return true;
    }
}