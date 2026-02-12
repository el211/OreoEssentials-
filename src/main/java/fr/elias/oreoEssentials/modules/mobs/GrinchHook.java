package fr.elias.oreoEssentials.modules.mobs;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;


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
                grinchManager = uc.getClass().getMethod("getGrinchManager").invoke(uc);
            } catch (Throwable ignored) {
                grinchManager = null;
            }
        }
    }


    public static boolean isGrinch(Entity e) {
        ensure();
        if (uc == null || e == null) return false;


        try {
            var m = uc.getClass().getMethod("isGrinchEntity", Entity.class);
            Object out = m.invoke(uc, e);
            if (out instanceof Boolean b) return b;
        } catch (Throwable ignored) { }

        if (grinchManager != null) {
            try {
                var m = grinchManager.getClass().getMethod("isGrinch", Entity.class);
                Object out = m.invoke(grinchManager, e);
                if (out instanceof Boolean b) return b;
            } catch (Throwable ignored) { }
            try {
                var m = grinchManager.getClass().getMethod("isGrinchEntity", Entity.class);
                Object out = m.invoke(grinchManager, e);
                if (out instanceof Boolean b) return b;
            } catch (Throwable ignored) { }
        }

        return false;
    }
}
