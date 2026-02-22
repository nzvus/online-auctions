package it.polimi.tiw.auctions.beans;

import java.io.Serializable;
import java.util.Base64;

/**
 * Represents an Item entity, mirroring the 'item' table in the database.
 * An item has a unique code, name, description, image, base price, and is associated with a seller.
 * The primary key is 'id' (auto-incremented by the database).
 * A unique constraint is expected on 'itemCode'.
 * A foreign key 'sellerUserId' references the 'user' table.
 * It implements {@link Serializable} for potential use in sessions or other serialization contexts.
 */
public record Item(
    int id,
    String itemCode,
    String name,
    String description,
    byte[] image, // Raw image data
    double basePrice,
    int sellerUserId
) implements Serializable {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.

    /**
     * Compact constructor for the {@link Item} record.
     * This constructor performs validation on the item's attributes.
     *
     * @param id The unique identifier of the item.
     * @param itemCode The unique code for the item. Cannot be null or blank.
     * @param name The name of the item. Cannot be null or blank.
     * @param description A description of the item. Cannot be null or blank.
     * @param image The image data for the item as a byte array. Cannot be null or empty.
     * @param basePrice The base price of the item. Cannot be negative.
     * @param sellerUserId The ID of the user selling the item. Must be a positive integer.
     * @throws IllegalArgumentException if any of the validation rules are violated.
     */
    public Item {
        // Validate that the item code is not null or blank.
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("Item code cannot be null or blank.");
        }
        // Validate that the item name is not null or blank.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be null or blank.");
        }
        // Validate that the item description is not null or blank.
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Item description cannot be null or blank.");
        }
        // Validate that the image data is provided (not null and not an empty array).
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Image data cannot be null or empty.");
        }
        // Validate that the base price is not negative.
        if (basePrice < 0) {
            throw new IllegalArgumentException("Base price cannot be negative.");
        }
        // Validate that the seller user ID is a positive integer (assuming IDs are positive).
        if (sellerUserId <= 0) {
            throw new IllegalArgumentException("Invalid seller user ID.");
        }
    }

    /**
     * Static factory method to create an {@link Item} instance for insertion into the database.
     * The item ID is set to 0 (or a convention indicating it's not yet assigned by the database).
     *
     * @param itemCode The unique code for the item.
     * @param name The name of the item.
     * @param description A description of the item.
     * @param image The image data for the item as a byte array.
     * @param basePrice The base price of the item.
     * @param sellerUserId The ID of the user selling the item.
     * @return A new {@link Item} object configured for database insertion.
     */
    public static Item createItemForInsert(String itemCode, String name, String description, byte[] image, double basePrice, int sellerUserId) {
        // Create and return a new Item object.
        // ID is typically 0 or handled by auto-increment in the database.
        return new Item(0, itemCode, name, description, image, basePrice, sellerUserId);
    }

    /**
     * Helper method to get the item's image as a Base64 encoded string.
     * This is typically used for embedding the image directly in HTML (e.g., in an <img> tag's src attribute).
     *
     * @return The Base64 encoded string of the image, or an empty string if the image data is null or empty.
     *         Alternatively, could return null or a placeholder Base64 string for a default image.
     */
    public String getBase64Image() {
        // Check if the image data is present.
        if (this.image != null && this.image.length > 0) {
            // Encode the byte array to a Base64 string.
            return Base64.getEncoder().encodeToString(this.image);
        }
        // Return an empty string if no image data is available.
        return "";
    }
}