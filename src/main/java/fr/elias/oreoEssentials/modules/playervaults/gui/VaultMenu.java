// File: src/main/java/fr/elias/oreoEssentials/playervaults/gui/VaultMenu.java
package fr.elias.oreoEssentials.modules.playervaults.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsConfig;
import fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsService;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * Vault selection menu listing all vault IDs.
 *
 * ✅ VERIFIED - Uses Lang.send() for chat messages + § for GUI ItemStacks
 *
 * Features:
 * - Locked vaults show barrier with custom text
 * - Unlocked vaults show chest
 * - Click to open vault (if permitted)
 * - Deny message with sound for locked vaults
 *
 * Chat message uses Lang.send():
 * - playervaults.deny
 */
public final class VaultMenu {

    public static SmartInventory build(OreoEssentials plugin,
                                       PlayerVaultsService svc,
                                       PlayerVaultsConfig cfg,
                                       List<Integer> vaultIds) {
        int rows = Math.min(Math.max(1, (int) Math.ceil(vaultIds.size() / 9.0)), 6);
        return SmartInventory.builder()
                .manager(plugin.getInvManager())
                .id("oe_vault_menu")
                .provider(new Provider(svc, cfg, vaultIds))
                .size(rows, 9)
                .title(ChatColor.translateAlternateColorCodes('&', cfg.menuTitle()))
                .build();
    }

    private static final class Provider implements InventoryProvider {
        private final PlayerVaultsService svc;
        private final PlayerVaultsConfig cfg;
        private final List<Integer> ids;

        Provider(PlayerVaultsService svc, PlayerVaultsConfig cfg, List<Integer> ids) {
            this.svc = svc;
            this.cfg = cfg;
            this.ids = ids;
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            for (int i = 0; i < ids.size(); i++) {
                int id = ids.get(i);
                boolean unlocked = svc.canAccess(player, id);

                ItemStack icon = new ItemStack(unlocked ? Material.CHEST : Material.BARRIER);
                ItemMeta meta = icon.getItemMeta();

                // ✅ GUI ItemStack display name (visual styling - § is correct)
                String name = (unlocked ? cfg.menuItemUnlockedName() : cfg.menuItemLockedName())
                        .replace("<id>", String.valueOf(id));
                String lore = (unlocked ? cfg.menuItemUnlockedLore() : cfg.menuItemLockedLore())
                        .replace("<id>", String.valueOf(id));

                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
                icon.setItemMeta(meta);

                contents.set(i / 9, i % 9, ClickableItem.of(icon, e -> {
                    if (!svc.hasUsePermission(player, id)) {
                        // ✅ Chat message uses Lang.send()
                        Lang.send(player, "playervaults.deny",
                                "<red>You don't have permission to access vault <yellow>%id%</yellow>.</red>",
                                Map.of("id", String.valueOf(id)));
                        player.playSound(player.getLocation(), cfg.denySound(), 1f, 0.7f);
                        return;
                    }
                    svc.openVault(player, id);
                }));
            }
        }

        @Override
        public void update(Player player, InventoryContents contents) {}
    }
}