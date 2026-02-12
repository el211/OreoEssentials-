package fr.elias.oreoEssentials.modules.skin;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SkinRefresh {
    private static final SkinRefresher IMPL = pick();

    private SkinRefresh() {}

    private static SkinRefresher pick() {
        boolean hasProtocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;

        if (hasProtocolLib) {
            SkinDebug.log("Picker: Using ProtocolLib");
            try {
                return new SkinRefresherProtocolLib();
            } catch (Throwable t) {
                SkinDebug.log("Picker: ProtocolLib present but failed to load: " + t.getMessage());
            }
        }

        SkinDebug.log("Picker: Using fallback (hide/show) - install ProtocolLib for instant updates");
        return new SkinRefresherFallback();
    }

    public static void refresh(Player player) {
        SkinDebug.p(player, "Refreshing view for new skin…");
        IMPL.refresh(player);
    }
}