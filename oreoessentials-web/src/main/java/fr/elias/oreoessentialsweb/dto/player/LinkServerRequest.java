package fr.elias.oreoessentialsweb.dto.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkServerRequest(
        @NotBlank String serverSlug,
        @NotNull UUID minecraftUuid
) {}
