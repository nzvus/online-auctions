package it.polimi.tiw.auctions.dao;

import it.polimi.tiw.auctions.beans.Auction; // Import the Auction bean

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for managing {@link Auction} entities in the database.
 * This class provides methods to perform CRUD (Create, Read, Update, Delete) operations
 * related to auctions, such as creating new auctions, finding existing ones by various criteria,
 * and updating their status (e.g., closing an auction).
 * It requires a {@link Connection} object to interact with the database.
 */
public class AuctionDAO {
    private Connection connection; // Database connection instance.

    /**
     * Constructs an AuctionDAO with the given database connection.
     *
     * @param connection The active database connection to be used for DAO operations.
     */
    public AuctionDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Creates a new auction in the database and links its associated items.
     * This operation is transactional, ensuring that both the auction and its item links
     * are created, or neither is if an error occurs.
     * The auction's initial price must be pre-calculated by the caller.
     * The auction's creation timestamp should also be set by the caller (e.g., to the current time).
     *
     * @param auction The {@link Auction} object to be created. The ID field is ignored as it's auto-generated.
     * @param itemIds A list of integer IDs for the items to be included in this auction.
     * @return The auto-generated ID of the newly created auction.
     * @throws SQLException if a database access error occurs or if the auction cannot be created (e.g., due to constraint violations).
     * @throws IllegalArgumentException if {@code itemIds} is null or empty.
     */
    public int createAuction(Auction auction, List<Integer> itemIds) throws SQLException {
        // Validate that itemIds is not null or empty, as an auction must have at least one item.
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Auction must contain at least one item.");
        }

        // SQL query to insert a new auction into the 'auction' table.
        String auctionQuery = "INSERT INTO auction (initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status) " +
                              "VALUES (?, ?, ?, ?, ?, ?)";
        // SQL query to link items to the auction in the 'auction_item_link' table.
        String linkQuery = "INSERT INTO auction_item_link (auction_id, item_id) VALUES (?, ?)";
        int generatedAuctionId = -1; // Variable to store the auto-generated ID of the new auction.

        // Preserve the original auto-commit state of the connection.
        boolean originalAutoCommit = connection.getAutoCommit();
        // Disable auto-commit to manage the transaction manually.
        connection.setAutoCommit(false);

        try {
            // Step 1: Insert the auction details into the 'auction' table.
            // Use try-with-resources to ensure PreparedStatement is closed.
            try (PreparedStatement psAuction = connection.prepareStatement(auctionQuery, Statement.RETURN_GENERATED_KEYS)) {
                // Set the parameters for the auction insertion query.
                psAuction.setDouble(1, auction.initialPrice());
                psAuction.setDouble(2, auction.minimumBidIncrement());
                psAuction.setTimestamp(3, auction.deadline());
                psAuction.setTimestamp(4, auction.creationTimestamp());
                psAuction.setInt(5, auction.creatorUserId());
                psAuction.setString(6, auction.status().getStatusValue()); // Persist status as its string value.

                // Execute the update and get the number of affected rows.
                int affectedRows = psAuction.executeUpdate();
                // If no rows were affected, the auction creation failed.
                if (affectedRows == 0) {
                    throw new SQLException("Creating auction failed, no rows affected for auction table.");
                }

                // Retrieve the auto-generated ID for the new auction.
                // Use try-with-resources for ResultSet.
                try (ResultSet rs = psAuction.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedAuctionId = rs.getInt(1); // Get the first generated key.
                    } else {
                        throw new SQLException("Creating auction failed, no ID obtained for auction.");
                    }
                }
            }

            // Step 2: Insert the links between the auction and its items into 'auction_item_link'.
            // Proceed only if a valid auction ID was generated.
            if (generatedAuctionId > 0) {
                // Use try-with-resources for PreparedStatement.
                try (PreparedStatement psLink = connection.prepareStatement(linkQuery)) {
                    // Iterate over the list of item IDs to be linked.
                    for (Integer itemId : itemIds) {
                        // Set parameters for the link insertion query.
                        psLink.setInt(1, generatedAuctionId);
                        psLink.setInt(2, itemId);
                        // Add the current statement to the batch for efficient execution.
                        psLink.addBatch();
                    }
                    // Execute all statements in the batch.
                    psLink.executeBatch();
                }
            } else {
                 // This should ideally not be reached if the previous ID generation check worked.
                 throw new SQLException("Generated Auction ID is invalid, cannot link items.");
            }

