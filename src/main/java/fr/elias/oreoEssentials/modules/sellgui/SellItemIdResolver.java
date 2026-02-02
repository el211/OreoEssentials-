package fr.elias.oreoEssentials.modules.sellgui;

import com.nexomc.nexo.api.NexoItems;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public final class SellItemIdResolver {

    private SellItemIdResolver() {}

    public static String resolveKey(ItemStack item) {
        if (item == null) return null;

        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                CustomStack cs = CustomStack.byItemStack(item);
                if (cs != null) {
                    String id = cs.getNamespacedID(); // namespace:item
                    if (id != null && !id.isBlank()) {
                        return ("IA:" + id).toUpperCase();
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            try {
                String id = NexoItems.idFromItem(item);
                if (id != null && !id.isBlank()) {
                    return ("NEXO:" + id).toUpperCase();
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }
}
