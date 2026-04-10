package fr.elias.oreoessentialsweb.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerRegisterRequest(

        @NotBlank @Size(min = 3, max = 64)
        String username,

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 128)
        String password
) {}
