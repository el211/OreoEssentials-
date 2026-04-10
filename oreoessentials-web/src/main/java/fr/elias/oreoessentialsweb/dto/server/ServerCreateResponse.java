package fr.elias.oreoessentialsweb.dto.server;

public record ServerCreateResponse(
        Long serverId,
        String name,
        String slug,

        /**
         * The raw API key — shown ONCE at creation time.
         * The owner must copy this immediately; it cannot be retrieved later.
         * Store it in the OreoEssentials plugin config.yml.
         */
        String apiKey,

        String message
) {}
