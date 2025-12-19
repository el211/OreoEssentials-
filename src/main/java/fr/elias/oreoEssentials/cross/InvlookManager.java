// File: src/main/java/fr/elias/oreoEssentials/cross/InvlookManager.java
package fr.elias.oreoEssentials.cross;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvlookManager {

    private final Set<UUID> readOnly = ConcurrentHashMap.newKeySet();

    public void markReadOnly(UUID viewer) {
        readOnly.add(viewer);
    }

    public boolean isReadOnly(UUID viewer) {
        return readOnly.contains(viewer);
    }

    public void unmark(UUID viewer) {
        readOnly.remove(viewer);
    }
}
