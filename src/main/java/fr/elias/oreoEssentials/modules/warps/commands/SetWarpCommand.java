package fr.elias.oreoEssentials.modules.warps.commands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SetWarpCommand implements OreoCommand {
    private final WarpService warps;

    public SetWarpCommand(WarpService warps) {
        this.warps = warps;
    }

    @Override public String name() { return "setwarp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.setwarp"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "admin.setwarp.usage",
                    "<yellow>Usage: /%label% <name></yellow>",
                    Map.of("label", label));
            return true;
        }

        Player p = (Player) sender;
        String name = args[0].trim().toLowerCase(Locale.ROOT);
        org.bukkit.Location loc = p.getLocation();
        OreoEssentials plugin = OreoEssentials.get();
        String local = plugin.getConfig().getString("server.name", Bukkit.getServer().getName());

        Async.run(() -> {
            warps.setWarp(name, loc);
            WarpDirectory warpDir = plugin.getWarpDirectory();
            if (warpDir != null) warpDir.setWarpServer(name, local);

            OreScheduler.runForEntity(plugin, p, () -> {
                Lang.send(p, "admin.setwarp.set",
                        "<green>Warp <aqua>%warp%</aqua> has been set.</green>",
                        Map.of("warp", name));
                if (warpDir != null) {
                    Lang.send(p, "admin.setwarp.cross-server-info",
                            "<gray>(Cross-server) Warp owner set to <aqua>%server%</aqua>.</gray>",
                            Map.of("server", local));
                }
            });
        });

        return true;
    }
}