package fr.elias.oreoEssentials.modules.trade.pending;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class InMemoryPendingGrantsDao implements PendingGrantsDao {
    private static final class Entry {
        UUID sessionId;
        ItemStack[] items;
        Entry(UUID s, ItemStack[] i) { sessionId = s; items = i; }
    }

    private final Map<UUID, Entry> map = new ConcurrentHashMap<>();

    @Override
    public void storePending(UUID target, UUID sessionId, ItemStack[] items) {
        if (target == null) return;
        map.put(target, new Entry(sessionId, cloneArray(items)));
    }

    @Override
    public PendingItems fetchAndDelete(UUID target) {
        if (target == null) return null;
        Entry e = map.remove(target);
        if (e == null) return null;
        return new PendingItems(e.sessionId, cloneArray(e.items));
    }

    private static ItemStack[] cloneArray(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            var it = src[i];
            out[i] = (it == null ? null : it.clone());
        }
        return out;
    }
}
