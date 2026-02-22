package it.polimi.tiw.auctions.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import it.polimi.tiw.auctions.beans.Item; // Import the Item bean

/**
 * Data Access Object (DAO) for managing {@link Item} entities in the database.
 * This class provides methods for creating items, finding items by various criteria
 * (ID, seller, auction), and checking for item code existence.
 * It requires a {@link Connection} object to interact with the database.
 */
public class ItemDAO {
    private Connection connection; // Database connection instance.

    /**
     * Constructs an ItemDAO with the given database connection.
     *
     * @param connection The active database connection to be used for DAO operations.
     */
    public ItemDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Creates a new item in the database.
     *
     * @param item The {@link Item} object to create. The ID field is ignored as it's auto-generated.
     * @return The auto-generated ID of the newly created item.
     * @throws SQLException if a database access error occurs or if item creation fails.
     */
    public int createItem(Item item) throws SQLException {
        // SQL query to insert a new item into the 'item' table.
        String query = "INSERT INTO item (itemCode, name, description, image, basePrice, sellerUserId) VALUES (?, ?, ?, ?, ?, ?)";
        int generatedId = -1; // Variable to store the auto-generated ID.
        // Use try-with-resources for PreparedStatement, requesting generated keys.
        try (PreparedStatement pStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            // Set the parameters for the item insertion query.
            pStatement.setString(1, item.itemCode());
            pStatement.setString(2, item.name());
            pStatement.setString(3, item.description());
            pStatement.setBytes(4, item.image());
            pStatement.setDouble(5, item.basePrice());
            pStatement.setInt(6, item.sellerUserId());

            // Execute the update and get the number of affected rows.
            int affectedRows = pStatement.executeUpdate();
            // If no rows were affected, the item creation failed.
            if (affectedRows == 0) {
                throw new SQLException("Creating item failed, no rows affected.");
            }

            // Retrieve the auto-generated ID for the new item.
            // Use try-with-resources for ResultSet.
            try (ResultSet generatedKeys = pStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    generatedId = generatedKeys.getInt(1); // Get the first generated key.
                } else {
                    throw new SQLException("Creating item failed, no ID obtained.");
                }
            }
        }
        // Return the ID of the newly created item.
        return generatedId;
    }

    /**
     * Finds an item by its unique ID.
     *
     * @param itemId The ID of the item to find.
     * @return An {@link Item} object if found, otherwise {@code null}.
     * @throws SQLException if a database access error occurs.
     */
    public Item findItemById(int itemId) throws SQLException {
        // SQL query to select an item by its ID.
        String query = "SELECT id, itemCode, name, description, image, basePrice, sellerUserId FROM item WHERE id = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the item ID parameter.
            pStatement.setInt(1, itemId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a result was found.
                if (result.next()) {
                    // Create and return a new Item object from the ResultSet data.
                    return new Item(
                            result.getInt("id"),
                            result.getString("itemCode"),
                            result.getString("name"),
                            result.getString("description"),
                            result.getBytes("image"),
                            result.getDouble("basePrice"),
                            result.getInt("sellerUserId")
                    );
                }
            }
        }
        // If no item is found with the given ID, return null.
        return null;
    }

    /**
     * Finds items available for a specific seller to put up for auction.
     * An item is considered available if:
     * 1. It belongs to the specified seller.
     * 2. It is NOT currently part of an OPEN auction.
     * 3. It was NOT sold in a CLOSED auction (i.e., if an auction is closed and has a winner, its items are considered sold).
     * Items in closed auctions without a winner are implicitly available again.
     *
     * @param sellerUserId The ID of the seller whose available items are to be retrieved.
     * @return A list of available {@link Item} objects. The list is empty if no such items are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Item> findAvailableItemsBySellerId(int sellerUserId) throws SQLException {
        List<Item> items = new ArrayList<>(); // Initialize an empty list.
        // SQL query to find available items for a seller.
        // It excludes items that are part of any OPEN auction.
        // It also excludes items that were part of a CLOSED auction that had a winner (i.e., sold items).
        String query = "SELECT i.id, i.itemCode, i.name, i.description, i.image, i.basePrice, i.sellerUserId " +
                       "FROM item i " +
                       "WHERE i.sellerUserId = ? " + // Item must belong to the seller.
                       "AND i.id NOT IN ( " +        // Exclude items currently in an OPEN auction.
                       "  SELECT ail.item_id " +
                       "  FROM auction_item_link ail " +
                       "  JOIN auction a ON ail.auction_id = a.id " +
                       "  WHERE a.status = 'OPEN' " +
                       ") " +
                       "AND i.id NOT IN ( " +        // Exclude items that were sold (in a CLOSED auction with a winner).
                       "  SELECT ail.item_id " +
                       "  FROM auction_item_link ail " +
                       "  JOIN auction a ON ail.auction_id = a.id " +
                       "  WHERE a.status = 'CLOSED' AND a.winnerUserId IS NOT NULL" +
                       ")";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the seller user ID parameter.
            pStatement.setInt(1, sellerUserId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                    // For each row, create an Item object and add it to the list.
                    items.add(new Item(
                            result.getInt("id"),
                            result.getString("itemCode"),
                            result.getString("name"),
                            result.getString("description"),
                            result.getBytes("image"),
                            result.getDouble("basePrice"),
                            result.getInt("sellerUserId")
                    ));
                }
            }
        }
        // Return the list of available items.
        return items;
    }

    /**
     * Finds all items associated with a specific auction.
     *
     * @param auctionId The ID of the auction for which to retrieve items.
     * @return A list of {@link Item} objects included in the auction. The list is empty if the auction has no items or does not exist.
     * @throws SQLException if a database access error occurs.
     */
    public List<Item> findItemsByAuctionId(int auctionId) throws SQLException {
        List<Item> items = new ArrayList<>(); // Initialize an empty list.
        // SQL query to select items linked to a specific auction via the 'auction_item_link' table.
        String query = "SELECT i.id, i.itemCode, i.name, i.description, i.image, i.basePrice, i.sellerUserId " +
                       "FROM item i INNER JOIN auction_item_link ail ON i.id = ail.item_id " +
                       "WHERE ail.auction_id = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the auction ID parameter.
            pStatement.setInt(1, auctionId);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate over the ResultSet.
                while (result.next()) {
                    // For each row, create an Item object and add it to the list.
                    items.add(new Item(
                            result.getInt("id"),
                            result.getString("itemCode"),
                            result.getString("name"),
                            result.getString("description"),
                            result.getBytes("image"),
                            result.getDouble("basePrice"),
                            result.getInt("sellerUserId")
                    ));
                }
            }
        }
        // Return the list of items found for the auction.
        return items;
    }

    /**
     * Checks if an item code already exists in the database.
     * This is useful for ensuring item codes are unique before creating a new item.
     *
     * @param itemCode The item code to check for existence.
     * @return {@code true} if the item code exists, {@code false} otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean itemCodeExists(String itemCode) throws SQLException {
        // SQL query to count occurrences of an item code.
        String query = "SELECT COUNT(*) AS count FROM item WHERE itemCode = ?";
        // Use try-with-resources for PreparedStatement.
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            // Set the item code parameter.
            pStatement.setString(1, itemCode);
            // Execute the query and get the ResultSet.
            // Use try-with-resources for ResultSet.
            try (ResultSet result = pStatement.executeQuery()) {
                // Check if a result was returned.
                if (result.next()) {
                    // Return true if the count is greater than 0, indicating the item code exists.
                    return result.getInt("count") > 0;
                }
            }
        }
        // If no result or count is 0, the item code does not exist.
        return false;
    }
}