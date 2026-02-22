package it.polimi.tiw.auctions.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import it.polimi.tiw.auctions.beans.Bid; // Import the Bid bean

/**
 * Data Access Object (DAO) for managing {@link Bid} entities in the database.
 * This class provides methods for creating bids and retrieving bids associated with auctions.
 * It requires a {@link Connection} object to interact with the database.
 */
public class BidDAO {
    private Connection connection; // Database connection instance.

    /**
     * Constructs a BidDAO with the given database connection.
     *
     * @param connection The active database connection to be used for DAO operations.
     */
    public BidDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Creates a new bid in the database.
     * The bid's timestamp should typically be set to the current time by the caller.
     * Additional business logic validations (e.g., auction openness, bid amount validity against current highest bid)
     * are assumed to be handled by a service layer or controller before calling this method.
     *
     * @param bid The {@link Bid} object to create. The ID field is ignored as it's auto-generated.
     * @return The auto-generated ID of the newly created bid.
     * @throws SQLException if a database access error occurs or if bid creation fails.
     */
    public int createBid(Bid bid) throws SQLException {
        // SQL query to insert a new bid into the 'bid' table.
        String query = "INSERT INTO bid (auctionId, userId, amount, timestamp) VALUES (?, ?, ?, ?)";
        int generatedId = -1; // Variable to store the auto-generated ID.
        // Use try-with-resources for PreparedStatement, requesting generated keys.
        try (PreparedStatement pStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            // Set the parameters for the bid insertion query.
            pStatement.setInt(1, bid.auctionId());
            pStatement.setInt(2, bid.userId());
            pStatement.setDouble(3, bid.amount());
            pStatement.setTimestamp(4, bid.timestamp());

            // Execute the update and get the number of affected rows.
            int affectedRows = pStatement.executeUpdate();
            // If no rows were affected, the bid creation failed.
            if (affectedRows == 0) {
                throw new SQLException("Creating bid failed, no rows affected.");
            }

            // Retrieve the auto-generated ID for the new bid.
            // Use try-with-resources for ResultSet.
            try (ResultSet rs = pStatement.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getInt(1); // Get the first generated key.
                } else {
                    throw new SQLException("Creating bid failed, no ID obtained.");
                }
            }
        }
        // Return the ID of the newly created bid.
        return generatedId;
    }

    /**
     * Finds all bids for a specific auction, ordered by timestamp in descending order (most recent first).
     * This method also joins with the 'user' table to fetch the username of the bidder, although
     * the {@link Bid} bean itself does not store the username. The calling servlet or service
     * might need to handle this additional information if required for display.
     *
     * @param auctionId The ID of the auction for which to retrieve bids.
     * @return A list of {@link Bid} objects. The list will be empty if no bids are found for the auction.
     * @throws SQLException if a database access error occurs.
     */
    public List<Bid> findBidsByAuctionId(int auctionId) throws SQLException {
        List<Bid> bids = new ArrayList<>(); // Initialize an empty list to store bids.
        // SQL query to select bids for an auction, joining with user to get username.
        // Ordered by timestamp descending as per specification ("lista delle offerte ... ordinata per data+ora decrescente").
        String query = "SELECT b.id, b.auctionId, b.userId, b.amount, b.timestamp, u.username " + // Username is fetched but not part of Bid bean.
                       "FROM bid b JOIN user u ON b.userId = u.id " +
                       "WHERE b.auctionId = ? ORDER BY b.timestamp DESC";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the auction ID parameter.
            pStatement.setInt(1, auctionId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                    // For each row, create a Bid object and add it to the list.
                    // The username (result.getString("username")) is fetched but not directly stored in the Bid record.
                    // The caller might need to create a DTO or handle this separately if the username is needed alongside the bid.
                    bids.add(new Bid(
                            result.getInt("id"),
                            result.getInt("auctionId"),
                            result.getInt("userId"),
                            result.getDouble("amount"),
                            result.getTimestamp("timestamp")
                    ));
                }
            }
        }
        // Return the list of found bids.
        return bids;
    }

    /**
     * Finds the highest bid for a specific auction.
     * If multiple bids have the same highest amount, the one placed earliest (ASC timestamp) is chosen.
     *
     * @param auctionId The ID of the auction.
     * @return The {@link Bid} object representing the highest bid, or {@code null} if no bids exist for the auction.
     * @throws SQLException if a database access error occurs.
     */
    public Bid findHighestBidForAuction(int auctionId) throws SQLException {
        // SQL query to find the highest bid for an auction.
        // Orders by amount descending, then by timestamp ascending to break ties (earliest bid wins).
        // LIMIT 1 ensures only the single highest bid is returned.
        String query = "SELECT id, auctionId, userId, amount, timestamp FROM bid " +
                       "WHERE auctionId = ? ORDER BY amount DESC, timestamp ASC LIMIT 1";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the auction ID parameter.
            pStatement.setInt(1, auctionId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a result was found.
                if (result.next()) {
                    // Create and return a Bid object from the ResultSet data.
                    return new Bid(
                            result.getInt("id"),
                            result.getInt("auctionId"),
                            result.getInt("userId"),
                            result.getDouble("amount"),
                            result.getTimestamp("timestamp")
                    );
                }
            }
        }
        // If no bids are found for the auction, return null.
        return null;
    }
}