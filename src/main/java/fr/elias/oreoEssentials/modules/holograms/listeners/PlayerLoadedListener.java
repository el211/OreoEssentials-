package fr.elias.oreoEssentials.modules.holograms.listeners;

import fr.elias.oreoEssentials.modules.holograms.OHolograms;
import fr.elias.oreoEssentials.modules.holograms.api.hologram.Hologram;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerLoadedListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLoaded(@NotNull final PlayerClientLoadedWorldEvent event) {
        OHolograms.get().getHologramThread().submit(() -> {
            for (final Hologram hologram : OHolograms.get().getHologramsManager().getHolograms()) {
                hologram.forceUpdateShownStateFor(event.getPlayer());
            }
        });
    }

}
