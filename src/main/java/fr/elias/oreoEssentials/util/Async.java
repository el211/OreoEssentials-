package fr.elias.oreoEssentials.util;


import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class Async {

    public static CompletableFuture<Void> run(Runnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static <T> CompletableFuture<T> run(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}
