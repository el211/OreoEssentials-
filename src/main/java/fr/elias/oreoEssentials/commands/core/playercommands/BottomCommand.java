package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BottomCommand implements OreoCommand {

    @Override
    public String name() {
        return "bottom";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.bottom";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        Location loc = p.getLocation();
        World world = p.getWorld();

        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int minY = world.getMinHeight();

        // Find the lowest solid, non-air block scanning upward from bedrock
        int floorY = -1;
        for (int y = minY; y < world.getMaxHeight() - 1; y++) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (!type.isAir() && type != Material.WATER && type != Material.LAVA
                    && type != Material.KELP && type != Material.KELP_PLANT
                    && type != Material.SEAGRASS && type != Material.TALL_SEAGRASS) {
                // Make sure there are two air blocks above to stand in
                Material above1 = world.getBlockAt(x, y + 1, z).getType();
                Material above2 = world.getBlockAt(x, y + 2, z).getType();
                if (above1.isAir() && above2.isAir()) {
                    floorY = y;
                    break;
                }
            }
        }

        if (floorY == -1) {
            Lang.send(p, "bottom.no-floor", "<red>No safe floor found below you.</red>");
            return true;
        }

        Location dest = new Location(world, x + 0.5, floorY + 1, z + 0.5, loc.getYaw(), loc.getPitch());
        p.teleport(dest);
        Lang.send(p, "bottom.teleported", "<green>Teleported to the bottom.</green>");
        return true;
    }
}
