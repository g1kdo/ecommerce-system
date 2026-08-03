package rw.smart.ecommerce.utils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {
    private static final String CONFIG_RESOURCE = "/db.properties";
    private static final Properties CONFIG = loadConfiguration();
    private static final String URL = requiredProperty("db.url");
    private static final String USER = requiredProperty("db.username");
    private static final String PASSWORD = requiredProperty("db.password");
    private static final String DRIVER = optionalProperty("db.url");


    static {
        if (DRIVER != null && !DRIVER.isBlank()) {
            try {
                Class.forName(DRIVER.trim());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Configured JDBC driver class was not found: " + DRIVER, e);
            }
        }
    }

    private DBConnection() {
        // utility class, no instances
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static Properties loadConfiguration() {
        Properties properties = new Properties();
        try (InputStream inputStream = DBConnection.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (inputStream == null) throw  new IllegalArgumentException("Missing configuration file: " + CONFIG_RESOURCE);

            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load database configuration from " + CONFIG_RESOURCE, e);
        }
    }

    private static String requiredProperty(String key) {
        String value = CONFIG.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required database property: " + key);

        return value.trim();
    }

    private static String optionalProperty(String key) {
        String value = CONFIG.getProperty(key);
        return value == null ? null : value.trim();
    }
}
