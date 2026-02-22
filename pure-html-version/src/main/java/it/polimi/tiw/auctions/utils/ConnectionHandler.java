package it.polimi.tiw.auctions.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

/**
 * Utility class for managing database connections.
 * This class provides static methods to obtain and close database connections.
 * Connection parameters (driver, URL, username, password) are retrieved from
 * the {@link ServletContext} (i.e., from {@code web.xml} initialization parameters).
 */
public class ConnectionHandler {

    /**
     * Retrieves a database connection using parameters defined in the {@code web.xml} (ServletContext).
     * It loads the specified JDBC driver and attempts to establish a connection to the database.
     *
     * @param context The {@link ServletContext} from which to read database initialization parameters
     *                (dbDriver, dbUrl, dbUser, dbPassword).
     * @return A {@link Connection} object to the database if successful.
     * @throws ServletException if database connection parameters are missing or incorrect in {@code web.xml},
     *                          if the database driver cannot be loaded, or if a connection cannot be established.
     */
    public static Connection getConnection(ServletContext context) throws ServletException {
        Connection connection = null; // Initialize connection to null.

        // Retrieve database connection parameters from ServletContext init-params.
        String dbDriver = context.getInitParameter("dbDriver");
        String dbUrl = context.getInitParameter("dbUrl");
        String dbUser = context.getInitParameter("dbUser");
        String dbPassword = context.getInitParameter("dbPassword");

        // Validate that all required parameters are present.
        if (dbDriver == null || dbUrl == null || dbUser == null || dbPassword == null) {
            throw new ServletException("Database connection parameters (dbDriver, dbUrl, dbUser, dbPassword) must be configured in web.xml.");
        }

        try {
            // Step 1: Load the JDBC driver class.
            // This registers the driver with DriverManager.
            Class.forName(dbDriver);
            // Step 2: Establish the connection to the database.
            // DriverManager uses the loaded driver that matches the dbUrl.
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (ClassNotFoundException e) {
            // If the driver class is not found, throw a ServletException.
            throw new ServletException("Database driver not found: " + dbDriver, e);
        } catch (SQLException e) {
            // If a connection cannot be established, throw a ServletException.
            throw new ServletException("Could not establish connection to the database at URL: " + dbUrl, e);
        }
        // Return the established connection.
        return connection;
    }

    /**
     * Closes the given database connection.
     * It checks if the connection is not null and not already closed before attempting to close it.
     * Any {@link SQLException} during closing is caught and logged to standard error,
     * but not re-thrown to avoid masking primary exceptions.
     *
     * @param connection The {@link Connection} object to close.
     */
    public static void closeConnection(Connection connection) {
        // Check if the connection object is valid.
        if (connection != null) {
            try {
                // Check if the connection is not already closed.
                if (!connection.isClosed()) {
                    // Close the connection.
                    connection.close();
                }
            } catch (SQLException e) {
                // Log an error message if closing fails.
                // In a real application, use a proper logging framework.
                System.err.println("Error closing database connection: " + e.getMessage());
                // Optionally, print stack trace for debugging, but avoid in production logs unless necessary.
                // e.printStackTrace();
            }
        }
    }
}