package database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DatabaseConnection Utility
 *
 * Manages JDBC connections to MySQL using DriverManager.
 * Reads configuration from environment variables first, falling back
 * to a local db.properties file excluded from version control.
 */
public class DatabaseConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/hostel_complaint_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String PROPERTIES_FILE = "db.properties";

    private static String dbUrl;
    private static String dbUsername;
    private static String dbPassword;
    private static boolean initialized = false;

    static {
        loadConfiguration();
    }

    /**
     * Loads database credentials from environment variables or db.properties.
     */
    public static synchronized void loadConfiguration() {
        // 1. Try Environment Variables first
        String envUrl = System.getenv("DB_URL");
        String envUser = System.getenv("DB_USERNAME");
        String envPass = System.getenv("DB_PASSWORD");

        Properties props = new Properties();
        File propFile = new File(PROPERTIES_FILE);
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("[DatabaseConnection] Warning: Could not read " + PROPERTIES_FILE + ": " + e.getMessage());
            }
        }

        dbUrl = (envUrl != null && !envUrl.trim().isEmpty())
                ? envUrl.trim()
                : props.getProperty("DB_URL", DEFAULT_URL).trim();

        dbUsername = (envUser != null && !envUser.trim().isEmpty())
                ? envUser.trim()
                : props.getProperty("DB_USERNAME", "root").trim();

        dbPassword = (envPass != null)
                ? envPass
                : props.getProperty("DB_PASSWORD", "");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            initialized = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseConnection] MySQL Driver not found on classpath: " + e.getMessage());
            initialized = false;
        }
    }

    /**
     * Obtains a new database connection using DriverManager.
     * Callers should use try-with-resources to ensure the connection is closed.
     *
     * @return an open java.sql.Connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            loadConfiguration();
        }
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    /**
     * Tests whether the database connection is currently working.
     *
     * @return true if connection succeeds and is valid, false otherwise.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the configured DB URL (safe for logging/diagnostics).
     */
    public static String getDbUrl() {
        return dbUrl;
    }

    /**
     * Get the configured DB Username (safe for logging/diagnostics).
     */
    public static String getDbUsername() {
        return dbUsername;
    }
}
