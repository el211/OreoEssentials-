package fr.elias.oreoEssentials.modules.skin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;

public final class SkinRefresherPacketEvents implements SkinRefresher {

    @Override
    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;

        SkinDebug.log("PacketEvents refresh starting for " + player.getName());

        if (!Bukkit.isPrimaryThread()) {
            OreScheduler.run(OreoEssentials.get(), () -> refresh(player));
            return;
        }

        try {
            User peUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
            UserProfile profile = peUser.getProfile();

            WrapperPlayServerPlayerInfoRemove removePacket =
                    new WrapperPlayServerPlayerInfoRemove(List.of(player.getUniqueId()));

            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                    new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            profile,
                            true,
                            player.getPing(),
                            toPacketEventsGameMode(player.getGameMode()),
                            null,
                            null
                    );

            WrapperPlayServerPlayerInfoUpdate addPacket = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(
                            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
                    ),
                    List.of(playerInfo)
            );

            WrapperPlayServerDestroyEntities destroyPacket =
                    new WrapperPlayServerDestroyEntities(player.getEntityId());

            OreoEssentials plugin = OreoEssentials.get();
            List<Player> viewers = List.copyOf(Bukkit.getOnlinePlayers());

            // Step 1: remove player from tab info for all viewers
            for (Player viewer : viewers) {
                try {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
                } catch (Exception ignored) {}
            }

            OreScheduler.runLater(plugin, () -> {
                // Step 2: add player back with updated skin profile
                for (Player viewer : viewers) {
                    if (!viewer.isOnline()) continue;
                    try {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, addPacket);
                    } catch (Exception ignored) {}
                }

                OreScheduler.runLater(plugin, () -> {
                    // Step 3: destroy entity + hide for other viewers
                    for (Player viewer : viewers) {
                        if (!viewer.isOnline() || viewer.equals(player)) continue;
                        try {
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                            viewer.hidePlayer(plugin, player);
                        } catch (Exception ignored) {}
                    }

                    OreScheduler.runLater(plugin, () -> {
                        // Step 4: show player again
                        for (Player viewer : viewers) {
                            if (!viewer.isOnline() || viewer.equals(player)) continue;
                            try {
                                viewer.showPlayer(plugin, player);
                            } catch (Exception ignored) {}
                        }
                        player.updateInventory();
                        SkinDebug.log("PacketEvents refresh complete for " + player.getName());
                    }, 2L);
                }, 2L);
            }, 2L);

        } catch (Exception e) {
            SkinDebug.log("PacketEvents refresh critical error: " + e.getMessage());
        }
    }

    private static GameMode toPacketEventsGameMode(org.bukkit.GameMode bukkit) {
        switch (bukkit) {
            case CREATIVE:  return GameMode.CREATIVE;
            case ADVENTURE: return GameMode.ADVENTURE;
            case SPECTATOR: return GameMode.SPECTATOR;
            default:        return GameMode.SURVIVAL;
        }
    }
}
