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
 * Used in the RIA version to support bidding functionality and display bid history.
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
     * Business logic validations (e.g., auction status, bid amount rules) are assumed
     * to be handled by the service layer or controller before this method is called.
     *
     * @param bid The {@link Bid} object to create. Its ID field is ignored (auto-generated).
     * @return The auto-generated ID of the newly created bid.
     * @throws SQLException if a database access error occurs or if bid creation fails.
     */
    public int createBid(Bid bid) throws SQLException {
        // SQL query for inserting a new bid.
        String query = "INSERT INTO bid (auctionId, userId, amount, timestamp) VALUES (?, ?, ?, ?)";
        int generatedId = -1; // To store the auto-generated ID.
        // Use try-with-resources, requesting RETURN_GENERATED_KEYS.
        try (PreparedStatement pStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            // Set parameters for the bid insertion.
            pStatement.setInt(1, bid.auctionId());
            pStatement.setInt(2, bid.userId());
            pStatement.setDouble(3, bid.amount());
            pStatement.setTimestamp(4, bid.timestamp());

            // Execute the update and check if successful.
            int affectedRows = pStatement.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating bid failed, no rows affected.");
            }

            // Retrieve the auto-generated ID.
            try (ResultSet rs = pStatement.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getInt(1);
                } else {
                    throw new SQLException("Creating bid failed, no ID obtained.");
                }
            }
        }
        // Return the new bid's ID.
        return generatedId;
    }

    /**
     * Finds all bids for a specific auction, ordered by timestamp in descending order (most recent first).
     * The query joins with the 'user' table to fetch the bidder's username, although this
     * is not part of the {@link Bid} bean itself. Client-side logic or a DTO would handle
     * the username if needed for display.
     *
     * @param auctionId The ID of the auction for which to retrieve bids.
     * @return A list of {@link Bid} objects. The list is empty if no bids are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Bid> findBidsByAuctionId(int auctionId) throws SQLException {
        List<Bid> bids = new ArrayList<>(); // Initialize an empty list for bids.
        // SQL query to get bids for an auction, ordered by timestamp (descending).
        // Includes username from the user table.
        String query = "SELECT b.id, b.auctionId, b.userId, b.amount, b.timestamp, u.username " +
                       "FROM bid b JOIN user u ON b.userId = u.id " +
                       "WHERE b.auctionId = ? ORDER BY b.timestamp DESC";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, auctionId); // Set the auction ID parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate through the results and map each row to a Bid object.
                while (result.next()) {
                    bids.add(new Bid(
                            result.getInt("id"),
                            result.getInt("auctionId"),
                            result.getInt("userId"),
                            result.getDouble("amount"),
                            result.getTimestamp("timestamp")
                            // Username (result.getString("username")) is fetched but not part of Bid bean.
                    ));
                }
            }
        }
        return bids; // Return the list of bids.
    }

    /**
     * Finds the highest bid for a specific auction.
     * If multiple bids have the same highest amount, the one placed earliest (ascending timestamp) is chosen.
     *
     * @param auctionId The ID of the auction.
     * @return The {@link Bid} object representing the highest bid, or {@code null} if no bids exist for the auction.
     * @throws SQLException if a database access error occurs.
     */
    public Bid findHighestBidForAuction(int auctionId) throws SQLException {
        // SQL query to find the highest bid, resolving ties by earliest timestamp.
        String query = "SELECT id, auctionId, userId, amount, timestamp FROM bid " +
                       "WHERE auctionId = ? ORDER BY amount DESC, timestamp ASC LIMIT 1";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, auctionId); // Set the auction ID parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // If a highest bid is found, map it to a Bid object.
                if (result.next()) {
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
        return null; // Return null if no bids are found.
    }
}