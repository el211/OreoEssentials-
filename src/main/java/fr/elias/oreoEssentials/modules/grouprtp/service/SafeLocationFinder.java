package fr.elias.oreoEssentials.modules.grouprtp.service;

import fr.elias.oreoEssentials.modules.grouprtp.model.GroupRtpPortalDef;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Safe random-location finder for Group RTP Portals.
 *
 * Based on the existing {@code RtpCommand.findSafeLocation} algorithm but
 * extracted as a standalone utility so it can be called from an async context
 * ({@code OreScheduler.runAsync}) without blocking the main thread.
 *
 * <p>Biome blacklisting is an extension over the base RTP module.</p>
 *
 * <h3>Threading</h3>
 * Always call {@link #find} from an <em>async</em> thread.
 * The method uses synchronous {@code chunk.load(true)} which is safe in async
 * on Paper (it force-loads the chunk from a thread-pool thread); on standard
 * Spigot it is also fine but blocks that thread briefly.
 */
public final class SafeLocationFinder {

    private SafeLocationFinder() {}

    /**
     * Attempts to find a safe landing location matching the portal's RTP config.
     *
     * @param world  already-resolved target world (non-null)
     * @param def    portal definition carrying all RTP parameters
     * @return a safe {@link Location} or {@code null} if none found within attempts
     */
    public static Location find(World world, GroupRtpPortalDef def) {
        return find(world,
                def.getRtpRadius(), def.getRtpMinRadius(),
                def.getRtpCenterX(), def.getRtpCenterZ(),
                def.getRtpMinY(), def.getRtpMaxY(), def.getRtpAttempts(),
                def.getUnsafeBlocks(), def.getBlacklistedBiomes());
    }

    /**
     * Overload that accepts raw RTP parameters — used by the cross-server broker
     * which receives params via {@code GroupRtpSyncPacket} without a portal def.
     */
    public static Location find(World world,
            double radius, double minRadius, double cx, double cz,
            int minY, int maxY, int attempts,
            Set<String> unsafeBlocks, Set<String> blacklistedBiomes) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        minRadius = Math.max(0, minRadius);
        minY      = Math.max(5, minY);
        maxY      = Math.min(world.getMaxHeight() - 1, maxY);

        Set<String> unsafe         = unsafeBlocks;
        Set<String> badBiomes      = blacklistedBiomes;
        boolean     isNether       = world.getEnvironment() == World.Environment.NETHER;
        boolean     checkBiomes    = !badBiomes.isEmpty();

        for (int i = 0; i < attempts; i++) {
            double angle = rnd.nextDouble(0, Math.PI * 2);
            double dist  = rnd.nextDouble(minRadius, Math.max(minRadius + 1, radius + 1));

            int x = (int) Math.round(cx + Math.cos(angle) * dist);
            int z = (int) Math.round(cz + Math.sin(angle) * dist);

            // ── Chunk loading ─────────────────────────────────────────────────
            Chunk chunk = world.getChunkAt(new Location(world, x, 0, z));
            if (!chunk.isLoaded()) {
                chunk.load(true);   // safe in async on Paper/Spigot
            }

            // ── Find ground level ─────────────────────────────────────────────
            int top = isNether ? Math.min(maxY, 120) : maxY;
            int y   = top;
            while (y > minY && world.getBlockAt(x, y, z).isEmpty()) {
                y--;
            }
            if (y <= minY) continue;

            Block ground = world.getBlockAt(x, y, z);
            Block feet   = world.getBlockAt(x, y + 1, z);
            Block head   = world.getBlockAt(x, y + 2, z);

            // ── Nether-specific checks ────────────────────────────────────────
            if (isNether) {
                if (y >= 126) continue;
                if (ground.getType() == Material.BEDROCK && y >= 120) continue;
            }

            // ── Clearance check ───────────────────────────────────────────────
            if (!feet.isEmpty() || !head.isEmpty()) continue;

            // ── Surface safety ────────────────────────────────────────────────
            if (ground.isLiquid()) continue;
            if (unsafe.contains(ground.getType().name())) continue;

            // ── Biome blacklist ───────────────────────────────────────────────
            if (checkBiomes) {
                String biome = ground.getBiome().name().toUpperCase(java.util.Locale.ROOT);
                if (badBiomes.stream().anyMatch(b -> b.toUpperCase(java.util.Locale.ROOT).equals(biome))) continue;
            }

            // ── Success ───────────────────────────────────────────────────────
            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            loc.setYaw(rnd.nextFloat() * 360f);
            loc.setPitch(0f);
            return loc;
        }

        return null;
    }

    /**
     * Returns a safe spot within {@code clusterRadius} blocks of {@code base},
     * or {@code base} itself if no better spot is found quickly.
     * Used to scatter a group of players around the primary landing zone.
     */
    public static Location scatter(Location base, double clusterRadius, World world,
                                   Set<String> unsafeBlocks) {
        if (clusterRadius <= 0) return base.clone();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rnd.nextDouble(0, Math.PI * 2);
            double dist  = rnd.nextDouble(1, clusterRadius + 1);

            int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Chunk chunk = world.getChunkAt(new Location(world, x, 0, z));
            if (!chunk.isLoaded()) chunk.load(true);

            // Search downward from base Y ± clusterRadius
            int startY = Math.min(base.getBlockY() + (int) clusterRadius, world.getMaxHeight() - 3);
            for (int y = startY; y >= base.getBlockY() - (int) clusterRadius - 5 && y > 1; y--) {
                Block ground = world.getBlockAt(x, y, z);
                Block feet   = world.getBlockAt(x, y + 1, z);
                Block head   = world.getBlockAt(x, y + 2, z);
                if (!ground.isEmpty() && !ground.isLiquid()
                        && !unsafeBlocks.contains(ground.getType().name())
                        && feet.isEmpty() && head.isEmpty()) {
                    Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                    loc.setYaw(rnd.nextFloat() * 360f);
                    return loc;
                }
            }
        }
        return base.clone();
    }
}
