package it.polimi.tiw.auctions.dao;

import it.polimi.tiw.auctions.beans.Auction; // Import the Auction bean

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections; // For Collections.nCopies, used in findOpenAuctionsByIds
import java.util.List;

/**
 * Data Access Object (DAO) for managing {@link Auction} entities in the database.
 * This class provides methods to perform CRUD (Create, Read, Update, Delete) operations
 * related to auctions. It is designed to be used by servlets in the RIA version of the application,
 * often returning data that will be serialized to JSON for client-side consumption.
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
     * This operation is transactional, ensuring atomicity: either all operations succeed,
     * or none are persisted if an error occurs.
     * The auction's initial price and creation timestamp should be set by the caller.
     *
     * @param auction The {@link Auction} object to be created. Its ID field is ignored (auto-generated).
     * @param itemIds A list of integer IDs for the items to be included in this auction.
     * @return The auto-generated ID of the newly created auction.
     * @throws SQLException if a database access error occurs or if the auction/link creation fails.
     * @throws IllegalArgumentException if {@code itemIds} is null or empty, as an auction must have items.
     */
    public int createAuction(Auction auction, List<Integer> itemIds) throws SQLException {
        // Step 1: Validate input parameters.
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Auction must contain at least one item.");
        }

        // Step 2: Define SQL queries for inserting auction and linking items.
        String auctionQuery = "INSERT INTO auction (initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status) " +
                              "VALUES (?, ?, ?, ?, ?, ?)";
        String linkQuery = "INSERT INTO auction_item_link (auction_id, item_id) VALUES (?, ?)";
        int generatedAuctionId = -1; // Initialize to an invalid ID.

        // Step 3: Manage database transaction manually.
        boolean originalAutoCommit = connection.getAutoCommit(); // Store original auto-commit state.
        connection.setAutoCommit(false); // Disable auto-commit for transactional integrity.

        try {
            // Step 3a: Insert the auction record.
            // Use try-with-resources to ensure PreparedStatement is closed automatically.
            try (PreparedStatement psAuction = connection.prepareStatement(auctionQuery, Statement.RETURN_GENERATED_KEYS)) {
                // Populate the PreparedStatement with auction data.
                psAuction.setDouble(1, auction.initialPrice());
                psAuction.setDouble(2, auction.minimumBidIncrement());
                psAuction.setTimestamp(3, auction.deadline());
                psAuction.setTimestamp(4, auction.creationTimestamp());
                psAuction.setInt(5, auction.creatorUserId());
                psAuction.setString(6, auction.status().getStatusValue()); // Persist enum as its string value.

                // Execute the insert and check if it was successful.
                int affectedRows = psAuction.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating auction failed, no rows affected for auction table.");
                }

                // Retrieve the auto-generated ID of the inserted auction.
                try (ResultSet rs = psAuction.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedAuctionId = rs.getInt(1);
                    } else {
                        throw new SQLException("Creating auction failed, no ID obtained for auction.");
                    }
                }
            }

            // Step 3b: Link items to the auction if auction insertion was successful.
            if (generatedAuctionId > 0) {
                try (PreparedStatement psLink = connection.prepareStatement(linkQuery)) {
                    // Batch insert item links for efficiency.
                    for (Integer itemId : itemIds) {
                        psLink.setInt(1, generatedAuctionId);
                        psLink.setInt(2, itemId);
                        psLink.addBatch(); // Add current item link to the batch.
                    }
                    psLink.executeBatch(); // Execute all batched item link insertions.
                }
            } else {
                 // This should not be reached if ID generation was successful.
                 throw new SQLException("Generated Auction ID is invalid, cannot link items.");
            }

            // Step 3c: If all operations succeeded, commit the transaction.
            connection.commit();
        } catch (SQLException e) {
            // Step 3d: If any error occurred, roll back the transaction.
            connection.rollback();
            // Re-throw the exception to inform the caller of the failure.
            throw new SQLException("Error during auction creation transaction: " + e.getMessage(), e);
        } finally {
            // Step 3e: Restore the original auto-commit state of the connection.
            connection.setAutoCommit(originalAutoCommit);
        }
        // Step 4: Return the ID of the newly created auction.
        return generatedAuctionId;
    }

    /**
     * Finds an auction by its unique ID.
     *
     * @param auctionId The ID of the auction to retrieve.
     * @return An {@link Auction} object if an auction with the given ID is found, otherwise {@code null}.
     * @throws SQLException if a database access error occurs.
     */
    public Auction findAuctionById(int auctionId) throws SQLException {
        // SQL query to select all columns for an auction with a specific ID.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE id = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, auctionId); // Set the auction ID parameter.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // If a result is found, map it to an Auction object.
                if (result.next()) {
                    Integer winnerId = result.getObject("winnerUserId", Integer.class); // Handle potential NULL.
                    Double winningPrice = result.getObject("winningPrice", Double.class); // Handle potential NULL.
                    return new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.fromString(result.getString("status")), // Convert status string to enum.
                            winnerId,
                            winningPrice
                    );
                }
            }
        }
        return null; // Return null if no auction is found.
    }

    /**
     * Finds all auctions created by a specific user, filtered by a given status.
     * Results are ordered by creation timestamp in ascending order (oldest auctions first).
     *
     * @param creatorUserId The ID of the user whose created auctions are to be retrieved.
     * @param auctionStatus The {@link Auction.AuctionStatus} to filter the auctions by (e.g., OPEN, CLOSED).
     * @return A list of {@link Auction} objects matching the criteria. The list will be empty if no such auctions are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findAuctionsByCreatorAndStatus(int creatorUserId, Auction.AuctionStatus auctionStatus) throws SQLException {
        List<Auction> auctions = new ArrayList<>(); // Initialize an empty list to store results.
        // SQL query to select auctions by creator and status, ordered by creation time.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE creatorUserId = ? AND status = ? ORDER BY creationTimestamp ASC";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set query parameters.
            pStatement.setInt(1, creatorUserId);
            pStatement.setString(2, auctionStatus.getStatusValue()); // Use string value of enum for query.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate through the results and map each row to an Auction object.
                while (result.next()) {
                    auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.fromString(result.getString("status")),
                            result.getObject("winnerUserId", Integer.class),
                            result.getObject("winningPrice", Double.class)
                    ));
                }
            }
        }
        return auctions; // Return the list of found auctions.
    }

    /**
     * Finds open auctions where at least one of their associated items' name or description
     * contains the given keyword. Results are ordered by deadline in ascending order
     * (auctions ending soonest appear first).
     *
     * @param keyword The keyword to search for.
     * @return A list of open {@link Auction} objects matching the keyword criteria.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findOpenAuctionsByKeyword(String keyword) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        // SQL query joining auction, auction_item_link, and item tables.
        // Filters for OPEN auctions with future deadlines and matching keyword in item name/description.
        String query = "SELECT DISTINCT a.id, a.initialPrice, a.minimumBidIncrement, a.deadline, a.creationTimestamp, a.creatorUserId, a.status, a.winnerUserId, a.winningPrice " +
                       "FROM auction a " +
                       "JOIN auction_item_link ail ON a.id = ail.auction_id " +
                       "JOIN item i ON ail.item_id = i.id " +
                       "WHERE a.status = ? AND a.deadline > NOW() " +
                       "AND (i.name LIKE ? OR i.description LIKE ?) " +
                       "ORDER BY a.deadline ASC";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set parameters: status 'OPEN', and keyword with wildcards for LIKE comparison.
            pStatement.setString(1, Auction.AuctionStatus.OPEN.getStatusValue());
            String searchPattern = "%" + keyword + "%";
            pStatement.setString(2, searchPattern);
            pStatement.setString(3, searchPattern);
            try (ResultSet result = pStatement.executeQuery()) {
                // Map results to Auction objects.
                while (result.next()) {
                     auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.OPEN, // Status is known to be OPEN.
                            result.getObject("winnerUserId", Integer.class), // Expected to be null for OPEN.
                            result.getObject("winningPrice", Double.class)  // Expected to be null for OPEN.
                    ));
                }
            }
        }
        return auctions;
    }

    /**
     * Finds auctions won by a specific user. These are CLOSED auctions where the user is the winner.
     * Results are ordered by deadline in descending order (most recently ended first).
     *
     * @param userId The ID of the user whose won auctions are to be retrieved.
     * @return A list of {@link Auction} objects won by the specified user.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findWonAuctionsByUser(int userId) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        // SQL query to select auctions won by a specific user.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE winnerUserId = ? AND status = ? ORDER BY deadline DESC";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set parameters for winner user ID and status 'CLOSED'.
            pStatement.setInt(1, userId);
            pStatement.setString(2, Auction.AuctionStatus.CLOSED.getStatusValue());
            try (ResultSet result = pStatement.executeQuery()) {
                // Map results to Auction objects.
                while (result.next()) {
                    auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.CLOSED, // Status is known to be CLOSED.
                            result.getInt("winnerUserId"), // Winner ID is known.
                            result.getDouble("winningPrice") // Winning price is known.
                    ));
                }
            }
        }
        return auctions;
    }

    /**
     * Closes an auction by updating its status to CLOSED and setting the winner and winning price.
     * This operation is conditional: it only proceeds if the auction is currently OPEN and its
     * deadline (as stored in the database) is less than or equal to the provided {@code currentTimestamp}.
     *
     * @param auctionId The ID of the auction to close.
     * @param winnerUserId The ID of the winning user. Can be {@code null} if no winner.
     * @param winningPrice The final price of the auction. Can be {@code null} if no winner.
     * @param currentTimestamp The current server time, used to verify that the auction's deadline has passed.
     * @return The number of rows affected by the update operation (1 if successful, 0 otherwise).
     * @throws SQLException if a database access error occurs.
     */
    public int closeAuction(int auctionId, Integer winnerUserId, Double winningPrice, Timestamp currentTimestamp) throws SQLException {
        // SQL query to update auction status to CLOSED, and set winner/price.
        // Includes conditions to ensure the auction is OPEN and its deadline has passed.
        String query = "UPDATE auction SET status = ?, winnerUserId = ?, winningPrice = ? " +
                       "WHERE id = ? AND status = ? AND deadline <= ?";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set parameters for the update statement.
            pStatement.setString(1, Auction.AuctionStatus.CLOSED.getStatusValue());
            // Handle nullable winnerUserId and winningPrice.
            if (winnerUserId != null) {
                pStatement.setInt(2, winnerUserId);
            } else {
                pStatement.setNull(2, java.sql.Types.INTEGER);
            }
            if (winningPrice != null) {
                pStatement.setDouble(3, winningPrice);
            } else {
                pStatement.setNull(3, java.sql.Types.DECIMAL);
            }
            pStatement.setInt(4, auctionId); // ID of the auction to close.
            pStatement.setString(5, Auction.AuctionStatus.OPEN.getStatusValue()); // Current status must be OPEN.
            pStatement.setTimestamp(6, currentTimestamp); // Ensure deadline has passed based on current server time.

            // Execute the update and return the number of rows affected.
            return pStatement.executeUpdate();
        }
    }

    /**
     * Finds open auctions by a list of their IDs.
     * This method is specifically useful for the RIA client which might store visited auction IDs
     * and needs to fetch their current status and details.
     * It only returns auctions that are currently OPEN and whose deadline has not passed.
     * Auctions are ordered by their deadline in ascending order.
     *
     * @param auctionIds A list of integer IDs of auctions to retrieve.
     * @return A list of {@link Auction} objects that match the given IDs and are currently open.
     *         Returns an empty list if no IDs are provided or no matching open auctions are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> findOpenAuctionsByIds(List<Integer> auctionIds) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        // Return immediately if the list of IDs is null or empty.
        if (auctionIds == null || auctionIds.isEmpty()) {
            return auctions;
        }

        // Create a string of '?' placeholders for the IN clause, based on the number of IDs.
        // E.g., for 3 IDs, this will be "?,?,?".
        String placeholders = String.join(",", Collections.nCopies(auctionIds.size(), "?"));

        // SQL query to select auctions from the list of IDs, filtering for OPEN status and future deadlines.
        String query = "SELECT id, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, status, winnerUserId, winningPrice " +
                       "FROM auction WHERE id IN (" + placeholders + ") AND status = ? AND deadline > NOW() " +
                       "ORDER BY deadline ASC"; // Order by soonest deadline first.

        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            int index = 1; // Index for setting parameters in the PreparedStatement.
            // Set each auction ID from the input list into the PreparedStatement.
            for (Integer id : auctionIds) {
                pStatement.setInt(index++, id);
            }
            // Set the status parameter to 'OPEN'.
            pStatement.setString(index, Auction.AuctionStatus.OPEN.getStatusValue());

            // Execute the query and process the results.
            try (ResultSet result = pStatement.executeQuery()) {
                while (result.next()) {
                     // Map each row to an Auction object and add to the list.
                     auctions.add(new Auction(
                            result.getInt("id"),
                            result.getDouble("initialPrice"),
                            result.getDouble("minimumBidIncrement"),
                            result.getTimestamp("deadline"),
                            result.getTimestamp("creationTimestamp"),
                            result.getInt("creatorUserId"),
                            Auction.AuctionStatus.OPEN, // Status is known to be OPEN from the query conditions.
                            result.getObject("winnerUserId", Integer.class), // Expected to be null for OPEN auctions.
                            result.getObject("winningPrice", Double.class)  // Expected to be null for OPEN auctions.
                    ));
                }
            }
        }
        // Return the list of found open auctions.
        return auctions;
    }
}