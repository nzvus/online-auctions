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
 * It is used in the RIA version to support item management functionalities, often
 * returning data to be serialized to JSON for the client.
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
     * @param item The {@link Item} object to create. The ID field is ignored (auto-generated).
     * @return The auto-generated ID of the newly created item.
     * @throws SQLException if a database access error occurs or if item creation fails.
     */
    public int createItem(Item item) throws SQLException {
        // SQL query for inserting a new item.
        String query = "INSERT INTO item (itemCode, name, description, image, basePrice, sellerUserId) VALUES (?, ?, ?, ?, ?, ?)";
        int generatedId = -1; // To store the auto-generated ID.
        // Use try-with-resources, requesting RETURN_GENERATED_KEYS.
        try (PreparedStatement pStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            // Set parameters for the item insertion.
            pStatement.setString(1, item.itemCode());
            pStatement.setString(2, item.name());
            pStatement.setString(3, item.description());
            pStatement.setBytes(4, item.image());
            pStatement.setDouble(5, item.basePrice());
            pStatement.setInt(6, item.sellerUserId());

            // Execute the update and check if successful.
            int affectedRows = pStatement.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating item failed, no rows affected.");
            }

            // Retrieve the auto-generated item ID.
            try (ResultSet generatedKeys = pStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    generatedId = generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating item failed, no ID obtained.");
                }
            }
        }
        // Return the new item's ID.
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
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, itemId); // Set the item ID parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // If an item is found, map the ResultSet row to an Item object.
                if (result.next()) {
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
        return null; // Item not found.
    }

    /**
     * Finds items available for a specific seller to list in new auctions.
     * An item is considered "available" if it belongs to the seller AND is not part of an OPEN auction
     * AND was not sold in a CLOSED auction (i.e., a closed auction that has a winner).
     *
     * @param sellerUserId The ID of the seller whose available items are to be retrieved.
     * @return A list of available {@link Item} objects. The list is empty if no such items are found.
     * @throws SQLException if a database access error occurs.
     */
    public List<Item> findAvailableItemsBySellerId(int sellerUserId) throws SQLException {
        List<Item> items = new ArrayList<>(); // Initialize an empty list for results.
        // SQL query to find items available for a seller.
        // It excludes items in open auctions and items that have been sold (closed auction with a winner).
        String query = "SELECT i.id, i.itemCode, i.name, i.description, i.image, i.basePrice, i.sellerUserId " +
                       "FROM item i " +
                       "WHERE i.sellerUserId = ? " + // Item must belong to the specified seller.
                       "AND i.id NOT IN ( " +        // Item must NOT be in any currently OPEN auction.
                       "  SELECT ail.item_id " +
                       "  FROM auction_item_link ail " +
                       "  JOIN auction a ON ail.auction_id = a.id " +
                       "  WHERE a.status = 'OPEN' " +
                       ") " +
                       "AND i.id NOT IN ( " +        // Item must NOT have been sold (i.e., in a CLOSED auction with a winner).
                       "  SELECT ail.item_id " +
                       "  FROM auction_item_link ail " +
                       "  JOIN auction a ON ail.auction_id = a.id " +
                       "  WHERE a.status = 'CLOSED' AND a.winnerUserId IS NOT NULL" +
                       ")";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, sellerUserId); // Set the seller ID parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate through results and map each row to an Item object.
                while (result.next()) {
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
        return items; // Return the list of available items.
    }

    /**
     * Finds all items associated with a specific auction.
     *
     * @param auctionId The ID of the auction for which to retrieve items.
     * @return A list of {@link Item} objects included in the specified auction.
     *         The list is empty if the auction has no items or does not exist.
     * @throws SQLException if a database access error occurs.
     */
    public List<Item> findItemsByAuctionId(int auctionId) throws SQLException {
        List<Item> items = new ArrayList<>(); // Initialize an empty list for results.
        // SQL query to select items linked to a specific auction via the 'auction_item_link' table.
        String query = "SELECT i.id, i.itemCode, i.name, i.description, i.image, i.basePrice, i.sellerUserId " +
                       "FROM item i INNER JOIN auction_item_link ail ON i.id = ail.item_id " +
                       "WHERE ail.auction_id = ?";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setInt(1, auctionId); // Set the auction ID parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // Iterate through results and map each row to an Item object.
                while (result.next()) {
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
        return items; // Return the list of items for the auction.
    }

    /**
     * Checks if an item code already exists in the database.
     * This is typically used to ensure item codes are unique before creating a new item.
     *
     * @param itemCode The item code to check for existence.
     * @return {@code true} if the item code already exists in the database, {@code false} otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean itemCodeExists(String itemCode) throws SQLException {
        // SQL query to count items with the specified itemCode.
        String query = "SELECT COUNT(*) AS count FROM item WHERE itemCode = ?";
        try (PreparedStatement pStatement = connection.prepareStatement(query)) {
            pStatement.setString(1, itemCode); // Set the item code parameter.
            try (ResultSet result = pStatement.executeQuery()) {
                // If a result is returned (it always will be for COUNT(*)), check the count.
                if (result.next()) {
                    return result.getInt("count") > 0; // True if count > 0, meaning code exists.
                }
            }
        }
        return false; // Should not be reached if query executes correctly, but default to false.
    }
}