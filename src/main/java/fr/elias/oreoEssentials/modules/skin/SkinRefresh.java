package fr.elias.oreoEssentials.modules.skin;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SkinRefresh {
    private static final SkinRefresher IMPL = pick();

    private SkinRefresh() {}

    private static SkinRefresher pick() {
        boolean hasPacketEvents = Bukkit.getPluginManager().getPlugin("packetevents") != null;

        if (hasPacketEvents) {
            SkinDebug.log("Picker: Using PacketEvents");
            try {
                return new SkinRefresherPacketEvents();
            } catch (Throwable t) {
                SkinDebug.log("Picker: PacketEvents present but failed to load: " + t.getMessage());
            }
        }

        SkinDebug.log("Picker: Using fallback (hide/show) - install PacketEvents for instant updates");
        return new SkinRefresherFallback();
    }

    public static void refresh(Player player) {
        SkinDebug.p(player, "Refreshing view for new skin…");
        IMPL.refresh(player);
    }
}