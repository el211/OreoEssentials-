package fr.elias.oreoessentialsweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import fr.elias.oreoessentialsweb.config.AppProperties;

/**
 * OreoEssentials Web Panel — multi-tenant SaaS dashboard.
 *
 * MongoDB auto-configuration is disabled here because we manage
 * per-server MongoClient instances dynamically at runtime
 * (each server owner has their own MongoDB URI).
 */
@SpringBootApplication(exclude = {MongoAutoConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class OreoEssentialsWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(OreoEssentialsWebApplication.class, args);
    }
}
