package fr.elias.oreoEssentials.modules.mobs;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.LivingEntity;

final class MythicMobsHook {
    private MythicMobsHook() {}

    static String tryName(LivingEntity le) {
        try {
            if (MythicBukkit.inst() == null) return null;
            ActiveMob am = MythicBukkit.inst().getMobManager().getActiveMob(le.getUniqueId()).orElse(null);
            if (am == null) return null;
            String n = am.getDisplayName();
            return (n == null || n.isBlank()) ? null : n;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
