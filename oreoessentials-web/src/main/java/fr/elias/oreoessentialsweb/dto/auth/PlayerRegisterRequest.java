package fr.elias.oreoessentialsweb.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PlayerRegisterRequest(

        @NotBlank @Size(min = 3, max = 64)
        String username,

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 128)
        String password,

        /** Optional — player's Minecraft UUID for pre-linking. Can be set later. */
        UUID minecraftUuid,

        String minecraftUsername
) {}
