package fr.elias.oreoEssentials.holograms.nms;

import org.bukkit.Bukkit;

public final class NmsBridgeLoader {

    private NmsBridgeLoader() {}

    public static NmsHologramBridge loadOrThrow() {
        String cb = Bukkit.getServer().getClass().getPackage().getName();
        String ver = cb.substring(cb.lastIndexOf('.') + 1); // v1_21_R1 etc.

        String impl = "fr.elias.oreoEssentials.holograms.nms." + ver + ".Bridge_" + ver;
        try {
            Class<?> c = Class.forName(impl);
            return (NmsHologramBridge) c.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "No NMS bridge for " + ver + ". Expected class: " + impl, t
            );
        }
    }
}
