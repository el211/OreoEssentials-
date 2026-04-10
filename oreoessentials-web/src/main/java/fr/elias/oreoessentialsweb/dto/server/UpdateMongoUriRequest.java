package fr.elias.oreoessentialsweb.dto.server;

import jakarta.validation.constraints.NotBlank;

public record UpdateMongoUriRequest(
        @NotBlank String mongoUri,
        @NotBlank String databaseName
) {}
