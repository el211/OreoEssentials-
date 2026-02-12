package fr.elias.oreoEssentials.modules.customcraft;

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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class CustomCraftingListener implements Listener {
    private final CustomCraftingService service;
    private final CraftActionsConfig actionsConfig;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public CustomCraftingListener(CustomCraftingService service) {
        this(service, null);
    }

    public CustomCraftingListener(CustomCraftingService service, CraftActionsConfig actionsConfig) {
        this.service = service;
        this.actionsConfig = actionsConfig;
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null || !(r instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm == null) return;
            if (e.getView().getPlayer() instanceof Player p && !p.hasPermission(perm)) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof Keyed keyed)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm != null && !p.hasPermission(perm)) {
                e.setCancelled(true);
                Component msg = MM.deserialize(applyPapi(p, Lang.get("customcraft.messages.no-permission-craft",
                        "<red>You need <yellow>%permission%</yellow> to craft this."))
                        .replace("%permission%", perm));
                p.sendMessage(msg);
                return;
            }

            if (actionsConfig != null) {
                executeCraftActions(p, name, e.getRecipe().getResult());
            }
        });

        if (actionsConfig != null) {
            ItemStack result = e.getRecipe().getResult();
            if (result != null && !result.getType().isAir()) {
                CraftActionsConfig.CraftAction action = actionsConfig.getAction(result.getType());
                if (action != null) {
                    executeAction(p, action, result);
                }
            }
        }
    }

    private void executeCraftActions(Player player, String recipeName, ItemStack result) {
        CraftActionsConfig.CraftAction action = actionsConfig.getActionForRecipe(recipeName);
        if (action != null) {
            executeAction(player, action, result);
        }
    }
    private void executeAction(Player player, CraftActionsConfig.CraftAction action, ItemStack result) {
        // Execute commands
        if (action.hasCommands()) {
            for (String cmd : action.getCommands()) {
                String processed = processPlaceholders(player, cmd, result);
                Bukkit.getScheduler().runTask(service.getPlugin(), () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
                    } catch (Exception ex) {
                        service.getPlugin().getLogger().warning(
                                "[CraftActions] Failed to execute command: " + processed + " - " + ex.getMessage()
                        );
                    }
                });
            }
        }
        if (action.hasMessage()) {
            String msg = processPlaceholders(player, action.getMessage(), result);
            Component component = MM.deserialize(msg);
            player.sendMessage(component);
        }
    }

    private String processPlaceholders(Player player, String text, ItemStack result) {
        String processed = text
                .replace("%player%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName())
                .replace("%item%", result.getType().name())
                .replace("%amount%", String.valueOf(result.getAmount()));

        processed = applyPapi(player, processed);

        return processed;
    }

    private static String applyPapi(Player p, String raw) {
        if (raw == null) return "";
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return PlaceholderAPI.setPlaceholders(p, raw);
            }
        } catch (Throwable ignored) {}
        return raw;
    }
}