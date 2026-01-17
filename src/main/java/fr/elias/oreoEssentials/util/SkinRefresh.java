package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SkinRefresh {
    private static final SkinRefresher IMPL = pick();

    private SkinRefresh() {}

    private static SkinRefresher pick() {
        boolean hasPE = Bukkit.getPluginManager().getPlugin("PacketEvents") != null
                || Bukkit.getPluginManager().getPlugin("packetevents") != null;
        SkinDebug.log("Picker: PacketEvents present = " + hasPE);
        return hasPE ? new SkinRefresherPE(OreoEssentials.get())
                : new SkinRefresherFallback();
    }

    public static void refresh(Player player) {
        SkinDebug.p(player, "Refreshing view for new skin/name…");
        IMPL.refresh(player);
    }
}
