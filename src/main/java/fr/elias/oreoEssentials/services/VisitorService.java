// File: src/main/java/fr/elias/oreoEssentials/services/VisitorService.java
package fr.elias.oreoEssentials.services;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VisitorService {
    private final Set<UUID> visitors = ConcurrentHashMap.newKeySet();

    public boolean isVisitor(UUID id) {
        return visitors.contains(id);
    }

    public void setVisitor(UUID id, boolean on) {
        if (on) visitors.add(id);
        else visitors.remove(id);
    }
}
