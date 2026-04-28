package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.config.SettingsConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies lightweight login pressure handling:
 * - hard-rate-limits extreme login bursts before full join
 * - delays heavy post-join UI work (scoreboard/tab/nametag) during floods
 */
public final class JoinFloodGuardService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final OreoEssentials plugin;
    private final Object loginLock = new Object();
    private final Object joinLock = new Object();
    private final Deque<Long> recentLoginAttemptsMs = new ArrayDeque<>();
    private final Deque<Long> recentJoinsMs = new ArrayDeque<>();
    private final ConcurrentHashMap<UUID, Long> uiReadyAtMs = new ConcurrentHashMap<>();

    public JoinFloodGuardService(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return settings().joinFloodProtectionEnabled();
    }

    public boolean isUiReady(Player player) {
        return player == null || isUiReady(player.getUniqueId());
    }

    public boolean isUiReady(UUID playerId) {
        if (!isEnabled() || playerId == null) {
            return true;
        }

        Long readyAt = uiReadyAtMs.get(playerId);
        if (readyAt == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now >= readyAt) {
            uiReadyAtMs.remove(playerId, readyAt);
            return true;
        }
        return false;
    }

    public long getDeferredJoinDelayTicks(Player player, long minimumDelayTicks) {
        long safeMinimum = Math.max(0L, minimumDelayTicks);
        if (!isEnabled() || player == null) {
            return safeMinimum;
        }

        Long readyAt = uiReadyAtMs.get(player.getUniqueId());
        if (readyAt == null) {
            return safeMinimum;
        }

        long remainingMs = readyAt - System.currentTimeMillis();
        if (remainingMs <= 0L) {
            uiReadyAtMs.remove(player.getUniqueId(), readyAt);
            return safeMinimum;
        }

        long deferredTicks = (remainingMs + 49L) / 50L;
        return Math.max(safeMinimum, deferredTicks);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isEnabled() || !settings().joinFloodLoginRateLimitEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMs = settings().joinFloodWindowMillis();
        int maxLogins = settings().joinFloodMaxLoginsPerWindow();

        synchronized (loginLock) {
            prune(recentLoginAttemptsMs, now, windowMs);
            if (recentLoginAttemptsMs.size() >= maxLogins) {
                event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        legacyMessage(settings().joinFloodKickMessage())
                );
                return;
            }
            recentLoginAttemptsMs.addLast(now);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMs = settings().joinFloodWindowMillis();
        int softLimit = settings().joinFloodUiSoftJoinsPerWindow();
        long baseDelayTicks = settings().joinFloodBaseUiDelayTicks();
        long extraDelayTicks = settings().joinFloodExtraUiDelayTicks();
        long maxDelayTicks = settings().joinFloodMaxUiDelayTicks();

        long delayTicks = 0L;
        synchronized (joinLock) {
            prune(recentJoinsMs, now, windowMs);
            recentJoinsMs.addLast(now);
            int windowCount = recentJoinsMs.size();
            int excess = Math.max(0, windowCount - softLimit);
            if (excess > 0) {
                delayTicks = Math.min(maxDelayTicks, baseDelayTicks + ((long) (excess - 1) * extraDelayTicks));
            }
        }

        if (delayTicks > 0L) {
            uiReadyAtMs.put(event.getPlayer().getUniqueId(), now + (delayTicks * 50L));
        } else {
            uiReadyAtMs.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        uiReadyAtMs.remove(event.getPlayer().getUniqueId());
    }

    private SettingsConfig settings() {
        return plugin.getSettingsConfig();
    }

    private static void prune(Deque<Long> deque, long now, long windowMs) {
        while (!deque.isEmpty()) {
            Long oldest = deque.peekFirst();
            if (oldest == null || now - oldest <= windowMs) {
                return;
            }
            deque.removeFirst();
        }
    }

    private static String legacyMessage(String input) {
        if (input == null || input.isBlank()) {
            return "Please reconnect in a moment.";
        }
        try {
            return LEGACY.serialize(MM.deserialize(input));
        } catch (Throwable ignored) {
            return input;
        }
    }
}
