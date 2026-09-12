package fr.elias.oreoEssentials.migration.cmi;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal reflection utilities for the CMI migration.
 *
 * All CMI API interaction goes through this class so that the
 * importers stay free of compile-time dependencies on CMI or CMILib.
 *
 * <h3>CMILocation handling</h3>
 * CMILocation (net.Zrips.CMILib.Container.CMILocation) extends Bukkit's
 * {@link Location} in modern CMILib builds. We first try a direct cast;
 * if that fails we fall back to calling {@code getLocation()} and, if that
 * is unavailable, we reconstruct a Location from component getters.
 */
public final class CMIReflection {

    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private CMIReflection() {}

    // ──────────────────────────────────────────────────────────────────────
    //  Instance access
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the {@code CMI} singleton by calling the static
     * {@code CMI#getInstance()} method on the plugin's class.
     */
    public static Object getInstance(Plugin cmiPlugin) {
        try {
            Method m = cmiPlugin.getClass().getMethod("getInstance");
            return m.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Cannot obtain CMI.getInstance() — is CMI loaded?", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Method invocation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Invokes a no-arg method on {@code obj} by name.
     * Throws {@link RuntimeException} on any error.
     */
    public static Object invoke(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = findMethod(obj.getClass(), methodName);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Reflection call failed: " + obj.getClass().getSimpleName() + "#" + methodName,
                    e);
        }
    }

    /**
     * Like {@link #invoke} but returns {@code null} on any error instead
     * of throwing (useful for optional / possibly-absent methods).
     */
    public static Object invokeQuiet(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = findMethod(obj.getClass(), methodName);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  CMILocation → Bukkit Location conversion
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Converts a {@code CMILocation} (or any Location-like object) into a
     * plain Bukkit {@link Location}.
     *
     * Resolution order:
     * <ol>
     *   <li>Direct cast — CMILocation extends Location in recent CMILib builds.</li>
     *   <li>{@code getLocation()} method — some older builds expose this.</li>
     *   <li>Component reconstruction via {@code getWorld/getX/getY/getZ/getYaw/getPitch}.</li>
     * </ol>
     *
     * Returns {@code null} when the location cannot be resolved or the world
     * referenced by the location is not currently loaded.
     */
    public static Location toLocation(Object cmiLoc) {
        if (cmiLoc == null) return null;

        // 1. Direct cast (CMILocation extends Location in most CMILib versions)
        if (cmiLoc instanceof Location) {
            return (Location) cmiLoc;
        }

        // 2. getLocation()
        try {
            Object result = invokeQuiet(cmiLoc, "getLocation");
            if (result instanceof Location) return (Location) result;
        } catch (Exception ignored) {}

        // 3. Component reconstruction
        try {
            Object worldObj = invokeQuiet(cmiLoc, "getWorld");
            if (!(worldObj instanceof World)) return null;
            World  world = (World) worldObj;
            double x     = ((Number) invoke(cmiLoc, "getX")).doubleValue();
            double y     = ((Number) invoke(cmiLoc, "getY")).doubleValue();
            double z     = ((Number) invoke(cmiLoc, "getZ")).doubleValue();
            float  yaw   = getFloatSafe(cmiLoc, "getYaw");
            float  pitch = getFloatSafe(cmiLoc, "getPitch");
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception ignored) {}

        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────────────────

    private static Method findMethod(Class<?> cls, String name) throws NoSuchMethodException {
        String key = cls.getName() + "#" + name;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        // Walk up the class hierarchy
        Class<?> c = cls;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name);
                METHOD_CACHE.put(key, m);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(cls.getName() + "#" + name);
    }

    private static float getFloatSafe(Object obj, String method) {
        Object result = invokeQuiet(obj, method);
        if (result == null) return 0f;
        return ((Number) result).floatValue();
    }
}
