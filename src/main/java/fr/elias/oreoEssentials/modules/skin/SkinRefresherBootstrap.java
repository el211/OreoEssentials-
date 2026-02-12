package fr.elias.oreoEssentials.modules.skin;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;

public final class SkinRefresherBootstrap {
    private SkinRefresherBootstrap() {}

    public static void init(OreoEssentials plugin) {
        boolean hasPE = Bukkit.getPluginManager().getPlugin("PacketEvents") != null
                || Bukkit.getPluginManager().getPlugin("packetevents") != null;
        if (hasPE) {
            try {
                SkinRefresher.Holder.set(new SkinRefresherPE(plugin));
                plugin.getLogger().info("[Skins] Using PacketEvents for live refresh.");
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[Skins] PacketEvents present but PE path failed: " + t.getMessage());
            }
        }
        SkinRefresher.Holder.set(new SkinRefresherFallback());
        plugin.getLogger().info("[Skins] Using Bukkit hide/show fallback.");
    }
}
