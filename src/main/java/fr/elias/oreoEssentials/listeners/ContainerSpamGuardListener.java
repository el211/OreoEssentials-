package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.config.SettingsConfig;
import fr.elias.oreoEssentials.util.OreScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limits bursty container actions that can be abused by client macros/mods
 * to flood the server with inventory packets.
 */
public final class ContainerSpamGuardListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Set<InventoryType> PROTECTED_TOP_TYPES = EnumSet.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.HOPPER,
            InventoryType.DISPENSER,
            InventoryType.DROPPER,
            InventoryType.ENDER_CHEST,
            InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER,
            InventoryType.BREWING
    );

    private final OreoEssentials plugin;
    private final ConcurrentHashMap<UUID, BurstState> burstStates = new ConcurrentHashMap<>();

    private static final class BurstState {
        private long windowStartMs;
        private int score;
        private int strikes;
        private long lastStrikeAtMs;
        private long lastWarnAtMs;
    }

    public ContainerSpamGuardListener(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!settings().containerSpamGuardEnabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isProtectedContainer(event.getView().getTopInventory())) {
            return;
        }

        int weight = clickWeight(event);
        if (weight <= 0) {
            return;
        }

        if (shouldTrip(player.getUniqueId(), weight)) {
            trip(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!settings().containerSpamGuardEnabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isProtectedContainer(event.getView().getTopInventory())) {
            return;
        }

        int topSlotsTouched = 0;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                topSlotsTouched++;
            }
        }
        if (topSlotsTouched <= 0) {
            return;
        }

        int slotWeight = Math.max(1, settings().containerSpamGuardDragSlotWeight());
        int weight = Math.max(2, topSlotsTouched * slotWeight);
        if (shouldTrip(player.getUniqueId(), weight)) {
            trip(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && !player.isOnline()) {
            burstStates.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        burstStates.remove(event.getPlayer().getUniqueId());
    }

    private SettingsConfig settings() {
        return plugin.getSettingsConfig();
    }

    private boolean isProtectedContainer(Inventory top) {
        if (top == null || !PROTECTED_TOP_TYPES.contains(top.getType())) {
            return false;
        }
        InventoryHolder holder = top.getHolder();
        if (holder == null) {
            return true;
        }
        String holderClass = holder.getClass().getName();
        return !holderClass.startsWith("fr.elias.oreoEssentials")
                && !holderClass.startsWith("fr.minuskube.inv");
    }

    private int clickWeight(InventoryClickEvent event) {
        int weight = 0;

        if (event.isShiftClick()) {
            weight += Math.max(1, settings().containerSpamGuardShiftWeight());
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.UNKNOWN) {
            weight += Math.max(1, settings().containerSpamGuardTransferWeight());
        }

        ClickType click = event.getClick();
        if (click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK) {
            weight += 2;
        }

        if (weight == 0 && event.getClickedInventory() == event.getView().getTopInventory()) {
            weight = 1;
        }

        return weight;
    }

    private boolean shouldTrip(UUID playerId, int weight) {
        long now = System.currentTimeMillis();
        int windowMs = settings().containerSpamGuardWindowMillis();
        int maxScore = settings().containerSpamGuardMaxScore();

        BurstState state = burstStates.computeIfAbsent(playerId, id -> new BurstState());
        synchronized (state) {
            if (state.windowStartMs == 0L || now - state.windowStartMs > windowMs) {
                state.windowStartMs = now;
                state.score = 0;
            }
            state.score += Math.max(1, weight);
            return state.score > maxScore;
        }
    }

    private void trip(Player player, org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        int warnCooldownMs = settings().containerSpamGuardWarnCooldownMillis();
        int kickAfterStrikes = settings().containerSpamGuardKickAfterStrikes();

        BurstState state = burstStates.computeIfAbsent(playerId, id -> new BurstState());
        synchronized (state) {
            int windowMs = settings().containerSpamGuardWindowMillis();
            if (now - state.lastStrikeAtMs > (windowMs * 2L)) {
                state.strikes = 0;
            }

            state.strikes++;
            state.lastStrikeAtMs = now;
            state.score = settings().containerSpamGuardMaxScore();

            if (settings().containerSpamGuardCloseInventory()) {
                OreScheduler.runLaterForEntity(plugin, player, player::closeInventory, 1L);
            }

            if (kickAfterStrikes > 0 && state.strikes >= kickAfterStrikes) {
                String kickMessage = legacyMessage(settings().containerSpamGuardKickMessage());
                OreScheduler.runLaterForEntity(plugin, player, () -> player.kickPlayer(kickMessage), 1L);
                burstStates.remove(playerId);
                return;
            }

            if (warnCooldownMs <= 0 || now - state.lastWarnAtMs >= warnCooldownMs) {
                state.lastWarnAtMs = now;
                player.sendMessage(legacyMessage(settings().containerSpamGuardWarnMessage()));
            }
        }
    }

    private static String legacyMessage(String input) {
        if (input == null || input.isBlank()) {
            return "Slow down.";
        }
        try {
            return LEGACY.serialize(MM.deserialize(input));
        } catch (Throwable ignored) {
            return input;
        }
    }
}
