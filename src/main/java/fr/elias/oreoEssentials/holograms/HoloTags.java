package fr.elias.oreoEssentials.holograms;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class HoloTags {
    public final NamespacedKey TAG_IS_OREO;
    public final NamespacedKey TAG_NAME;    // hologram name (lowercased)
    public final NamespacedKey TAG_TYPE;    // TEXT / ITEM / BLOCK

    public HoloTags(Plugin plugin) {
        TAG_IS_OREO = new NamespacedKey(plugin, "oreo_hologram");
        TAG_NAME    = new NamespacedKey(plugin, "oreo_name");
        TAG_TYPE    = new NamespacedKey(plugin, "oreo_type");
    }
}
