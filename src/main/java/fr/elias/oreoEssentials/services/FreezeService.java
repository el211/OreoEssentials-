package fr.elias.oreoEssentials.services;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeService {
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    public boolean isFrozen(UUID id) { return frozen.contains(id); }
    public boolean set(UUID id, boolean state) { return state ? frozen.add(id) : frozen.remove(id); }
}
