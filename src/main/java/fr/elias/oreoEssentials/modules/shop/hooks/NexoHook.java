package fr.elias.oreoEssentials.modules.shop.hooks;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class NexoHook {

    private final boolean enabled;

    public NexoHook(Plugin plugin) {
        boolean found = false;
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Nexo") != null) {
                Class.forName("com.nexomc.nexo.api.NexoItems");
                found = true;
                plugin.getLogger().info("[Shop] Nexo hook enabled.");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[Shop] Nexo found but API class missing — hook disabled.");
        }
        this.enabled = found;
    }

    public boolean isEnabled() { return enabled; }

    public ItemStack buildItem(String itemId) {
        if (!enabled || itemId == null) return null;
        try {
            com.nexomc.nexo.items.ItemBuilder builder =
                    com.nexomc.nexo.api.NexoItems.itemFromId(itemId);
            if (builder == null) return null;
            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean matches(ItemStack item, String itemId) {
        if (!enabled || item == null || itemId == null) return false;
        try {
            String id = com.nexomc.nexo.api.NexoItems.idFromItem(item);
            return itemId.equalsIgnoreCase(id);
        } catch (Exception e) {
            return false;
        }
    }

    public String getItemId(ItemStack item) {
        if (!enabled || item == null) return null;
        try {
            return com.nexomc.nexo.api.NexoItems.idFromItem(item);
        } catch (Exception e) {
            return null;
        }
    }
}