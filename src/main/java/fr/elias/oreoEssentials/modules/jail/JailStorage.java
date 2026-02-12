package fr.elias.oreoEssentials.modules.jail;

import java.util.*;

/**
 * Storage interface for jail system data.
 * Supports both YAML (single-server) and MongoDB (multi-server) implementations.
 */
public interface JailStorage {


    /**
     * Load all jails from storage.
     * @return Map of jail name (lowercase) -> Jail object
     */
    Map<String, JailModels.Jail> loadJails();

    /**
     * Save all jails to storage (overwrites existing).
     * @param all Map of all jails to persist
     */
    void saveJails(Map<String, JailModels.Jail> all);

    /**
     * Load all active sentences from storage.
     * @return Map of player UUID -> Sentence object
     */
    Map<UUID, JailModels.Sentence> loadSentences();

    /**
     * Save all sentences to storage (overwrites existing).
     * @param sentences Map of all active sentences to persist
     */
    void saveSentences(Map<UUID, JailModels.Sentence> sentences);


    /**
     * Save or update a single jail (upsert).
     * More efficient than saveJails() when only one jail changes.
     *
     * @param jail The jail to save/update
     */
    default void saveJail(JailModels.Jail jail) {
        // Default: not supported by YAML implementation
        throw new UnsupportedOperationException("Individual jail save not supported by this storage backend");
    }

    /**
     * Delete a single jail by name.
     *
     * @param jailName The name of the jail to delete (case-insensitive)
     */
    default void deleteJail(String jailName) {
        // Default: not supported by YAML implementation
        throw new UnsupportedOperationException("Individual jail delete not supported by this storage backend");
    }

    /**
     * Save or update a single sentence (upsert).
     * More efficient than saveSentences() when only one sentence changes.
     *
     * @param sentence The sentence to save/update
     */
    default void saveSentence(JailModels.Sentence sentence) {
        // Default: not supported by YAML implementation
        throw new UnsupportedOperationException("Individual sentence save not supported by this storage backend");
    }

    /**
     * Delete a single sentence by player UUID.
     *
     * @param player The UUID of the player whose sentence to delete
     */
    default void deleteSentence(UUID player) {
        // Default: not supported by YAML implementation
        throw new UnsupportedOperationException("Individual sentence delete not supported by this storage backend");
    }


    /**
     * Close any open connections/resources.
     * Called when the plugin disables.
     */
    void close();
}