package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class PlayerSyncCommand implements OreoCommand, Listener {
    private final OreoEssentials plugin;
    private final PlayerSyncService service;
    private final boolean masterEnabled;

    public PlayerSyncCommand(OreoEssentials plugin, PlayerSyncService service, boolean masterEnabled) {
        this.plugin = plugin;
        this.service = service;
        this.masterEnabled = masterEnabled;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public String name() { return "sync"; }
    @Override public List<String> aliases() { return List.of("playersync"); }
    @Override public String permission() { return "oreo.sync"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        Inventory gui = Bukkit.createInventory(p, 9, "§bPlayer Sync");

        PlayerSyncPrefs prefs = service.prefs().get(p.getUniqueId());
        gui.setItem(0, toggleItem(Material.CHEST, "Inventory", prefs.inv));
        gui.setItem(2, toggleItem(Material.EXPERIENCE_BOTTLE, "XP", prefs.xp));
        gui.setItem(4, toggleItem(Material.GOLDEN_APPLE, "Health", prefs.health));
        gui.setItem(6, toggleItem(Material.COOKED_BEEF, "Hunger", prefs.hunger));
        gui.setItem(8, toggleItem(Material.GLASS_BOTTLE, "Potions", prefs.potions));

        if (!masterEnabled) {
            gui.setItem(4, disabledCenter());
        }

        OreScheduler.runForEntity(plugin, p, () -> p.openInventory(gui));
        return true;
    }

    private ItemStack toggleItem(Material m, String name, boolean on) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName((on ? "§a" : "§c") + name + " §7[" + (on ? "ON" : "OFF") + "]");
        meta.setLore(List.of("§7Click to toggle"));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack disabledCenter() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§cSync disabled by server config");
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTitle().equals("§bPlayer Sync")) {
            e.setCancelled(true);
            if (!masterEnabled) return;

            int slot = e.getRawSlot();
            PlayerSyncPrefs prefs = service.prefs().get(p.getUniqueId());
            switch (slot) {
                case 0 -> prefs.inv = !prefs.inv;
                case 2 -> prefs.xp = !prefs.xp;
                case 4 -> prefs.health = !prefs.health;
                case 6 -> prefs.hunger = !prefs.hunger;
                case 8 -> prefs.potions = !prefs.potions;
                default -> { return; }
            }
            service.prefs().set(p.getUniqueId(), prefs);
            p.sendMessage(ChatColor.GRAY + "Updated sync prefs.");
            // re-open to refresh
            OreScheduler.runForEntity(plugin, p, () -> {
                p.closeInventory();
                execute(p, "sync", new String[0]);
            });
        }
    }
}
