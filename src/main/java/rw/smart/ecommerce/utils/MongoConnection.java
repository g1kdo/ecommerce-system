package rw.smart.ecommerce.utils;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Connection holder for the NoSQL half of the hybrid model (see nosql/).
 *
 * The document store is treated as optional infrastructure: the client is built
 * lazily and never during class initialization, so an unreachable MongoDB cannot
 * stop the application from starting. Callers that need to tell the user whether
 * documents are reachable use {@link #isAvailable()}; everything else simply lets
 * the driver's own exception surface and degrades.
 */
public final class MongoConnection {

    public static final String REVIEW_CONTENT_COLLECTION = "review_content";
    public static final String LOGS_COLLECTION = "logs";

    private static final String URI_PROPERTY = "mongo.uri";
    private static final String DATABASE_PROPERTY = "mongo.database";

    /** Kept short so a missing server fails fast instead of stalling the UI. */
    private static final long SERVER_SELECTION_TIMEOUT_MS = 1_200;
    /** How long an availability answer is reused before pinging again. */
    private static final long AVAILABILITY_CACHE_MS = 10_000;

    private static MongoClient client;
    private static MongoDatabase database;
    private static Boolean lastAvailability;
    private static long lastAvailabilityCheckAt;

    private MongoConnection() {
        // utility class, no instances
    }

    public static synchronized MongoDatabase getDatabase() {
        if (database == null) {
            Properties config = DBConnection.loadConfiguration();
            String uri = requiredProperty(config, URI_PROPERTY);
            String databaseName = requiredProperty(config, DATABASE_PROPERTY);

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .applyToClusterSettings(builder ->
                            builder.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .build();

            client = MongoClients.create(settings);
            database = client.getDatabase(databaseName);
        }
        return database;
    }

    public static MongoCollection<Document> collection(String name) {
        return getDatabase().getCollection(name);
    }

    /**
     * Pings the server, reusing the previous answer for a few seconds so screens
     * can ask freely without paying the round trip every time.
     */
    public static synchronized boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (lastAvailability != null && now - lastAvailabilityCheckAt < AVAILABILITY_CACHE_MS) {
            return lastAvailability;
        }

        try {
            getDatabase().runCommand(new Document("ping", 1));
            lastAvailability = true;
        } catch (RuntimeException e) {
            lastAvailability = false;
        }
        lastAvailabilityCheckAt = now;
        return lastAvailability;
    }

    /**
     * The connection details live only in db.properties — there is no built-in
     * fallback host or database name, so the app can never silently talk to a
     * different store than the one that was configured. A missing key surfaces
     * lazily (on first document use) and is reported by {@link #isAvailable()} the
     * same way an unreachable server is.
     */
    private static String requiredProperty(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalStateException("Missing required document store property: " + key);

        return value.trim();
    }

    public static synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
            database = null;
            lastAvailability = null;
        }
    }
}
