package fr.elias.oreoEssentials.modules.holograms.nms;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface NmsHologramBridge {

    String craftBukkitVersion();

    void sendTextDisplayText(Player player, int entityId, Component text);

    void destroyEntityClientside(Player player, int entityId);

    void teleportEntityClientside(Player player, int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround);
}
