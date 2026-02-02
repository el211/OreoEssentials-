// File: src/main/java/fr/elias/oreoEssentials/events/MythicHook.java
package fr.elias.oreoEssentials.modules.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Optional;

public final class MythicHook {
    private static final boolean PRESENT;
    private static final Method INST;            // MythicBukkit.inst() or getInstance()
    private static final Method GET_MOB_MANAGER; // mythic.getMobManager()
    private static final Method IS_MYTHIC_MOB;   // mobManager.isMythicMob(Entity)
    private static final Method GET_INSTANCE;    // mobManager.getMythicMobInstance(Entity) -> Optional<ActiveMob>
    private static final Method AM_GET_TYPE;     // ActiveMob.getType() / getMobType()
    private static final Method MT_GET_INTERNAL; // MobType.getInternalName()
    private static final Method AM_GET_DISPLAY;  // ActiveMob.getDisplayName() (may be null)

    static {
        // temps to avoid "might already have been assigned" on finals
        Method tINST = null;
        Method tGET_MOB_MANAGER = null;
        Method tIS_MYTHIC_MOB = null;
        Method tGET_INSTANCE = null;
        Method tAM_GET_TYPE = null;
        Method tMT_GET_INTERNAL = null;
        Method tAM_GET_DISPLAY = null;
        boolean present = false;

        try {
            // Try modern class first, then very old one
            Class<?> mythicCls;
            try {
                mythicCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            } catch (ClassNotFoundException e) {
                mythicCls = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
            }

            // Static accessor: inst() or getInstance()
            try { tINST = mythicCls.getMethod("inst"); }
            catch (NoSuchMethodException e) { tINST = mythicCls.getMethod("getInstance"); }

            Object mythicObj = tINST.invoke(null);

            // Mob manager + methods
            tGET_MOB_MANAGER = mythicObj.getClass().getMethod("getMobManager");
            Object mobMgr = tGET_MOB_MANAGER.invoke(mythicObj);

            tIS_MYTHIC_MOB = mobMgr.getClass().getMethod("isMythicMob", Entity.class);

            try {
                tGET_INSTANCE = mobMgr.getClass().getMethod("getMythicMobInstance", Entity.class);
            } catch (NoSuchMethodException e) {
                tGET_INSTANCE = mobMgr.getClass().getMethod("getActiveMob", Entity.class);
            }

            // ActiveMob + MobType reflections
            Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            try { tAM_GET_TYPE = activeMobClass.getMethod("getType"); }
            catch (NoSuchMethodException e) { tAM_GET_TYPE = activeMobClass.getMethod("getMobType"); }

            try { tAM_GET_DISPLAY = activeMobClass.getMethod("getDisplayName"); } catch (NoSuchMethodException ignored) {}

            Class<?> mobTypeClass = Class.forName("io.lumine.mythic.core.mobs.MobType");
            tMT_GET_INTERNAL = mobTypeClass.getMethod("getInternalName");

            // Only mark present if plugin is actually enabled
            present = (Bukkit.getPluginManager().getPlugin("MythicMobs") != null)
                    || (Bukkit.getPluginManager().getPlugin("MythicBukkit") != null);
        } catch (Throwable ignored) {
            // leave temps null; present stays false
        }

        INST = tINST;
        GET_MOB_MANAGER = tGET_MOB_MANAGER;
        IS_MYTHIC_MOB = tIS_MYTHIC_MOB;
        GET_INSTANCE = tGET_INSTANCE;
        AM_GET_TYPE = tAM_GET_TYPE;
        MT_GET_INTERNAL = tMT_GET_INTERNAL;
        AM_GET_DISPLAY = tAM_GET_DISPLAY;
        PRESENT = present;
    }

    public static boolean isPresent() { return PRESENT; }

    public static MythicInfo resolve(Entity entity) {
        if (!PRESENT || entity == null || INST == null || GET_MOB_MANAGER == null
                || IS_MYTHIC_MOB == null || GET_INSTANCE == null || AM_GET_TYPE == null || MT_GET_INTERNAL == null) {
            return MythicInfo.none();
        }
        try {
            Object mythicObj = INST.invoke(null);
            Object mobMgr = GET_MOB_MANAGER.invoke(mythicObj);

            boolean isMythic = (boolean) IS_MYTHIC_MOB.invoke(mobMgr, entity);
            if (!isMythic) return MythicInfo.none();

            Object opt = GET_INSTANCE.invoke(mobMgr, entity); // Optional<ActiveMob>
            if (!(opt instanceof Optional<?> activeOpt) || activeOpt.isEmpty()) return MythicInfo.none();

            Object activeMob = activeOpt.get();
            Object mobType = AM_GET_TYPE.invoke(activeMob);
            String internal = (String) MT_GET_INTERNAL.invoke(mobType);

            String display = null;
            if (AM_GET_DISPLAY != null) {
                try { display = (String) AM_GET_DISPLAY.invoke(activeMob); } catch (Throwable ignored) {}
            }
            return new MythicInfo(internal, display);
        } catch (Throwable ignored) {
            return MythicInfo.none();
        }
    }

    public record MythicInfo(String internalName, String displayName) {
        public static MythicInfo none() { return new MythicInfo(null, null); }
        public boolean isMythic() { return internalName != null && !internalName.isEmpty(); }
    }
}
