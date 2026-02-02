package fr.elias.oreoEssentials.modules.playtime;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class PrewardsMenu implements InventoryProvider {

    private final PlaytimeRewardsService svc;

    public PrewardsMenu(PlaytimeRewardsService svc) {
        this.svc = svc;
    }

    public SmartInventory inventory(Player p) {
        final boolean featureOn = svc.isEnabled();
        String title = svc.skin.title;
        if (!featureOn) title = "&8" + title; // gray out when disabled

        return SmartInventory.builder()
                .manager(svc.getPlugin().getInvManager())
                .id("prewards:" + p.getUniqueId())
                .provider(this)
                .title(svc.color(title))
                .size(Math.max(1, svc.skin.rows), 9)
                .build();
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        final boolean featureOn = svc.isEnabled();

        if (svc.skin.fillEmpty) {
            ItemStack fill = new ItemStack(svc.skin.fillerMat);
            ItemMeta im = fill.getItemMeta();
            if (im != null) {
                im.setDisplayName(svc.color(svc.skin.fillerName));
                if (svc.skin.fillerCmd > 0) im.setCustomModelData(svc.skin.fillerCmd);
                im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
                fill.setItemMeta(im);
            }
            contents.fill(ClickableItem.empty(fill));
        }

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);

        svc.rewards.values().forEach(r -> {
            if (!svc.hasPermission(p, r)) return;

            PlaytimeRewardsService.State state = svc.stateOf(p, r);

            Material baseMat = (r.iconMaterial != null ? r.iconMaterial : Material.PAPER);
            String baseName  = (r.iconName != null ? r.iconName : r.displayName);

            List<String> lore = new ArrayList<>();
            if (r.iconLore != null) {
                for (String line : r.iconLore) lore.add(svc.color(line));
            }

            String displayName = baseName;
            if (!featureOn) {
                displayName = "&8" + displayName;
                lore.clear();
                lore.add(svc.color("&7Status: &cDISABLED"));
            }

            ItemStack item = new ItemStack(baseMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (r.customModelData != null) meta.setCustomModelData(r.customModelData);
                meta.setDisplayName(svc.color(displayName));
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

                PlaytimeRewardsService.SkinState skinState = svc.skin.states.get(state.name());
                if (skinState != null && featureOn) { // only decorate states when enabled
                    if (skinState.mat != null) item.setType(skinState.mat);
                    if (skinState.name != null && !skinState.name.isEmpty()) {
                        meta.setDisplayName(svc.color(skinState.name));
                    }
                    if (skinState.glow) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    }
                }

                if (featureOn) {
                    lore.add(svc.color("&7State: &f" + state.name()));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            ClickableItem clickable = ClickableItem.of(item, e -> {
                if (!svc.isEnabled()) {
                    p.sendMessage(svc.color("&cPlaytime Rewards is disabled."));
                    return;
                }
                PlaytimeRewardsService.State now = svc.stateOf(p, r);
                if (now == PlaytimeRewardsService.State.READY) {
                    boolean ok = svc.claim(p, r.id, true);
                    if (ok) inventory(p).open(p); else p.sendMessage(svc.color("&cNot ready or invalid reward."));
                } else {
                    p.sendMessage(svc.color("&cNot ready. Keep playing!"));
                }
            });

            if (r.slot != null) {
                int rows = Math.max(1, svc.skin.rows);
                int row = Math.max(0, Math.min(rows - 1, r.slot / 9));
                int col = Math.max(0, Math.min(8, r.slot % 9));
                contents.set(row, col, clickable);
            } else {
                it.set(clickable);
            }
        });

        int rows = Math.max(1, svc.skin.rows);
        if (rows >= 2 && p.hasPermission("oreo.prewards.admin")) {
            final int bottom = rows - 1;
            ItemStack lever = new ItemStack(Material.LEVER);
            ItemMeta lm = lever.getItemMeta();
            if (lm != null) {
                lm.setDisplayName(svc.color("&3Playtime Rewards: " + (featureOn ? "&aENABLED" : "&cDISABLED")));
                List<String> lore = new ArrayList<>();
                lore.add(svc.color("&7Click to " + (featureOn ? "&cDISABLE" : "&aENABLE")));
                lore.add(svc.color("&8(Admin only)"));
                lm.setLore(lore);
                lm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                lever.setItemMeta(lm);
            }
            contents.set(bottom, 5, ClickableItem.of(lever, e -> {
                boolean now = svc.toggleEnabled();
                p.sendMessage(svc.color("&7Playtime Rewards is now " + (now ? "&aENABLED" : "&cDISABLED")));
                inventory(p).open(p); // refresh
            }));
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }
}
