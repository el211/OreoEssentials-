package fr.elias.oreoEssentials.modules.invlook.manager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central invlook state manager (single source of truth).
 */
public final class InvlookManager {

    private final Set<UUID> readOnly = ConcurrentHashMap.newKeySet();

    /** Enable invlook (read-only) mode for a viewer */
    public void markReadOnly(UUID viewer) {
        readOnly.add(viewer);
    }

    /** Disable invlook mode for a viewer */
    public void unmark(UUID viewer) {
        readOnly.remove(viewer);
    }

    /** Check if viewer is in invlook mode */
    public boolean isReadOnly(UUID viewer) {
        return readOnly.contains(viewer);
    }

    /** Safety cleanup on reload / shutdown */
    public void clear() {
        readOnly.clear();
    }
}
