package fr.elias.oreoEssentials.modules.rtp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RtpPendingService {
    private final Map<UUID, String> pending = new ConcurrentHashMap<>();

    public void add(UUID uuid, String worldName) {
        pending.put(uuid, worldName);
    }

    public String consume(UUID uuid) {
        return pending.remove(uuid);
    }
}

