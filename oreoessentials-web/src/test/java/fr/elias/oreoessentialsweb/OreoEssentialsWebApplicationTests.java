package fr.elias.oreoessentialsweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic context load test.
 * Run with: mvn test -pl oreoessentials-web
 *
 * For full integration testing, configure a test PostgreSQL and set
 * environment variables (DB_URL, DB_USERNAME, DB_PASSWORD).
 */
@SpringBootTest
@ActiveProfiles("test")
class OreoEssentialsWebApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context starts without errors
    }
}
