package fr.elias.oreoEssentials.modules.chat.channels;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for channel persistence providers
 * Allows switching between YAML (local) and MongoDB (cross-server)
 */
public interface ChannelPersistenceProvider {

    /**
     * Load all player channel preferences
     * @return Map of UUID to channel ID
     */
    Map<UUID, String> loadAll();

    /**
     * Save all player channel preferences (bulk operation)
     * @param data Map of UUID to channel ID
     */
    void saveAll(Map<UUID, String> data);

    /**
     * Save a single player's channel preference
     * @param uuid Player UUID
     * @param channelId Channel ID
     */
    void save(UUID uuid, String channelId);

    /**
     * Remove a player's channel preference
     * @param uuid Player UUID
     */
    void remove(UUID uuid);

    /**
     * Get a player's channel preference
     * @param uuid Player UUID
     * @return Channel ID or null
     */
    default String get(UUID uuid) {
        return loadAll().get(uuid);
    }
}