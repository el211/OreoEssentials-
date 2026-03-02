package fr.elias.oreoEssentials.modules.shop.hooks;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ItemsAdderHook {

    private final boolean enabled;

    public ItemsAdderHook(Plugin plugin) {
        boolean found = false;
        try {
            if (plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null) {
                Class.forName("dev.lone.itemsadder.api.CustomStack");
                found = true;
                plugin.getLogger().info("[Shop] ItemsAdder hook enabled.");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[Shop] ItemsAdder found but API class missing — hook disabled.");
        }
        this.enabled = found;
    }

    public boolean isEnabled() { return enabled; }

    public ItemStack buildItem(String namespacedId) {
        if (!enabled || namespacedId == null) return null;
        try {
            dev.lone.itemsadder.api.CustomStack stack =
                    dev.lone.itemsadder.api.CustomStack.getInstance(namespacedId);
            if (stack == null) return null;
            return stack.getItemStack().clone();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean matches(ItemStack item, String namespacedId) {
        if (!enabled || item == null || namespacedId == null) return false;
        try {
            dev.lone.itemsadder.api.CustomStack stack =
                    dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            if (stack == null) return false;
            return namespacedId.equalsIgnoreCase(stack.getNamespacedID());
        } catch (Exception e) {
            return false;
        }
    }

    public String getNamespacedId(ItemStack item) {
        if (!enabled || item == null) return null;
        try {
            dev.lone.itemsadder.api.CustomStack stack =
                    dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            return stack == null ? null : stack.getNamespacedID();
        } catch (Exception e) {
            return null;
        }
    }
}