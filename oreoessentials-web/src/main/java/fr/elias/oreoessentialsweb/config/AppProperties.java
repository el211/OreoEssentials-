package fr.elias.oreoessentialsweb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe binding for all {@code app.*} properties defined in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Encryption encryption = new Encryption();
    private Cors cors = new Cors();
    private Mongo mongo = new Mongo();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpirationMs = 900_000L;
        private long refreshTokenExpirationMs = 604_800_000L;
    }

    @Getter @Setter
    public static class Encryption {
        private String secretKey;
    }

    @Getter @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:3000";
    }

    @Getter @Setter
    public static class Mongo {
        private int connectTimeoutMs = 5000;
        private int socketTimeoutMs  = 10000;
        private int maxPoolSize      = 3;
    }
}
