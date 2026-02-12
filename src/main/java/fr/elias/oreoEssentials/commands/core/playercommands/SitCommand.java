// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/SitCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class SitCommand implements OreoCommand {

    /**
     * Scoreboard tag used to mark OreoEssentials sit seats.
     * The SitListener can rely on this to detect "our" armor stands.
     */
    public static final String SEAT_TAG = "oreo_sit_seat";

    @Override
    public String name() {
        return "sit";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.sit";
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
        if (!(sender instanceof Player p)) {
            Lang.send(sender, "sit.player-only",
                    "<red>Only players can use this command.</red>");
            return true;
        }

        if (isSitting(p)) {
            standUp(p);
            Lang.send(p, "sit.stand",
                    "<gray>You stood up.</gray>");
            return true;
        }

        if (p.getVehicle() != null) {
            Lang.send(p, "sit.already-riding",
                    "<red>You're already riding something.</red>");
            return true;
        }

        if (!p.isOnGround()) {
            Lang.send(p, "sit.not-on-ground",
                    "<red>You must be on the ground to sit.</red>");
            return true;
        }

        if (p.isFlying() || p.isGliding()) {
            Lang.send(p, "sit.not-while-flying",
                    "<red>You can't sit while flying or gliding.</red>");
            return true;
        }

        Location playerLoc = p.getLocation();
        Block blockBelow = playerLoc.getBlock().getRelative(BlockFace.DOWN);

        if (blockBelow.getType() == Material.AIR) {
            blockBelow = playerLoc.getBlock();
        }

        if (blockBelow.getType() == Material.AIR) {
            Lang.send(p, "sit.no-block",
                    "<red>There's no block to sit on.</red>");
            return true;
        }

        double x = blockBelow.getX() + 0.5;
        double z = blockBelow.getZ() + 0.5;
        double y = blockBelow.getY();

        double yOffset;
        if (Tag.STAIRS.isTagged(blockBelow.getType())) {
            yOffset = 0.3;
        } else if (Tag.SLABS.isTagged(blockBelow.getType())) {
            yOffset = 0.2;
        } else {
            yOffset = 0.4;
        }

        Location seatLoc = new Location(
                blockBelow.getWorld(),
                x,
                y + yOffset,
                z,
                playerLoc.getYaw(),
                0f
        );

        cleanupExistingSeats(blockBelow, seatLoc);

        ArmorStand seat = blockBelow.getWorld().spawn(seatLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);         // no hitbox
            as.setGravity(false);
            as.setSmall(true);
            as.setPersistent(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setCollidable(false);
            as.setBasePlate(false);
            as.addScoreboardTag(SEAT_TAG);
        });

        seat.addPassenger(p);

        Lang.send(p, "sit.sat",
                "<green>You sat down on <yellow>%block%</yellow>.</green>",
                Map.of("block", blockBelow.getType().name().toLowerCase()));

        return true;
    }


    private boolean isSitting(Player p) {
        Entity vehicle = p.getVehicle();
        if (vehicle == null) return false;
        if (!(vehicle instanceof ArmorStand stand)) return false;
        return stand.getScoreboardTags().contains(SEAT_TAG);
    }


    private void standUp(Player p) {
        Entity vehicle = p.getVehicle();
        if (vehicle instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(SEAT_TAG)) {
            p.eject();
            stand.remove();
        }
    }


    private void cleanupExistingSeats(Block blockBelow, Location seatLoc) {
        double radius = 0.6; // small radius around the block center
        for (Entity e : blockBelow.getWorld().getNearbyEntities(seatLoc, radius, radius, radius)) {
            if (e instanceof ArmorStand stand &&
                    stand.getScoreboardTags().contains(SEAT_TAG) &&
                    stand.getPassengers().isEmpty()) {
                // Only remove empty / orphan seats
                stand.remove();
            }
        }
    }
}