package fr.elias.oreoEssentials.world;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lag-free world pre-generator using Paper's async chunk API.
 *
 * Strategy:
 *   - A 1-tick sync timer drains a queue of chunks to generate.
 *   - Each tick it submits up to DRAIN_PER_TICK new async futures,
 *     capped at MAX_CONCURRENT in-flight at once.
 *   - Chunk generation is fully off the main thread (getChunkAtAsync).
 *   - The main thread only spends a few microseconds per tick scheduling.
 *   - Progress is reported to the sender every 5% via a sync run.
 */
public final class WorldPreGenerator {

    /** Max concurrent in-flight async chunk requests. */
    private static final int MAX_CONCURRENT  = 10;
    /** Max new requests submitted per tick. */
    private static final int DRAIN_PER_TICK  = 4;
    /** Report progress every N% (must divide 100 evenly). */
    private static final int REPORT_STEP_PCT = 5;

    private final OreoEssentials plugin;
    private final World world;
    private final CommandSender sender;

    // flat arrays beat ArrayList for tight-loop access
    private final int[] chunksX;
    private final int[] chunksZ;
    private final int   totalChunks;

    private final AtomicInteger pending        = new AtomicInteger(0);
    private final AtomicInteger completed      = new AtomicInteger(0);
    private final AtomicBoolean cancelled      = new AtomicBoolean(false);

    // only touched from the global-region tick, so plain int is fine
    private int queueIndex       = 0;
    private int lastReportedStep = -1;

    private OreTask timerTask;

    /**
     * @param borderBlocks Full world-border diameter in blocks (e.g. 1000 → ±500 blocks from centre).
     */
    public WorldPreGenerator(OreoEssentials plugin, World world, int borderBlocks, CommandSender sender) {
        this.plugin = plugin;
        this.world  = world;
        this.sender = sender;

        int halfBorder  = Math.max(borderBlocks / 2, 16);
        int chunkRadius = (int) Math.ceil(halfBorder / 16.0);
        int diameter    = chunkRadius * 2 + 1;
        int total       = diameter * diameter;

        chunksX = new int[total];
        chunksZ = new int[total];

        int i = 0;
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                chunksX[i] = cx;
                chunksZ[i] = cz;
                i++;
            }
        }
        this.totalChunks = total;
    }

    public int getTotalChunks() { return totalChunks; }

    public boolean isCancelled() { return cancelled.get(); }

    /** Start pre-generation. Safe to call from the main thread. */
    public void start() {
        Lang.send(sender, "world.pregen.start",
                "<aqua>Pre-generating <white>" + totalChunks
                + "</white> chunks for world <white>" + world.getName()
                + "</white>. You will be notified as it progresses.</aqua>");

        // 1-tick period: minimal overhead, maximum throughput
        timerTask = OreScheduler.runTimer(plugin, this::tick, 1L, 1L);
    }

    /** Cancel an in-progress pre-generation. */
    public void cancel() {
        cancelled.set(true);
        if (timerTask != null) timerTask.cancel();
    }

    // -----------------------------------------------------------------------
    // Internal — runs on global-region tick (main thread on Paper, GT on Folia)
    // -----------------------------------------------------------------------

    private void tick() {
        if (cancelled.get()) return;

        int submitted = 0;
        while (queueIndex < totalChunks
                && pending.get() < MAX_CONCURRENT
                && submitted < DRAIN_PER_TICK) {

            final int cx = chunksX[queueIndex];
            final int cz = chunksZ[queueIndex];
            queueIndex++;
            submitted++;
            pending.incrementAndGet();

            // getChunkAtAsync — generates the chunk off the main thread,
            // saves it to disk, then completes the future.
            world.getChunkAtAsync(cx, cz).thenRun(() -> {
                if (cancelled.get()) {
                    pending.decrementAndGet();
                    return;
                }
                int done = completed.incrementAndGet();
                pending.decrementAndGet();
                reportIfNeeded(done);
                checkDone(done);
            });
        }
    }

    private void reportIfNeeded(int done) {
        // step = which REPORT_STEP_PCT% bucket we're in (0..19 for 5%)
        int stepsTotal = 100 / REPORT_STEP_PCT;
        int step = (done * stepsTotal) / totalChunks;
        if (step > lastReportedStep) {
            lastReportedStep = step;
            int pct = step * REPORT_STEP_PCT;
            OreScheduler.run(plugin, () ->
                    Lang.send(sender, "world.pregen.progress",
                            "<gray>[PreGen] <white>" + world.getName() + "</white>: "
                            + done + "/" + totalChunks + " (<green>" + pct + "%</green>)</gray>",
                            Map.of("world", world.getName(),
                                    "done",  String.valueOf(done),
                                    "total", String.valueOf(totalChunks),
                                    "pct",   String.valueOf(pct))));
        }
    }

    private void checkDone(int done) {
        if (done >= totalChunks && !cancelled.get()) {
            if (timerTask != null) timerTask.cancel();
            OreScheduler.run(plugin, () ->
                    Lang.send(sender, "world.pregen.done",
                            "<green>[PreGen] World <white>" + world.getName()
                            + "</white> fully pre-generated! "
                            + totalChunks + " chunks ready.</green>",
                            Map.of("world", world.getName(),
                                    "total", String.valueOf(totalChunks))));
        }
    }
}
