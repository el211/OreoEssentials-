package fr.elias.oreoEssentials.modules.holograms.listeners;

import fr.elias.oreoEssentials.modules.holograms.FHFeatureFlags;
import fr.elias.oreoEssentials.modules.holograms.api.events.HologramShowEvent;
import fr.elias.oreoEssentials.modules.holograms.util.PluginUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.geysermc.floodgate.api.FloodgateApi;

public class BedrockPlayerListener implements Listener {

    @EventHandler
    public void onHologramShow(final HologramShowEvent event) {
        if (FHFeatureFlags.DISABLE_HOLOGRAMS_FOR_BEDROCK_PLAYERS.isEnabled() && PluginUtils.isFloodgateEnabled()) {
            boolean isBedrockPlayer = FloodgateApi.getInstance().isFloodgatePlayer(event.getPlayer().getUniqueId());
            if (isBedrockPlayer) {
                event.setCancelled(true);
            }
        }
    }

}
