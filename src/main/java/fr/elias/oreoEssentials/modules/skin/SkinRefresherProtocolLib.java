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
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<Enum> actClass = (Class<Enum>) Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Actions"
                );
                @SuppressWarnings({"unchecked", "rawtypes"})
                EnumSet<?> nmsActions = EnumSet.of(
                        Enum.valueOf(actClass, "ADD_PLAYER"),
                        Enum.valueOf(actClass, "UPDATE_LISTED")
                );
                addInfo.getModifier().write(0, nmsActions);
            } catch (Exception e) {
                SkinDebug.log("NMS actions fallback: " + e.getMessage());
                addInfo.getPlayerInfoActions().write(0,
                        java.util.EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                                EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
            }

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

            OreoEssentials plugin = OreoEssentials.get();
            List<Player> viewers = List.copyOf(Bukkit.getOnlinePlayers());

            for (Player viewer : viewers) {
                try {
                    pm.sendServerPacket(viewer, removeInfo);
                } catch (Exception e) {
                    SkinDebug.log("Error sending remove to " + viewer.getName());
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player viewer : viewers) {
                    if (!viewer.isOnline()) continue;
                    try {
                        pm.sendServerPacket(viewer, addInfo);
                    } catch (Exception e) {
                        SkinDebug.log("Error sending add to " + viewer.getName());
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player viewer : viewers) {
                        if (!viewer.isOnline() || viewer.equals(player)) continue;
                        try {
                            pm.sendServerPacket(viewer, destroyEntity);
                            viewer.hidePlayer(plugin, player);
                        } catch (Exception e) {
                            SkinDebug.log("Error hiding for " + viewer.getName());
                        }
                    }

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Player viewer : viewers) {
                            if (!viewer.isOnline() || viewer.equals(player)) continue;
                            try {
                                viewer.showPlayer(plugin, player);
                            } catch (Exception e) {
                                SkinDebug.log("Error showing for " + viewer.getName());
                            }
                        }
                        player.updateInventory();
                        SkinDebug.log("ProtocolLib refresh complete for " + player.getName());
                    }, 2L);
                }, 2L);
            }, 2L);

        } catch (Exception e) {
            SkinDebug.log("ProtocolLib refresh critical error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
