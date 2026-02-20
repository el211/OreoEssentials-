package fr.elias.oreoEssentials.modules.holograms.nms;

import org.bukkit.Bukkit;

import java.util.List;

public final class NmsBridgeLoader {

    private NmsBridgeLoader() {}


    private static final List<String> FALLBACK_VERSIONS = List.of(
            "v1_21_11",
            "v1_21_10",
            "v1_21_9",
            "v1_21_8",
            "v1_21_7",
            "v1_21_6",
            "v1_21_5",
            "v1_21_4",
            "v1_21_3",
            "v1_21_2",
            "v1_21_1",
            "v1_21"
    );

    public static NmsHologramBridge loadOrThrow() {
        String ver = craftBukkitVersion();

        NmsHologramBridge exact = tryLoad(ver);
        if (exact != null) return exact;

        for (String fallback : FALLBACK_VERSIONS) {
            NmsHologramBridge bridge = tryLoad(fallback);
            if (bridge != null) {
                Bukkit.getLogger().warning(
                        "[OreoHolograms] No exact NMS bridge for " + ver
                                + " — using compatible fallback: " + fallback
                );
                return bridge;
            }
        }

        throw new IllegalStateException(
                "No NMS bridge for " + ver + " and no compatible fallback found. "
                        + "Expected class: fr.elias.oreoEssentials.modules.holograms.nms."
                        + ver + ".Bridge_" + ver
        );
    }


    private static NmsHologramBridge tryLoad(String ver) {
        String cls = "fr.elias.oreoEssentials.modules.holograms.nms." + ver + ".Bridge_" + ver;
        try {
            return (NmsHologramBridge) Class.forName(cls)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[OreoHolograms] Bridge " + cls + " failed to load: " + t);
            return null;
        }
    }

    private static String craftBukkitVersion() {
        String cb = Bukkit.getServer().getClass().getPackage().getName();
        String fromPackage = cb.substring(cb.lastIndexOf('.') + 1);
        if (!fromPackage.startsWith("v")) {
            String mcVer = Bukkit.getServer().getMinecraftVersion();
            return "v" + mcVer.replace(".", "_");
        }
        return fromPackage;
    }
}