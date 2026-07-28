package fr.elias.oreoEssentials.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Async {

    private static final Logger LOGGER = Logger.getLogger("OreoEssentials");

    public static CompletableFuture<Void> run(Runnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                // DC6: route to plugin logger instead of System.err
                LOGGER.log(Level.SEVERE, "Async task error", e);
            }
        });
    }

    public static <T> CompletableFuture<T> run(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                // DC6: route to plugin logger instead of System.err
                LOGGER.log(Level.SEVERE, "Async task error", e);
                return null;
            }
        });
    }
}
