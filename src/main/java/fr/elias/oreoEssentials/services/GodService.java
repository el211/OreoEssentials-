package fr.elias.oreoEssentials.services;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GodService {
    private final Set<UUID> god = ConcurrentHashMap.newKeySet();

    public boolean toggle(UUID uuid) {
        if (god.contains(uuid)) {
            god.remove(uuid);
            return false;
        } else {
            god.add(uuid);
            return true;
        }
    }

    public boolean enable(UUID uuid) {
        return god.add(uuid);
    }

    public boolean disable(UUID uuid) {
        return god.remove(uuid);
    }

    public boolean isGod(UUID uuid) {
        return god.contains(uuid);
    }
}
