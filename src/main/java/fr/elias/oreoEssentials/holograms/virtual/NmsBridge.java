package fr.elias.oreoEssentials.holograms.virtual;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface NmsBridge {

    void spawnTextDisplay(Player player, int entityId, Location loc);

    void updateText(Player player, int entityId, Component text);

    void teleport(Player player, int entityId, Location loc);

    void destroy(Player player, int entityId);
}
