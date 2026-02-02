package fr.elias.oreoEssentials.modules.mobs;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/** Lightweight bridge to UltimateChristmas Grinch boss. Safe if Xmas isn’t installed. */
public final class GrinchHook {
    private static boolean lookedUp = false;
    private static UltimateChristmas uc = null;
    private static Object grinchManager = null;

    private GrinchHook() {}

    private static void ensure() {
        if (lookedUp) return;
        lookedUp = true;
        var p = Bukkit.getPluginManager().getPlugin("UltimateChristmas");
        if (p instanceof UltimateChristmas u) {
            uc = u;
            try {
                // Try to cache manager if it exists
                grinchManager = uc.getClass().getMethod("getGrinchManager").invoke(uc);
            } catch (Throwable ignored) {
                grinchManager = null;
            }
        }
    }

    /** Returns true if entity is the Grinch boss (if present). */
    public static boolean isGrinch(Entity e) {
        ensure();
        if (uc == null || e == null) return false;

        // Preferred fast path: UltimateChristmas exposes isGrinchEntity(Entity)
        try {
            var m = uc.getClass().getMethod("isGrinchEntity", Entity.class);
            Object out = m.invoke(uc, e);
            if (out instanceof Boolean b) return b;
        } catch (Throwable ignored) { }

        // Fallback: manager.has/is methods
        if (grinchManager != null) {
            try {
                // try isGrinch(Entity)
                var m = grinchManager.getClass().getMethod("isGrinch", Entity.class);
                Object out = m.invoke(grinchManager, e);
                if (out instanceof Boolean b) return b;
            } catch (Throwable ignored) { }
            try {
                // try isGrinchEntity(Entity)
                var m = grinchManager.getClass().getMethod("isGrinchEntity", Entity.class);
                Object out = m.invoke(grinchManager, e);
                if (out instanceof Boolean b) return b;
            } catch (Throwable ignored) { }
        }

        return false;
    }
}
