package fr.elias.oreoessentialsweb.dto.server;

import java.time.Instant;

public record ServerResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String description,
        boolean active,
        Instant createdAt
) {}
