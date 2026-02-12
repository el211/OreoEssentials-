package fr.elias.oreoEssentials.modules.skin;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class SkinRefresherFallback implements SkinRefresher {
    @Override
    public void refresh(Player player) {
        SkinDebug.log("SkinRefresherFallback.refresh called");
        if (player == null || !player.isOnline()) return;
        var plugin = OreoEssentials.get();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) continue;
            viewer.hidePlayer(plugin, player);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(player)) continue;
                viewer.showPlayer(plugin, player);
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 2L);
        SkinDebug.log("Fallback hide/show done");
    }
}
