package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TopCommand implements OreoCommand {

    @Override public String name() { return "top"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.top"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        Location loc = p.getLocation();
        World world = p.getWorld();

        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int maxY = world.getMaxHeight() - 1;

        int surfaceY = -1;
        for (int y = maxY; y >= world.getMinHeight(); y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (!type.isAir() && type != Material.WATER && type != Material.LAVA
                    && type != Material.KELP && type != Material.KELP_PLANT
                    && type != Material.SEAGRASS && type != Material.TALL_SEAGRASS) {
                surfaceY = y;
                break;
            }
        }

        if (surfaceY == -1) {
            Lang.send(p, "top.no-surface", "<red>No solid surface found above you.</red>");
            return true;
        }

        Location dest = new Location(world, x + 0.5, surfaceY + 1, z + 0.5, loc.getYaw(), loc.getPitch());
        if (OreScheduler.isFolia()) {
            p.teleportAsync(dest).whenComplete((ok, err) ->
                    OreScheduler.runForEntity(OreoEssentials.get(), p, () -> {
                        if (err == null && Boolean.TRUE.equals(ok)) {
                            Lang.send(p, "top.teleported", "<green>Teleported to the surface.</green>");
                        } else {
                            Lang.send(p, "top.failed", "<red>Teleport failed.</red>");
                        }
                    }));
        } else {
            if (p.teleport(dest)) {
                Lang.send(p, "top.teleported", "<green>Teleported to the surface.</green>");
            } else {
                Lang.send(p, "top.failed", "<red>Teleport failed.</red>");
            }
        }
        return true;
    }
}
