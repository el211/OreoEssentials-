package fr.elias.oreoEssentials.modules.mobs;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.santa.SantaManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Lightweight bridge to UltimateChristmas Santa.
 * Safe even if UltimateChristmas isn't installed.
 */
public final class SantaHook {

    private static SantaManager cachedSantaManager = null;
    private static boolean lookedUp = false;

    private SantaHook() {}

    private static SantaManager getSantaManager() {
        // only try lookup once to avoid spam
        if (lookedUp) return cachedSantaManager;
        lookedUp = true;

        var plugin = Bukkit.getPluginManager().getPlugin("UltimateChristmas");
        if (plugin instanceof UltimateChristmas uc) {
            try {
                cachedSantaManager = uc.getSantaManager();
            } catch (Throwable ignored) {
                cachedSantaManager = null;
            }
        }
        return cachedSantaManager;
    }

    public static boolean isSanta(Entity e) {
        SantaManager sm = getSantaManager();
        if (sm == null || e == null) return false;
        try {
            return sm.isSanta(e);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
