package it.polimi.tiw.auctions.dao;

import it.polimi.tiw.auctions.beans.User; // Import the User bean

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object (DAO) for managing {@link User} entities in the database.
 * This class provides methods for user authentication (checking credentials),
 * retrieving user details by ID, and checking for username existence.
 * It requires a {@link Connection} object to interact with the database.
 */
public class UserDAO {
    private Connection connection; // Database connection instance.

    /**
     * Constructs a UserDAO with the given database connection.
     *
     * @param connection The active database connection to be used for DAO operations.
     */
    public UserDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Finds a user by their username and password for authentication purposes.
     * Note: Passwords should be hashed in a real application; this example assumes plain text for simplicity.
     *
     * @param username The username to check.
     * @param password The password to check.
     * @return A {@link User} object if the credentials match a user in the database, otherwise {@code null}.
     * @throws SQLException if a database access error occurs.
     */
    public User findUserByUsernameAndPassword(String username, String password) throws SQLException {
        // SQL query to select a user by username and password.
        String query = "SELECT id, username, password, name, surname, shippingAddress FROM user WHERE username = ? AND password = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the username and password parameters in the query.
            pStatement.setString(1, username);
            pStatement.setString(2, password);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a user with matching credentials was found.
                if (result.next()) {
                    // Create and return a new User object from the ResultSet data.
                    return new User(
                            result.getInt("id"),
                            result.getString("username"),
                            result.getString("password"), // Storing plain password in bean, consider security implications.
                            result.getString("name"),
                            result.getString("surname"),
                            result.getString("shippingAddress")
                    );
                }
            }
        }
        // If no user is found with the given credentials, return null.
        return null;
    }

    /**
     * Finds a user by their unique ID.
     * The password field in the returned {@link User} object is masked (e.g., with "********")
     * for security, as it's generally not needed when retrieving user details by ID for display purposes.
     *
     * @param userId The ID of the user to find.
     * @return A {@link User} object if found (with masked password), otherwise {@code null}.
     * @throws SQLException if a database access error occurs.
     */
    public User findUserById(int userId) throws SQLException {
        // SQL query to select a user by ID. Password is not selected or is ignored.
        String query = "SELECT id, username, name, surname, shippingAddress FROM user WHERE id = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the user ID parameter.
            pStatement.setInt(1, userId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a user was found.
                if (result.next()) {
                    // Create and return a new User object.
                    // Password is set to a placeholder for security as it's not typically needed here.
                    return new User(
                            result.getInt("id"),
                            result.getString("username"),
                            "********", // Masked password
                            result.getString("name"),
                            result.getString("surname"),
                            result.getString("shippingAddress")
                    );
                }
            }
        }
        // If no user is found with the given ID, return null.
        return null;
    }

    /**
     * Checks if a username already exists in the database.
     * This is useful for validating new user registrations to ensure username uniqueness.
     *
     * @param username The username to check for existence.
     * @return {@code true} if the username exists, {@code false} otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean usernameExists(String username) throws SQLException {
        // SQL query to count occurrences of a username.
        String query = "SELECT COUNT(*) AS count FROM user WHERE username = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the username parameter.
            pStatement.setString(1, username);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a result was returned.
                if (result.next()) {
                    // Return true if the count is greater than 0, indicating the username exists.
                    return result.getInt("count") > 0;
                }
            }
        }
        // If no result or count is 0, the username does not exist.
        return false;
    }
}