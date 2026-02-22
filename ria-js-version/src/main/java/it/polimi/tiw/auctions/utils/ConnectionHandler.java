package it.polimi.tiw.auctions.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

/**
 * Utility class for managing database connections within the web application.
 * It provides static methods to obtain a new database connection based on
 * configuration parameters stored in the {@link ServletContext} (typically from {@code web.xml}),
 * and to safely close connections.
 */
public class ConnectionHandler {

    /**
     * Retrieves a database connection using parameters defined in the {@code web.xml} (ServletContext).
     * This method loads the specified JDBC driver and attempts to establish a connection.
     *
     * @param context The {@link ServletContext} from which database initialization parameters
     *                (dbDriver, dbUrl, dbUser, dbPassword) are read.
     * @return A new {@link Connection} object to the database if successful.
     * @throws ServletException if database connection parameters are missing/incorrect in {@code web.xml},
     *                          if the database driver cannot be loaded, or if a connection cannot be established.
     */
    public static Connection getConnection(ServletContext context) throws ServletException {
        Connection connection = null; // Will hold the database connection.

        // Step 1: Retrieve database connection parameters from web.xml via ServletContext.
        String dbDriver = context.getInitParameter("dbDriver");
        String dbUrl = context.getInitParameter("dbUrl");
        String dbUser = context.getInitParameter("dbUser");
        String dbPassword = context.getInitParameter("dbPassword");

        // Step 2: Validate that all necessary parameters were found.
        if (dbDriver == null || dbUrl == null || dbUser == null || dbPassword == null) {
            throw new ServletException("Database connection parameters (dbDriver, dbUrl, dbUser, dbPassword) must be configured in web.xml.");
        }

        try {
            // Step 3: Load the JDBC driver class.
            // This step registers the driver with the DriverManager.
            Class.forName(dbDriver);
            // Step 4: Establish the connection to the database using DriverManager.
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (ClassNotFoundException e) {
            // Handle case where the JDBC driver class is not found.
            throw new ServletException("Database driver not found: " + dbDriver, e);
        } catch (SQLException e) {
            // Handle errors during database connection establishment.
            throw new ServletException("Could not establish connection to the database at URL: " + dbUrl + ". Error: " + e.getMessage(), e);
        }
        // Step 5: Return the successfully established connection.
        return connection;
    }

    /**
     * Closes the given database connection.
     * This method checks if the connection is not null and not already closed before attempting to close it.
     * Any {@link SQLException} occurring during the close operation is caught and logged to standard error,
     * but not re-thrown, to prevent masking a primary exception that might have occurred earlier.
     *
     * @param connection The {@link Connection} object to be closed. Can be null.
     */
    public static void closeConnection(Connection connection) {
        // Proceed only if the connection object is not null.
        if (connection != null) {
            try {
                // Check if the connection is not already closed before trying to close it.
                if (!connection.isClosed()) {
                    connection.close(); // Close the database connection.
                }
            } catch (SQLException e) {
                // Log an error if closing the connection fails.
                // In a production environment, a proper logging framework should be used.
                System.err.println("Error closing database connection: " + e.getMessage());
                // e.printStackTrace(); // Consider for debugging, but avoid in production logs if too verbose.
            }
        }
    }
}