package fr.elias.oreoEssentials.modules.chat.hooks;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


public final class NexoHook {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private NexoHook() {}

    public static Component getItemName(ItemStack item) {
        try {
            String id = NexoItems.idFromItem(item);
            if (id == null) return null;

            ItemBuilder builder = NexoItems.itemFromId(id);
            if (builder == null) return null;

            ItemStack built = builder.build();
            if (built != null) {
                ItemMeta meta = built.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    try {
                        Component comp = meta.displayName();
                        if (comp != null) return comp;
                    } catch (Throwable ignored) {}
                    return LEGACY.deserialize(meta.getDisplayName());
                }
            }

            return Component.text(friendlyId(id));
        } catch (Throwable t) {
            return null;
        }
    }

    public static String getItemId(ItemStack item) {
        try {
            return NexoItems.idFromItem(item);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isCustomItem(ItemStack item) {
        try {
            return NexoItems.idFromItem(item) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String friendlyId(String id) {
        if (id == null) return "Unknown";
        String local = id.contains(":") ? id.substring(id.lastIndexOf(':') + 1) : id;
        String[] words = local.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}