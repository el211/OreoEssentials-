package fr.elias.oreoEssentials.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Generates an empty (void) world with a single bedrock block at (0, 64, 0).
 * The bedrock block gives players a platform to land on when teleporting.
 * All vanilla generation passes (noise, surface, caves, structures…) are disabled.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    /** World-space Y of the spawn platform bedrock block. */
    public static final int SPAWN_Y = 64;

    @Override public boolean shouldGenerateNoise()       { return false; }
    @Override public boolean shouldGenerateSurface()     { return false; }
    @Override public boolean shouldGenerateBedrock()     { return false; }
    @Override public boolean shouldGenerateCaves()       { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs()        { return false; }
    @Override public boolean shouldGenerateStructures()  { return false; }

    /**
     * New Paper 1.18+ generation API.
     * Place a single bedrock block in chunk (0,0) at local pos (0, SPAWN_Y, 0).
     */
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                               int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        if (chunkX == 0 && chunkZ == 0) {
            chunkData.setBlock(0, SPAWN_Y, 0, Material.BEDROCK);
        }
    }

    /**
     * Legacy Bukkit API override — kept for maximum server compatibility.
     */
    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public @NotNull ChunkData generateChunkData(@NotNull org.bukkit.World world,
                                                 @NotNull Random random,
                                                 int chunkX, int chunkZ,
                                                 @NotNull BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        if (chunkX == 0 && chunkZ == 0) {
            data.setBlock(0, SPAWN_Y, 0, Material.BEDROCK);
        }
        return data;
    }
}
