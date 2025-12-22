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
            // Should be enforced by playerOnly(), but just in case
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // If already sitting on one of our seats -> stand up (toggle behavior)
        if (isSitting(p)) {
            standUp(p);
            Lang.send(p, "player.sit.stand", null, null);
            return true;
        }

        // Basic state checks
        if (p.getVehicle() != null) {
            Lang.send(p, "player.sit.already-riding", null, null);
            return true;
        }
        if (!p.isOnGround()) {
            Lang.send(p, "player.sit.not-on-ground", null, null);
            return true;
        }
        if (p.isFlying() || p.isGliding()) {
            Lang.send(p, "player.sit.not-while-flying", null, null);
            return true;
        }

        // Determine base block to sit on
        Location playerLoc = p.getLocation();
        Block blockBelow = playerLoc.getBlock().getRelative(BlockFace.DOWN);

        // If literally in the air (edge cases like slabs / carpets), fallback
        if (blockBelow.getType() == Material.AIR) {
            blockBelow = playerLoc.getBlock();
        }

        if (blockBelow.getType() == Material.AIR) {
            Lang.send(p, "player.sit.no-block", null, null);
            return true;
        }

        // Compute center of the block with Y offset depending on type
        double x = blockBelow.getX() + 0.5;
        double z = blockBelow.getZ() + 0.5;
        double y = blockBelow.getY();

        double yOffset;
        if (Tag.STAIRS.isTagged(blockBelow.getType())) {
            // Sitting on stairs: lower to feel like you're on the step
            yOffset = 0.3;
        } else if (Tag.SLABS.isTagged(blockBelow.getType())) {
            // Half-block: slightly lower than full block
            yOffset = 0.2;
        } else {
            // Default: normal solid block / anything else
            yOffset = 0.4;
        }

        Location seatLoc = new Location(
                blockBelow.getWorld(),
                x,
                y + yOffset,
                z,
                playerLoc.getYaw(), // keep player's facing direction
                0f
        );

        // Clean up any existing Oreo seats at this exact block (avoid stacking armor stands)
        cleanupExistingSeats(blockBelow, seatLoc);

        // Spawn invisible marker armor stand as the "seat"
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

        // Mount the player
        seat.addPassenger(p);

        // Feedback to player with simple placeholder
        Lang.send(
                p,
                "player.sit.sat",
                null,
                Map.of("block", blockBelow.getType().name().toLowerCase())
        );

        return true;
    }

    /**
     * Checks if the player is currently sitting on an OreoEssentials seat.
     */
    private boolean isSitting(Player p) {
        Entity vehicle = p.getVehicle();
        if (vehicle == null) return false;
        if (!(vehicle instanceof ArmorStand stand)) return false;
        return stand.getScoreboardTags().contains(SEAT_TAG);
    }

    /**
     * Makes the player stand up if riding an OreoEssentials sit seat.
     * Also removes the underlying armor stand.
     */
    private void standUp(Player p) {
        Entity vehicle = p.getVehicle();
        if (vehicle instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(SEAT_TAG)) {
            p.eject();
            stand.remove();
        }
    }

    /**
     * Remove any existing SEAT_TAG armor stands at / near the same block
     * to avoid leftover seats or stacked seats.
     */
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