            // If both insertions were successful, commit the transaction.
            connection.commit();
        } catch (SQLException e) {
            // If any SQL error occurs, roll back the transaction to maintain data integrity.
            connection.rollback();
            // Re-throw the exception to notify the caller.
            throw new SQLException("Error during auction creation transaction: " + e.getMessage(), e);
        } finally {
            // Restore the original auto-commit state of the connection.
            connection.setAutoCommit(originalAutoCommit);
        }
        // Return the ID of the newly created auction.
        return generatedAuctionId;
    }

    /**
     * Finds an auction by its unique ID.
     *
     * @param auctionId The ID of the auction to find.
     * @return An {@link Auction} object if found, otherwise {@code null}.
     * @throws SQLException if a database access error occurs.
     */
    public Auction findAuctionById(int auctionId) throws SQLException {
        // SQL query to select an auction by its ID.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE id = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the auction ID parameter in the query.
            pStatement.setInt(1, auctionId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a result was found.
                if (result.next()) {
                    // Retrieve winnerId and winningPrice, handling potential nulls from the database.
                    Integer winnerId = result.getObject("winnerUserId", Integer.class);
                    Double winningPrice = result.getObject("winningPrice", Double.class);
                    // Create and return a new Auction object from the ResultSet data.
                    return new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.fromString(result.getString("status")), // Convert string status to enum.
                            winnerId,
                            winningPrice
                    );
                }
            }
        }
        // If no auction is found with the given ID, return null.
        return null;
    }

    /**
     * Finds all auctions created by a specific user, filtered by a given status.
     * Results are ordered by creation timestamp in ascending order (oldest first),
     * as typically required for displaying auctions on a "Sell" page.
     *
     * @param creatorUserId The ID of the user who created the auctions.
     * @param auctionStatus The {@link Auction.AuctionStatus} to filter by (e.g., OPEN, CLOSED).
     * @return A list of {@link Auction} objects matching the criteria. The list will be empty if no auctions are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findAuctionsByCreatorAndStatus(int creatorUserId, Auction.AuctionStatus auctionStatus) throws SQLException {
        List<Auction> auctions = new ArrayList<>(); // Initialize an empty list to store results.
        // SQL query to select auctions by creator ID and status, ordered by creation time.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE creatorUserId = ? AND status = ? ORDER BY creationTimestamp ASC";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the parameters for the query.
            pStatement.setInt(1, creatorUserId);
            pStatement.setString(2, auctionStatus.getStatusValue()); // Use the string value of the enum for the query.
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                    // For each row, create an Auction object and add it to the list.
                    auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.fromString(result.getString("status")), // Convert string status to enum.
                            result.getObject("winnerUserId", Integer.class), // Handle possible nulls.
                            result.getObject("winningPrice", Double.class)   // Handle possible nulls.
                    ));
                }
            }
        }
        // Return the list of found auctions.
        return auctions;
    }

    /**
     * Finds open auctions where at least one of their associated items' name or description
     * contains the given keyword. Results are ordered by deadline in ascending order
     * (auctions ending soonest appear first).
     * This is typically used for the "Buy" page search functionality.
     *
     * @param keyword The keyword to search for in item names and descriptions.
     * @return A list of open {@link Auction} objects matching the keyword. The list will be empty if no matches are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findOpenAuctionsByKeyword(String keyword) throws SQLException {
        List<Auction> auctions = new ArrayList<>(); // Initialize an empty list.
        // SQL query to find open auctions by keyword in item details.
        // It joins 'auction', 'auction_item_link', and 'item' tables.
        // DISTINCT ensures that auctions with multiple matching items are listed only once.
        // Filters for OPEN status and deadline in the future.
        String query = "SELECT DISTINCT a.id, a.initialPrice, a.minimumBidIncrement, a.deadline, a.creationTimestamp, a.creatorUserId, a.status, a.winnerUserId, a.winningPrice " +
                       "FROM auction a " +
                       "JOIN auction_item_link ail ON a.id = ail.auction_id " +
                       "JOIN item i ON ail.item_id = i.id " +
                       "WHERE a.status = ? AND a.deadline > NOW() " + // Filter for open auctions with future deadlines.
                       "AND (i.name LIKE ? OR i.description LIKE ?) " + // Keyword search in item name or description.
                       "ORDER BY a.deadline ASC"; // Order by closest deadline.
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set parameters for the query.
            pStatement.setString(1, Auction.AuctionStatus.OPEN.getStatusValue()); // Status 'open'.
            String searchPattern = "%" + keyword + "%"; // Create a LIKE pattern for the keyword.
            pStatement.setString(2, searchPattern); // For item name.
            pStatement.setString(3, searchPattern); // For item description.
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                    // For each row, create an Auction object and add it to the list.
                     auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.OPEN, // Status is known to be OPEN from the query.
                            result.getObject("winnerUserId", Integer.class), // Should be null for open auctions.
                            result.getObject("winningPrice", Double.class)  // Should be null for open auctions.
                    ));
                }
            }
        }
        // Return the list of found auctions.
        return auctions;
    }

    /**
     * Finds all auctions won by a specific user.
     * These are auctions that are CLOSED and have the given user as the winner.
     * Results are ordered by deadline in descending order (most recently ended first).
     *
     * @param userId The ID of the user whose won auctions are to be retrieved.
     * @return A list of {@link Auction} objects won by the user. The list is empty if none are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findWonAuctionsByUser(int userId) throws SQLException {
        List<Auction> auctions = new ArrayList<>(); // Initialize an empty list.
        // SQL query to select auctions won by a user.
        // Filters for the specific winnerUserId and CLOSED status.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE winnerUserId = ? AND status = ? ORDER BY deadline DESC";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set parameters for the query.
            pStatement.setInt(1, userId);
            pStatement.setString(2, Auction.AuctionStatus.CLOSED.getStatusValue()); // Status 'closed'.
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                    // For each row, create an Auction object and add it to the list.
                    auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.CLOSED, // Status is known to be CLOSED from the query.
                            result.getInt("winnerUserId"), // Winner ID is known.
                            result.getDouble("winningPrice") // Winning price is known.
                    ));
                }
            }
        }
        // Return the list of won auctions.
        return auctions;
    }

    /**
     * Closes an auction by updating its status to CLOSED and setting the winner and winning price.
     * This operation ensures that the auction is currently OPEN and its deadline has passed.
     *
     * @param auctionId The ID of the auction to close.
     * @param winnerUserId The ID of the winning user. Can be {@code null} if there were no bids or no winner.
     * @param winningPrice The final price at which the auction was won. Can be {@code null} if no winner.
     * @param currentTimestamp The current time, used to verify that the auction's deadline has indeed passed.
     * @return The number of rows affected by the update operation (should be 1 if successful, 0 otherwise).
     * @throws SQLException if a database access error occurs.
     */
    public int closeAuction(int auctionId, Integer winnerUserId, Double winningPrice, Timestamp currentTimestamp) throws SQLException {
        // SQL query to update an auction to 'closed' status and set winner details.
        // It includes conditions to ensure the auction is 'open' and its deadline has passed.
        String query = "UPDATE auction SET status = ?, winnerUserId = ?, winningPrice = ? " +
                       "WHERE id = ? AND status = ? AND deadline <= ?"; // Ensure deadline has passed.
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the new status to 'closed'.
            pStatement.setString(1, Auction.AuctionStatus.CLOSED.getStatusValue());
            // Set the winner user ID. If null, set SQL NULL.
            if (winnerUserId != null) {
                pStatement.setInt(2, winnerUserId);
            } else {
                pStatement.setNull(2, java.sql.Types.INTEGER);
            }
            // Set the winning price. If null, set SQL NULL.
            if (winningPrice != null) {
                pStatement.setDouble(3, winningPrice);
            } else {
                pStatement.setNull(3, java.sql.Types.DECIMAL);
            }
            // Set the auction ID to identify the auction to update.
            pStatement.setInt(4, auctionId);
            // Ensure the auction is currently 'open'.
            pStatement.setString(5, Auction.AuctionStatus.OPEN.getStatusValue());
            // Ensure the deadline has passed using the provided current timestamp.
            pStatement.setTimestamp(6, currentTimestamp);

            // Execute the update and return the number of affected rows.
            return pStatement.executeUpdate();
        }
    }
    
    /**
     * Finds open auctions by a list of their IDs.
     * This is useful for the RIA version where the client might store a list of
     * previously visited auction IDs and request their current status.
     * Auctions are ordered by deadline ascending.
     *
     * @param auctionIds A list of auction IDs to retrieve.
     * @return A list of {@link Auction} objects that are currently open and match the provided IDs.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findOpenAuctionsByIds(List<Integer> auctionIds) throws SQLException {
        List<Auction> auctions = new ArrayList<>(); // Initialize an empty list.
        // If the list of IDs is null or empty, return an empty list immediately.
        if (auctionIds == null || auctionIds.isEmpty()) {
            return auctions;
        }
        
        // Dynamically create the correct number of placeholders (?) for the IN clause.
        // Example: if auctionIds has 3 elements, placeholders will be "?,?,?".
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < auctionIds.size(); i++) {
            placeholders.append("?");
            if (i < auctionIds.size() - 1) {
                placeholders.append(",");
            }
        }
        
        // SQL query to select auctions by a list of IDs, filtering for OPEN status and future deadlines.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE id IN (" + placeholders.toString() + ") AND status = ? AND deadline > NOW() " +
                       "ORDER BY deadline ASC"; // Consistent ordering.
        
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            int index = 1; // Start index for setting parameters.
            // Set each auction ID in the prepared statement.
            for (Integer id : auctionIds) {
                pStatement.setInt(index++, id);
            }
            // Set the status parameter to 'open'.
            pStatement.setString(index, Auction.AuctionStatus.OPEN.getStatusValue());
            
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                     // For each row, create an Auction object and add it to the list.
                     auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.OPEN, // Status is known to be OPEN.
                            result.getObject("winnerUserId", Integer.class), // Should be null.
                            result.getObject("winningPrice", Double.class)  // Should be null.
                    ));
                }
            }
        }
        // Return the list of found auctions.
        return auctions;
    }
}