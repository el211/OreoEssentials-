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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinRefresherPacketEvents implements SkinRefresher {

    /** DC13: prevents concurrent skin refresh for the same player. */
    private static final Set<UUID> refreshing = ConcurrentHashMap.newKeySet();

    @Override
    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;

        // DC13: skip if a refresh is already in-flight for this player
        if (!refreshing.add(player.getUniqueId())) {
            SkinDebug.log("Refresh already in progress for " + player.getName() + ", skipping.");
            return;
        }

        SkinDebug.log("PacketEvents refresh starting for " + player.getName());

        if (!Bukkit.isPrimaryThread()) {
            // re-schedule on main thread; refreshing set was already claimed above
            OreScheduler.run(OreoEssentials.get(), () -> {
                // DC13: delegate back into refresh() which will skip the add() — go direct
                refreshing.remove(player.getUniqueId());
                refresh(player);
            });
            return;
        }

        try {
            // DC4: null guard on peUser
            User peUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (peUser == null) {
                SkinDebug.log("peUser is null for " + player.getName());
                refreshing.remove(player.getUniqueId()); // DC13: release lock
                return;
            }
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

            // Step 1: remove player from tab info for all viewers (snapshot at this moment only)
            List<Player> step1Viewers = List.copyOf(Bukkit.getOnlinePlayers());
            for (Player viewer : step1Viewers) {
                try {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
                } catch (Exception ignored) {}
            }

            OreScheduler.runLater(plugin, () -> {
                // DC3: re-filter to only players still online at step 2
                List<Player> step2Viewers = Bukkit.getOnlinePlayers().stream()
                        .filter(Player::isOnline)
                        .map(v -> (Player) v)
                        .toList();

                // Step 2: add player back with updated skin profile
                for (Player viewer : step2Viewers) {
                    try {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, addPacket);
                    } catch (Exception ignored) {}
                }

                OreScheduler.runLater(plugin, () -> {
                    // DC3: re-filter to only players still online at step 3
                    List<Player> step3Viewers = Bukkit.getOnlinePlayers().stream()
                            .filter(v -> v.isOnline() && !v.equals(player))
                            .map(v -> (Player) v)
                            .toList();

                    // Step 3: destroy entity + hide for other viewers
                    for (Player viewer : step3Viewers) {
                        try {
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                            viewer.hidePlayer(plugin, player);
                        } catch (Exception ignored) {}
                    }

                    OreScheduler.runLater(plugin, () -> {
                        // DC3: re-filter to only players still online at step 4
                        List<Player> step4Viewers = Bukkit.getOnlinePlayers().stream()
                                .filter(v -> v.isOnline() && !v.equals(player))
                                .map(v -> (Player) v)
                                .toList();

                        // Step 4: show player again
                        for (Player viewer : step4Viewers) {
                            try {
                                viewer.showPlayer(plugin, player);
                            } catch (Exception ignored) {}
                        }
                        player.updateInventory();
                        SkinDebug.log("PacketEvents refresh complete for " + player.getName());
                        refreshing.remove(player.getUniqueId()); // DC13: release lock
                    }, 2L);
                }, 2L);
            }, 2L);

        } catch (Exception e) {
            SkinDebug.log("PacketEvents refresh critical error: " + e.getMessage());
            refreshing.remove(player.getUniqueId()); // DC13: release lock on error path
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
