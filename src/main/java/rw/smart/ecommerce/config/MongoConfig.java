package rw.smart.ecommerce.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Wires the synchronous MongoDB driver by hand — the project depends on
 * {@code mongodb-driver-sync} rather than {@code spring-boot-starter-data-mongodb},
 * so there is no auto-configuration to inherit.
 *
 * {@code MongoClients.create} does not open a connection, it starts background
 * server discovery. That is deliberate: the document store is optional
 * infrastructure here, and an unreachable server must degrade reviews and access
 * logging without preventing the application from starting.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MongoSettings.class)
public class MongoConfig {

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(MongoSettings settings) {
        log.info("Configuring MongoDB client for database '{}'", settings.getDatabase());

        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(settings.getUri()))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(
                        settings.getServerSelectionTimeoutMs(), TimeUnit.MILLISECONDS))
                .build();

        return MongoClients.create(clientSettings);
    }

    @Bean
    public MongoDatabase mongoDatabase(MongoClient mongoClient, MongoSettings settings) {
        return mongoClient.getDatabase(settings.getDatabase());
    }
}
