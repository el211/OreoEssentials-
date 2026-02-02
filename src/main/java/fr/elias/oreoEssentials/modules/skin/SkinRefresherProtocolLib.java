package fr.elias.oreoEssentials.modules.skin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Refreshes player skins using ProtocolLib packets.
 * Tested on Minecraft 1.21.8 with Paper.
 */
public final class SkinRefresherProtocolLib implements SkinRefresher {

    @Override
    public void refresh(Player player) {
        if (player == null || !player.isOnline()) {
            SkinDebug.log("refresh: player null or offline");
            return;
        }

        SkinDebug.log("ProtocolLib refresh starting for " + player.getName());

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> refresh(player));
            return;
        }

        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();

            WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);
            SkinDebug.log("Profile textures: " + profile.getProperties());

            PacketContainer removeInfo = pm.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            removeInfo.getModifier().write(0, List.of(player.getUniqueId()));

            PacketContainer addInfo = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
            addInfo.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));

            PlayerInfoData infoData = new PlayerInfoData(
                    profile.getUUID(),
                    player.getPing(),
                    true,
                    EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()),
                    profile,
                    WrappedChatComponent.fromText(player.getDisplayName())
            );
            addInfo.getPlayerInfoDataLists().write(1, List.of(infoData));

            PacketContainer destroyEntity = pm.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyEntity.getIntLists().write(0, List.of(player.getEntityId()));

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                try {
                    SkinDebug.log("Sending packets to " + viewer.getName());

                    pm.sendServerPacket(viewer, removeInfo);

                    Bukkit.getScheduler().runTaskLater(OreoEssentials.get(), () -> {
                        try {
                            pm.sendServerPacket(viewer, addInfo);
                        } catch (Exception e) {
                            SkinDebug.log("Error sending add info to " + viewer.getName());
                        }
                    }, 2L);

                    if (!viewer.equals(player)) {
                        Bukkit.getScheduler().runTaskLater(OreoEssentials.get(), () -> {
                            try {
                                pm.sendServerPacket(viewer, destroyEntity);

                                viewer.hidePlayer(OreoEssentials.get(), player);
                                Bukkit.getScheduler().runTaskLater(OreoEssentials.get(), () -> {
                                    viewer.showPlayer(OreoEssentials.get(), player);
                                }, 2L);
                            } catch (Exception e) {
                                SkinDebug.log("Error destroying entity for " + viewer.getName());
                            }
                        }, 4L);
                    }

                } catch (Exception e) {
                    SkinDebug.log("Error processing viewer " + viewer.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            Bukkit.getScheduler().runTaskLater(OreoEssentials.get(), () -> {
                player.updateInventory();
                SkinDebug.log("ProtocolLib refresh complete for " + player.getName());
            }, 8L);

        } catch (Exception e) {
            SkinDebug.log("ProtocolLib refresh critical error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}