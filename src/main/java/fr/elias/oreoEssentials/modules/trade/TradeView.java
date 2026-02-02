package fr.elias.oreoEssentials.modules.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.trade.config.TradeConfig;
import fr.elias.oreoEssentials.modules.trade.ui.TradeMenu;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TradeView {

    private static final int[] A_AREA_SLOTS = rectSlots(2, 2, 3, 3);
    private static final int[] B_AREA_SLOTS = rectSlots(2, 6, 3, 3);

    private static final Set<Integer> A_ALLOWED = toSet(A_AREA_SLOTS);
    private static final Set<Integer> B_ALLOWED = toSet(B_AREA_SLOTS);

    private static final Map<UUID, TradeSession> VIEW_SESSION = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> VIEW_SIDE_A = new ConcurrentHashMap<>();

    private static volatile boolean LISTENER_REGISTERED = false;

    private TradeView() {}

    public static void registerGuards(OreoEssentials plugin, TradeConfig cfg, TradeSession session,
                                      Player viewer, boolean forA) {
        if (viewer == null) return;
        VIEW_SESSION.put(viewer.getUniqueId(), session);
        VIEW_SIDE_A.put(viewer.getUniqueId(), forA);
        ensureListener(plugin);
    }

    public static void unregisterViewer(Player p) {
        if (p != null) {
            VIEW_SESSION.remove(p.getUniqueId());
            VIEW_SIDE_A.remove(p.getUniqueId());
        }
    }

    private static void ensureListener(OreoEssentials plugin) {
        if (LISTENER_REGISTERED) return;
        synchronized (TradeView.class) {
            if (LISTENER_REGISTERED) return;
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(new GuardListener(plugin), plugin);
            LISTENER_REGISTERED = true;
        }
    }

    private static boolean isTradeTop(OreoEssentials plugin, Player p) {
        try {
            Optional<SmartInventory> inv = plugin.getInvManager().getInventory(p);
            return inv.isPresent() && (inv.get().getProvider() instanceof TradeMenu);
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isAllowedSlot(UUID pid, int rawTopSlot) {
        Boolean forA = VIEW_SIDE_A.get(pid);
        if (forA == null) return false;
        return forA ? A_ALLOWED.contains(rawTopSlot) : B_ALLOWED.contains(rawTopSlot);
    }

    private static int rawTopSlot(int row, int col) {
        return row * 9 + col;
    }

    private static int[] rectSlots(int startRow, int startCol, int height, int width) {
        int[] out = new int[height * width];
        int k = 0;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                out[k++] = rawTopSlot(startRow + r, startCol + c);
            }
        }
        return out;
    }

    private static Set<Integer> toSet(int[] arr) {
        Set<Integer> s = new HashSet<>(arr.length * 2);
        for (int v : arr) s.add(v);
        return Collections.unmodifiableSet(s);
    }

    private static final class GuardListener implements Listener {
        private final OreoEssentials plugin;

        GuardListener(OreoEssentials plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();

            if (!VIEW_SESSION.containsKey(p.getUniqueId())) return;
            if (!isTradeTop(plugin, p)) {
                unregisterViewer(p);
                return;
            }

            switch (e.getAction()) {
                case COLLECT_TO_CURSOR:
                case MOVE_TO_OTHER_INVENTORY:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD:
                case SWAP_WITH_CURSOR:
                case UNKNOWN:
                    e.setCancelled(true);
                    return;
                default:
                    break;
            }

            Inventory top = p.getOpenInventory().getTopInventory();
            if (e.getClickedInventory() == top) {
                int raw = e.getSlot();
                if (!isAllowedSlot(p.getUniqueId(), raw)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onDrag(InventoryDragEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();

            if (!VIEW_SESSION.containsKey(p.getUniqueId())) return;
            if (!isTradeTop(plugin, p)) {
                unregisterViewer(p);
                return;
            }

            Inventory top = p.getOpenInventory().getTopInventory();
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize()) {
                    if (!isAllowedSlot(p.getUniqueId(), raw)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getPlayer() instanceof Player)) return;
            Player p = (Player) e.getPlayer();
            if (VIEW_SESSION.containsKey(p.getUniqueId()) && !isTradeTop(plugin, p)) {
                unregisterViewer(p);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent e) {
            unregisterViewer(e.getPlayer());
        }
    }
}