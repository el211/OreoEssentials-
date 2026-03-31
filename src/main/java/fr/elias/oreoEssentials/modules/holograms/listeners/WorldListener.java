package fr.elias.oreoEssentials.modules.holograms.listeners;

import fr.elias.oreoEssentials.modules.holograms.OHolograms;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldListener implements Listener {

    private final boolean hologramLoadLogging = OHolograms.get().getHologramConfiguration().isHologramLoadLogging();

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        OHolograms.get().getHologramThread().submit(() -> {
            if (hologramLoadLogging) OHolograms.get().getFancyLogger().info("Loading holograms for world " + event.getWorld().getName());
            OHolograms.get().getHologramsManager().loadHolograms(event.getWorld().getName());
        });
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        OHolograms.get().getHologramThread().submit(() -> {
            if (hologramLoadLogging) OHolograms.get().getFancyLogger().info("Unloading holograms for world " + event.getWorld().getName());
            OHolograms.get().getHologramsManager().unloadHolograms(event.getWorld().getName());
        });
    }

}
