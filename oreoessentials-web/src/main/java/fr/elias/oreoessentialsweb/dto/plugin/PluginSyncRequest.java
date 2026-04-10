package fr.elias.oreoessentialsweb.dto.plugin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload sent by the OreoEssentials plugin when syncing player data.
 *
 * The plugin sends this after each relevant player event (login, logout,
 * balance change, etc.) or on a scheduled interval.
 *
 * {@code playerDataJson} is the full JSON snapshot of the player's data.
 * Its schema is defined by the plugin and may vary between plugin versions.
 */
public record PluginSyncRequest(
        @NotNull UUID playerUuid,
        @NotBlank String playerName,

        /**
         * Full player data snapshot as JSON.
         * Recommended fields: balance, playtime, stats, inventoryBase64,
         * armorBase64, potionEffects, jobs, orders, lastSeen.
         */
        @NotBlank String playerDataJson
) {}
