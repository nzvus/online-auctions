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
 * It is used in the RIA version to support user-related operations, often returning
 * data that will be serialized to JSON for client-side consumption.
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
     * Security Note: In a production environment, passwords should always be securely hashed and salted.
     * This example uses plain text passwords for simplicity and should not be used in production.
     *
     * @param username The username entered by the user.
     * @param password The password entered by the user.
     * @return A {@link User} object if the provided credentials match a user in the database;
     *         otherwise, returns {@code null}.
     * @throws SQLException if a database access error occurs during the query.
     */
    public User findUserByUsernameAndPassword(String username, String password) throws SQLException {
        // SQL query to select a user based on username and plain text password.
        String query = "SELECT id, username, password, name, surname, shippingAddress FROM user WHERE username = ? AND password = ?";
        // Use try-with-resources to ensure PreparedStatement is closed automatically.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the username and password parameters in the query.
            pStatement.setString(1, username);
            pStatement.setString(2, password); // Comparing plain text password.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // If a user with matching credentials was found (i.e., result.next() is true).
                if (result.next()) {
                    // Create and return a new User object populated with data from the ResultSet.
                    return new User(
                            result.getInt("id"),
                            result.getString("username"),
                            result.getString("password"), // Includes plain password in bean.
                            result.getString("name"),
                            result.getString("surname"),
                            result.getString("shippingAddress")
                    );
                }
            }
        }
        // If no user is found with the given credentials, or if an error occurs, return null.
        return null;
    }

    /**
     * Finds a user by their unique ID.
     * For security reasons, the password field in the returned {@link User} object is
     * masked (e.g., replaced with "********") as it's generally not needed or advisable
     * to transmit or display actual passwords after authentication.
     *
     * @param userId The ID of the user to find.
     * @return A {@link User} object if a user with the given ID is found (password will be masked);
     *         otherwise, returns {@code null}.
     * @throws SQLException if a database access error occurs during the query.
     */
    public User findUserById(int userId) throws SQLException {
        // SQL query to select user details by ID (excluding the actual password for security).
        String query = "SELECT id, username, name, surname, shippingAddress FROM user WHERE id = ?";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, userId); // Set the user ID parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // If a user is found, create and return User object with a masked password.
                if (result.next()) {
                    return new User(
                            result.getInt("id"),
                            result.getString("username"),
                            "********", // Password masked for security.
                            result.getString("name"),
                            result.getString("surname"),
                            result.getString("shippingAddress")
                    );
                }
            }
        }
        return null; // User not found.
    }

    /**
     * Checks if a username already exists in the database.
     * This is useful for validating new user registrations to ensure username uniqueness.
     *
     * @param username The username to check for existence.
     * @return {@code true} if the username already exists in the database, {@code false} otherwise.
     * @throws SQLException if a database access error occurs during the query.
     */
    public boolean usernameExists(String username) throws SQLException {
        // SQL query to count the number of users with the given username.
        String query = "SELECT COUNT(*) AS count FROM user WHERE username = ?";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setString(1, username); // Set the username parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // If a result is returned (it always should be for COUNT(*)).
                if (result.next()) {
                    // If the count is greater than 0, the username exists.
                    return result.getInt("count") > 0;
                }
            }
        }
        // Default to false if query fails or count is 0.
        return false;
    }
}