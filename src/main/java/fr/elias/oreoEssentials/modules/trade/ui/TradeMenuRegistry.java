package fr.elias.oreoEssentials.modules.trade.ui;

import fr.elias.oreoEssentials.modules.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TradeMenuRegistry {
    private final ConcurrentMap<UUID, TradeMenu> byViewer = new ConcurrentHashMap<>();

    public void register(UUID viewerId, TradeMenu menu) {
        if (viewerId == null || menu == null) return;
        byViewer.put(viewerId, menu);
    }

    public void unregister(UUID viewerId) {
        if (viewerId == null) return;
        byViewer.remove(viewerId);
    }

    public TradeMenu get(UUID viewerId) {
        if (viewerId == null) return null;
        return byViewer.get(viewerId);
    }

    public TradeMenu peek(UUID viewer) {
        if (viewer == null) return null;
        return byViewer.get(viewer);
    }

    public void refreshViewer(UUID viewerId) {
        TradeMenu m = get(viewerId);
        if (m == null) return;

        try {
            Bukkit.getScheduler().runTask(m.getPlugin(), m::refreshFromSession);
        } catch (Throwable ignored) {}
    }


    public void ensureOpen(UUID viewerId, TradeSession session) {
        if (viewerId == null || session == null) return;

        TradeMenu existing = byViewer.get(viewerId);
        if (existing != null && existing.isOpenFor(viewerId)) {
            return;
        }

        TradeMenu newMenu = TradeMenu.createForSession(session);
        if (newMenu == null) return;

        byViewer.put(viewerId, newMenu);

        Player p = Bukkit.getPlayer(viewerId);
        if (p != null && p.isOnline()) {
            newMenu.openFor(viewerId);
        }
    }
    public void closeAll() {
        byViewer.clear();
    }
}
