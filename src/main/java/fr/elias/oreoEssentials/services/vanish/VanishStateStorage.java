package fr.elias.oreoEssentials.services.vanish;

import java.util.UUID;

public interface VanishStateStorage {
    boolean isVanished(UUID playerId) throws Exception;
    void setVanished(UUID playerId, boolean vanished) throws Exception;
}
