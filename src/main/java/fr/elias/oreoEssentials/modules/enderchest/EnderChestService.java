package fr.elias.oreoEssentials.modules.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;


public class EnderChestService {

    public static final String TITLE = Lang.color(Lang.get("enderchest.gui.title", "&5Ender Chest"));
    private static final int MAX_SIZE = 54; // 6 rows * 9

    private final OreoEssentials plugin;
    private final EnderChestConfig config;
    private final EnderChestStorage storage;
    private final NamespacedKey LOCK_KEY;

    public EnderChestService(OreoEssentials plugin, EnderChestConfig config, EnderChestStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.LOCK_KEY = new NamespacedKey(plugin, "ec_locked");
    }

    public void open(Player p) {
        OreScheduler.runForEntity(plugin, p, () -> p.openInventory(createVirtualEc(p)));
    }


    public Inventory createVirtualEc(Player p) {
        int allowedSlots = resolveSlots(p);
        int rowsForStorage = Math.max(1, (int) Math.ceil(allowedSlots / 9.0));

        Inventory inv = Bukkit.createInventory(p, MAX_SIZE, TITLE);

        ItemStack[] stored = storage.load(p.getUniqueId(), rowsForStorage);
        if (stored != null) {
            for (int i = 0; i < Math.min(stored.length, allowedSlots); i++) {
                inv.setItem(i, stored[i]);
            }
        }

        for (int slot = allowedSlots; slot < MAX_SIZE; slot++) {
            inv.setItem(slot, lockedBarrierItem(allowedSlots));
        }
        return inv;
    }


    public void saveFromInventory(Player p, Inventory inv) {
        try {
            int allowed = resolveSlots(p);

            final int MAX_ROWS = 6;
            final int MAX_SIZE = MAX_ROWS * 9;

            UUID uuid = p.getUniqueId();

            ItemStack[] existing = storage.load(uuid, MAX_ROWS);
            if (existing == null || existing.length < MAX_SIZE) {
                ItemStack[] fixed = new ItemStack[MAX_SIZE];
                if (existing != null) {
                    System.arraycopy(existing, 0, fixed, 0, existing.length);
                }
                existing = fixed;
            }

            ItemStack[] src = inv.getContents();
            for (int i = 0; i < allowed && i < src.length; i++) {
                ItemStack it = src[i];
                existing[i] = (isLockItem(it) ? null : it);
            }


            storage.save(uuid, MAX_ROWS, existing);
        } catch (Throwable t) {
            plugin.getLogger().warning("[EC] Save failed for " + p.getUniqueId() + ": " + t.getMessage());
            Lang.send(p, "enderchest.storage.save-failed",
                    "<red>Failed to save your ender chest. Please contact an administrator.</red>",
                    Map.of());
        }
    }


    public ItemStack[] loadFor(UUID uuid, int rows) {
        return storage.load(uuid, rows);
    }


    public void saveFor(UUID uuid, int rows, ItemStack[] contents) {
        storage.save(uuid, rows, contents);
    }


    public int resolveSlots(Player p) {
        int slots = 9; // absolute minimum fallback

        // Permission-based (oreo.tier.<key>)
        if (config.isPermissionSlotsEnabled()) {
            slots = Math.max(slots, config.getDefaultSlots());
            for (var e : config.getRankSlots().entrySet()) {
                String node = ("oreo.tier." + e.getKey()).toLowerCase(Locale.ROOT);
                if (p.hasPermission(node)) slots = Math.max(slots, e.getValue());
            }
        }

        // LuckPerms group-based (no permission nodes needed)
        if (config.isRankSlotsEnabled()) {
            slots = Math.max(slots, config.getRankSlotsDefault());
            Map<String, Integer> groupSlots = config.getRankGroupSlots();
            if (!groupSlots.isEmpty()) {
                for (String group : getLuckPermsGroups(p)) {
                    Integer v = groupSlots.get(group);
                    if (v != null) slots = Math.max(slots, v);
                }
            }
        }

        return Math.max(1, Math.min(slots, MAX_SIZE));
    }

    private Set<String> getLuckPermsGroups(Player player) {
        try {
            var reg = Bukkit.getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class);
            if (reg == null) return Collections.emptySet();
            net.luckperms.api.model.user.User user = reg.getProvider()
                    .getPlayerAdapter(Player.class).getUser(player);
            Set<String> groups = user.getNodes(net.luckperms.api.node.NodeType.INHERITANCE)
                    .stream()
                    .map(n -> n.getGroupName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(HashSet::new));
            groups.add(user.getPrimaryGroup().toLowerCase(Locale.ROOT));
            return groups;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }


    public int resolveSlotsOffline(UUID uuid) {
        ItemStack[] stored = storage.load(uuid, 6);
        if (stored == null) {
            return Math.max(1, Math.min(config.getDefaultSlots(), MAX_SIZE));
        }
        int slots = Math.min(stored.length, MAX_SIZE);
        if (slots <= 0) slots = config.getDefaultSlots();
        return Math.max(1, Math.min(slots, MAX_SIZE));
    }

    public boolean isLockedSlot(Player p, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= MAX_SIZE) return false;
        return rawSlot >= resolveSlots(p);
    }


    public boolean isLockItem(ItemStack it) {
        if (it == null || it.getType() != Material.BARRIER) return false;
        try {
            ItemMeta meta = it.getItemMeta();
            if (meta == null) return false;
            Integer mark = meta.getPersistentDataContainer().get(LOCK_KEY, PersistentDataType.INTEGER);
            return mark != null && mark == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }


    private ItemStack lockedBarrierItem(int allowedSlots) {
        ItemStack b = new ItemStack(Material.BARRIER);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            String name = Lang.get("enderchest.gui.locked-slot-name", "&cLocked slot");
            m.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> rawLore = Lang.getList("enderchest.gui.locked-slot-lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                line = line.replace("%slots%", String.valueOf(allowedSlots));
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            m.setLore(lore);

            m.getPersistentDataContainer().set(LOCK_KEY, PersistentDataType.INTEGER, 1);
            b.setItemMeta(m);
        }
        return b;
    }
}
