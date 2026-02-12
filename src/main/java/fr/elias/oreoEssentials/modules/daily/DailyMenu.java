package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public final class DailyMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private final DailyService svc;
    private final RewardsConfig rewards;
    private final int page;

    public DailyMenu(OreoEssentials plugin, DailyConfig cfg, DailyService svc, RewardsConfig rewards) {
        this(plugin, cfg, svc, rewards, 1);
    }

    private DailyMenu(OreoEssentials plugin, DailyConfig cfg, DailyService svc, RewardsConfig rewards, int page) {
        this.plugin  = plugin;
        this.cfg     = cfg;
        this.svc     = svc;
        this.rewards = rewards;
        this.page    = Math.max(1, page);
    }

    public SmartInventory inventory(Player p) {
        return SmartInventory.builder()
                .manager(plugin.getInvManager())
                .id("daily:" + p.getUniqueId() + ":" + page)
                .provider(this)
                .size(cfg.inventoryRows, 9)
                .title(svc.color(cfg.guiTitle + " &8(&f" + page + "&8)"))
                .build();
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        // Collect and sort days
        final List<RewardsConfig.DayDef> defs = new ArrayList<>(rewards.all());
        defs.sort(Comparator.comparingInt(d -> d.day));

        final int rows       = cfg.inventoryRows;
        final int usableRows = rows >= 2 ? rows - 1 : rows;
        final int perPage    = Math.max(0, usableRows) * 9;

        final int total      = Math.max(rewards.maxDay(), defs.size());
        final int safePP     = Math.max(1, perPage);
        final int maxPage    = Math.max(1, (int) Math.ceil(total / (double) safePP));
        final int idxStart   = (page - 1) * safePP + 1;
        final int idxEnd     = Math.min(idxStart + safePP - 1, total);

        final int currentStreak = svc.getStreak(p.getUniqueId());
        final int todayIndex    = svc.nextDayIndex(currentStreak);

        final boolean featureOn = svc.isEnabled();

        final ClickableItem[] grid = new ClickableItem[perPage];
        final AtomicInteger flowingIdx = new AtomicInteger(0);

        for (int day = idxStart; day <= idxEnd; day++) {
            final RewardsConfig.DayDef def = rewards.day(day);

            final Material mat = (def != null && def.icon != null) ? def.icon : Material.SUNFLOWER;
            final ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            String title = (def != null && def.name != null && !def.name.isEmpty())
                    ? def.name
                    : "Day " + day;

            final List<String> lore = new ArrayList<>(2);
            final boolean isReadyToday = (day == todayIndex) && svc.canClaimToday(p);

            if (!featureOn) {
                title = "&8" + title;
                lore.clear();
                lore.add("&7Status: &cDISABLED");
            } else if (day < todayIndex) {
                title = "&b" + title;
                lore.add("&7Status: &fCLAIMED");
            } else if (day == todayIndex) {
                if (isReadyToday) {
                    title = "&a" + title;
                    lore.add("&eClick to claim!");
                } else {
                    title = "&c" + title;
                    lore.add("&7Status: &fNOT READY");
                }
            } else {
                title = "&7" + title;
                lore.add("&7Status: &fFUTURE");
            }

            if (meta != null) {
                if (def != null && def.customModelData != null && def.customModelData > 0) {
                    try {
                        meta.setCustomModelData(def.customModelData.intValue());
                    } catch (Throwable ignored) {
                        try { meta.setCustomModelData(def.customModelData); } catch (Throwable ignored2) {}
                    }
                }

                meta.setDisplayName(svc.color(title));

                final List<String> coloredLore = new ArrayList<>(lore.size());
                for (String line : lore) coloredLore.add(svc.color(line));
                meta.setLore(coloredLore);

                if (def != null && def.enchanted) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                }
                if (cfg.hideAttributes) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }

            final int dayNumFinal = day;
            final boolean isReadyFinal = isReadyToday;

            final ClickableItem ci = ClickableItem.of(item, e -> {
                if (!svc.isEnabled()) {
                    Lang.send(p, "daily.gui.disabled",
                            "<red>Daily Rewards is disabled.</red>",
                            Map.of());
                    return;
                }

                if (dayNumFinal == todayIndex && isReadyFinal) {
                    final boolean ok = svc.claim(p);
                    if (ok && cfg.closeOnClaim) {
                        p.closeInventory();
                        return;
                    }
                    new DailyMenu(plugin, cfg, svc, rewards, page).inventory(p).open(p);
                }
            });

            boolean placed = false;
            if (def != null && def.guiPage != null && def.guiPage == page && def.guiPos != null && perPage > 0) {
                final int pos = def.guiPos;
                if (pos >= 0 && pos < perPage && grid[pos] == null) {
                    grid[pos] = ci;
                    placed = true;
                }
            }

            if (!placed && perPage > 0) {
                placeNextFree(grid, flowingIdx, perPage, ci);
            }
        }

        for (int slot = 0; slot < perPage; slot++) {
            final ClickableItem ci = grid[slot];
            if (ci == null) continue;
            final int row = slot / 9;
            final int col = slot % 9;
            contents.set(row, col, ci);
        }

        if (rows >= 2) {
            final int bottom = rows - 1;

            {
                final ItemStack prev = new ItemStack(Material.ARROW);
                final ItemMeta pm = prev.getItemMeta();
                if (pm != null) {
                    pm.setDisplayName(svc.color("&3Previous Page &f[" + Math.max(1, page - 1) + "/" + maxPage + "]"));
                    prev.setItemMeta(pm);
                }
                contents.set(bottom, 0, ClickableItem.of(prev, e -> {
                    final int np = Math.max(1, page - 1);
                    new DailyMenu(plugin, cfg, svc, rewards, np).inventory(p).open(p);
                }));
            }

            {
                final ItemStack stats = new ItemStack(Material.PLAYER_HEAD);
                final ItemMeta sm = stats.getItemMeta();
                if (sm != null) {
                    sm.setDisplayName(svc.color("&3" + p.getName() + "'s Daily Stats"));
                    final List<String> statsLore = new ArrayList<>(2);
                    statsLore.add(svc.color("&bStreak: &f" + currentStreak));
                    statsLore.add(svc.color("&bToday: &fDay " + todayIndex));
                    sm.setLore(statsLore);
                    if (cfg.hideAttributes) sm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                    stats.setItemMeta(sm);
                }
                contents.set(bottom, 4, ClickableItem.empty(stats));
            }

            if (p.hasPermission("oreo.daily.admin")) {
                final ItemStack lever = new ItemStack(Material.LEVER);
                final ItemMeta lm = lever.getItemMeta();
                if (lm != null) {
                    lm.setDisplayName(svc.color("&3Daily Rewards: " + (featureOn ? "&aENABLED" : "&cDISABLED")));
                    lm.setLore(java.util.List.of(
                            svc.color("&7Click to " + (featureOn ? "&cDISABLE" : "&aENABLE")),
                            svc.color("&8(Admin only)")
                    ));
                    if (cfg.hideAttributes) lm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    lever.setItemMeta(lm);
                }
                contents.set(bottom, 5, ClickableItem.of(lever, e -> {
                    boolean now = svc.toggleEnabled();
                    cfg.setEnabled(now);
                    try { cfg.save(); } catch (Throwable ignored) {}

                    Lang.send(p, "daily.gui.toggled",
                            "<yellow>Daily Rewards is now %state%</yellow>",
                            Map.of("state", now ? "<green>ENABLED</green>" : "<red>DISABLED</red>"));

                    new DailyMenu(plugin, cfg, svc, rewards, page).inventory(p).open(p); // refresh view
                }));
            }

            {
                final ItemStack next = new ItemStack(Material.ARROW);
                final ItemMeta nm = next.getItemMeta();
                if (nm != null) {
                    nm.setDisplayName(svc.color("&3Next Page &f[" + Math.min(maxPage, page + 1) + "/" + maxPage + "]"));
                    next.setItemMeta(nm);
                }
                contents.set(bottom, 8, ClickableItem.of(next, e -> {
                    final int np = Math.min(maxPage, page + 1);
                    new DailyMenu(plugin, cfg, svc, rewards, np).inventory(p).open(p);
                }));
            }
        }
    }

    private static void placeNextFree(ClickableItem[] grid, AtomicInteger flowingIdx, int perPage, ClickableItem ci) {
        int i = flowingIdx.get();
        while (i < perPage && grid[i] != null) i++;
        if (i < perPage) {
            grid[i] = ci;
            flowingIdx.set(i + 1);
        }
    }

    @Override
    public void update(Player p, InventoryContents contents) {
    }
}