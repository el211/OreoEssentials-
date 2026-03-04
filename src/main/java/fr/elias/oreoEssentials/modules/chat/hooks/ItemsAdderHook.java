package fr.elias.oreoEssentials.modules.chat.hooks;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;


public final class ItemsAdderHook {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ItemsAdderHook() {}


    public static Component getItemName(ItemStack item) {
        try {
            CustomStack stack = CustomStack.byItemStack(item);
            if (stack == null) return null;

            String displayName = stack.getDisplayName();
            if (displayName == null || displayName.isEmpty()) return null;

            return LEGACY.deserialize(displayName);
        } catch (Throwable t) {
            return null;
        }
    }


    public static String getItemId(ItemStack item) {
        try {
            CustomStack stack = CustomStack.byItemStack(item);
            if (stack == null) return null;

            return stack.getNamespacedID();
        } catch (Throwable t) {
            return null;
        }
    }


    public static boolean isCustomItem(ItemStack item) {
        try {
            return CustomStack.byItemStack(item) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}