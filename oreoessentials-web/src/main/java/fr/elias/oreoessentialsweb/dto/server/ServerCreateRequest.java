package fr.elias.oreoessentialsweb.dto.server;

import fr.elias.oreoessentialsweb.model.ServerConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServerCreateRequest(

        @NotBlank @Size(max = 128)
        String name,

        /** URL-safe slug: lowercase letters, digits, hyphens only. */
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,64}$",
                message = "Slug must be 3-64 chars, lowercase letters/digits/hyphens only")
        String slug,

        @Size(max = 512)
        String logoUrl,

        @Size(max = 512)
        String description,

        /**
         * Optional: raw MongoDB URI.
         * If provided, it will be AES-encrypted and stored in ServerConfig.
         * Required when dataSourceMode = DIRECT_MONGO or HYBRID.
         */
        String mongoUri,

        @Size(max = 128)
        String mongoDatabaseName,

        /** Defaults to API_SYNC if null. */
        ServerConfig.DataSourceMode dataSourceMode
) {}
