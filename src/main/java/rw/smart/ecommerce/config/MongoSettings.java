package rw.smart.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code app.mongo.*} keys from the active profile. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.mongo")
public class MongoSettings {

    private String uri = "mongodb://localhost:27017";
    private String database = "smart_ecommerce_db";
    private String reviewCollection = "reviews";
    private String accessLogCollection = "access_logs";

    /** Kept short so an absent server fails fast instead of stalling a request. */
    private long serverSelectionTimeoutMs = 1500;
}
