package fr.elias.oreoEssentials.modules.portals;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the portal selection wand.
 *
 * Usage:
 *  /portal wand  — gives the wand item and enters wand mode
 *  Left-click block  → set POS1
 *  Right-click block → set POS2
 *
 * Players must have oreo.portals.create permission to use the wand.
 */
public final class PortalWandListener implements Listener {

    private final PortalsManager manager;
    private final PortalConfig config;

    /** Players currently in wand mode */
    private final Set<UUID> wandMode = ConcurrentHashMap.newKeySet();

    public PortalWandListener(PortalsManager manager, PortalConfig config) {
        this.manager = manager;
        this.config = config;
    }

    public void enableWandMode(Player p) {
        wandMode.add(p.getUniqueId());
        p.getInventory().addItem(config.buildWand());
        p.sendMessage(ChatColor.GOLD + "Portal wand activated! "
                + ChatColor.GRAY + "Left-click = POS1, Right-click = POS2.");
    }

    public void disableWandMode(Player p) {
        wandMode.remove(p.getUniqueId());
    }

    public boolean isInWandMode(Player p) {
        return wandMode.contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();

        if (!wandMode.contains(p.getUniqueId())) return;
        if (!p.hasPermission("oreo.portals.create")) return;

        // Must be holding the wand
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!isWandItem(held)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = block.getLocation();
            manager.setPos1(p, loc);
            Lang.send(p, "portals.pos1-set",
                    "<green>POS1 set → <aqua>%location%</aqua></green>",
                    Map.of("location", locStr(loc)));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = block.getLocation();
            manager.setPos2(p, loc);
            Lang.send(p, "portals.pos2-set",
                    "<green>POS2 set → <aqua>%location%</aqua></green>",
                    Map.of("location", locStr(loc)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wandMode.remove(event.getPlayer().getUniqueId());
    }

    private boolean isWandItem(ItemStack item) {
        if (item == null || item.getType() != config.getWandMaterial()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.getDisplayName().contains("Portal Wand");
    }

    private String locStr(Location l) {
        return l.getWorld().getName()
                + " (" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")";
    }
}
