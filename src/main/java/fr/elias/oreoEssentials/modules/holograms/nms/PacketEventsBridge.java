package fr.elias.oreoEssentials.modules.holograms.nms;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * NmsHologramBridge implementation backed by PacketEvents.
 * Replaces the raw NMS reflection bridge — no per-version class needed.
 */
public final class PacketEventsBridge implements NmsHologramBridge {

    // Minecraft 1.20+ metadata index for TextDisplay "text" field.
    // Entity (0-7) + Display (8-22) = TextDisplay starts at 23.
    private static final int TEXT_DISPLAY_TEXT_INDEX = 23;

    @Override
    public String craftBukkitVersion() {
        return "packetevents";
    }

    @Override
    public void sendTextDisplayText(Player player, int entityId, Component text) {
        try {
            List<EntityData<?>> data = List.of(
                    new EntityData<>(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, text)
            );
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(entityId, data);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void destroyEntityClientside(Player player, int entityId) {
        try {
            WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(entityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void teleportEntityClientside(Player player, int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        try {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(
                    entityId,
                    new Vector3d(x, y, z),
                    yaw, pitch,
                    onGround
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception ignored) {
        }
    }
}
