package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.shop.hooks.ItemsAdderHook;
import fr.elias.oreoEssentials.modules.shop.hooks.NexoHook;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * SmartInvs GUI shown by /registerreward.
 *
 * Layout (default 3 rows):
 *   [G][G][G][G][G][G][G][G][G]
 *   [G][G][G][G][L][G][G][G][G]   ← logo at slot 13
 *   [G][G][G][G][B][G][G][G][G]   ← claim/status button at slot 22
 */
public final class RegisterRewardMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final RegisterRewardConfig cfg;
    private final RegisterRewardService svc;
    private final ItemsAdderHook iaHook;
    private final NexoHook nexoHook;
    /** Pre-fetched: whether this player's UUID is linked on the website. */
    private final boolean registered;

    public RegisterRewardMenu(OreoEssentials plugin,
                               RegisterRewardConfig cfg,
                               RegisterRewardService svc,
                               ItemsAdderHook iaHook,
                               NexoHook nexoHook,
                               boolean registered) {
        this.plugin     = plugin;
        this.cfg        = cfg;
        this.svc        = svc;
        this.iaHook     = iaHook;
        this.nexoHook   = nexoHook;
        this.registered = registered;
    }

    public SmartInventory inventory(Player p) {
        return SmartInventory.builder()
                .manager(plugin.getInvManager())
                .id("register_reward:" + p.getUniqueId())
                .provider(this)
                .size(cfg.guiRows(), 9)
                .title(cfg.guiTitle())
                .closeable(true)
                .build();
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        // Background filler
        ItemStack filler = makeSimple(cfg.fillMaterial(), " ", List.of());
        ClickableItem bg = ClickableItem.empty(filler);
        for (int r = 0; r < cfg.guiRows(); r++) {
            for (int c = 0; c < 9; c++) {
                contents.set(r, c, bg);
            }
        }

        // Logo
        int logoSlot = cfg.logoSlot();
        contents.set(logoSlot / 9, logoSlot % 9, ClickableItem.empty(buildLogo()));

        // Status / claim button
        int btnSlot = cfg.claimSlot();
        boolean alreadyClaimed = svc.hasClaimed(p.getUniqueId());

        if (alreadyClaimed) {
            contents.set(btnSlot / 9, btnSlot % 9,
                    ClickableItem.empty(makeSimple(
                            cfg.alreadyClaimedMaterial(),
                            cfg.alreadyClaimedName(),
                            cfg.alreadyClaimedLore())));
        } else if (!registered) {
            contents.set(btnSlot / 9, btnSlot % 9,
                    ClickableItem.empty(makeSimple(
                            cfg.notRegisteredMaterial(),
                            cfg.notRegisteredName(),
                            cfg.notRegisteredLore())));
        } else {
            contents.set(btnSlot / 9, btnSlot % 9,
                    ClickableItem.of(makeSimple(
                            cfg.claimMaterial(),
                            cfg.claimName(),
                            cfg.claimLore()), e -> {
                        p.closeInventory();
                        svc.giveReward(p);
                    }));
        }
    }

    @Override
    public void update(Player p, InventoryContents contents) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack buildLogo() {
        ItemStack item = null;

        String iaId = cfg.logoItemsAdder();
        if (iaId != null && iaHook != null && iaHook.isEnabled()) {
            item = iaHook.buildItem(iaId);
        }

        if (item == null) {
            String nexoId = cfg.logoNexo();
            if (nexoId != null && nexoHook != null && nexoHook.isEnabled()) {
                item = nexoHook.buildItem(nexoId);
            }
        }

        if (item == null) {
            Material mat;
            try {
                mat = Material.valueOf(cfg.logoMaterial().toUpperCase());
            } catch (Exception e) {
                mat = Material.NETHER_STAR;
            }
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cfg.logoName());
            meta.setLore(cfg.logoLore());
            int cmd = cfg.logoCustomModelData();
            if (cmd > 0) {
                try { meta.setCustomModelData(cmd); } catch (Throwable ignored) {}
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack makeSimple(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
