package fr.elias.oreoEssentials.holograms.nms;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface NmsHologramBridge {

    /** craftbukkit package like "v1_21_R1" / "v1_22_R1" */
    String craftBukkitVersion();

    /** Send a per-player text override packet for an existing TextDisplay entity id. */
    void sendTextDisplayText(Player player, int entityId, Component text);

    /** Optional helper: destroy entity clientside (rarely needed if you keep a real entity). */
    void destroyEntityClientside(Player player, int entityId);

    /** Optional helper: teleport entity clientside. */
    void teleportEntityClientside(Player player, int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround);

    /** Convert Bukkit entity UUID to internal id is handled elsewhere; this is just an NMS bridge. */
}
