// File: src/main/java/fr/elias/oreoEssentials/customcraft/CustomCraftingListener.java
package fr.elias.oreoEssentials.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Locale;
import java.util.Map;

public final class CustomCraftingListener implements Listener {
    private final CustomCraftingService service;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public CustomCraftingListener(CustomCraftingService service) { this.service = service; }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null || !(r instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm == null) return; // public recipe
            if (e.getView().getPlayer() instanceof Player p && !p.hasPermission(perm)) {
                e.getInventory().setResult(new ItemStack(Material.AIR)); // hide result
            }
        });
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm == null) return;
            if (e.getWhoClicked() instanceof Player p && !p.hasPermission(perm)) {
                e.setCancelled(true);
                // why: GUI strings need legacy; chat can use Component with full MM
                Component msg = MM.deserialize(applyPapi(p, Lang.get("customcraft.messages.no-permission-craft",
                        "<red>You need <yellow>%permission%</yellow> to craft this."))
                        .replace("%permission%", perm));
                p.sendMessage(msg);
            }
        });
    }

    /* ---------------- util ---------------- */

    private static String applyPapi(Player p, String raw) {
        if (raw == null) return "";
        try { if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return PlaceholderAPI.setPlaceholders(p, raw); }
        catch (Throwable ignored) {}
        return raw;
    }
}
