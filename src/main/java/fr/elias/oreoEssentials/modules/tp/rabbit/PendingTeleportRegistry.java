// src/main/java/fr/elias/oreoEssentials/teleport/PendingTeleportRegistry.java
package fr.elias.oreoEssentials.modules.tp.rabbit;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingTeleportRegistry {
    public enum Kind { HOME, WARP, SPAWN, LOCATION }

    public static final class Pending {
        public final Kind kind;
        public final String name;
        public final Location direct;
        public Pending(Kind kind, String name, Location direct) { this.kind = kind; this.name = name; this.direct = direct; }
        @Override public String toString() { return "Pending{kind=" + kind + ", name=" + name + ", direct=" + direct + '}'; }
    }

    private final Map<UUID, Pending> map = new ConcurrentHashMap<>();

    public void put(UUID id, Pending p) { if (p != null) map.put(id, p); }
    public Pending get(UUID id) { return map.get(id); }
    public Pending remove(UUID id) { return map.remove(id); }
    public boolean has(UUID id) { return map.containsKey(id); }
}
