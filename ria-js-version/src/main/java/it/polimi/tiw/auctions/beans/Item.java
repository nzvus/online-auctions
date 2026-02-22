package it.polimi.tiw.auctions.beans;

import java.io.Serializable;
// Base64 import can be removed here if image conversion is solely handled by a dedicated JSON serializer.

/**
 * Represents an Item entity in the online auction system for the RIA version.
 * This record stores item details. For JSON serialization, the raw image byte array
 * is typically converted to a Base64 string by a custom serializer (e.g., ItemSerializer).
 * Implements {@link Serializable} for potential session management.
 */
public record Item(
    int id,
    String itemCode,
    String name,
    String description,
    byte[] image, // Raw image data; will be serialized to Base64 by ItemSerializer.
    double basePrice,
    int sellerUserId
) implements Serializable {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.

    /**
     * Compact constructor for the {@link Item} record (RIA version).
     * Validates item attributes.
     *
     * @param id The unique item identifier.
     * @param itemCode Unique item code, not null/blank.
     * @param name Item name, not null/blank.
     * @param description Item description, not null/blank.
     * @param image Image data (byte array), not null/empty.
     * @param basePrice Item's base price, non-negative.
     * @param sellerUserId ID of the seller, positive.
     * @throws IllegalArgumentException if validation fails.
     */
    public Item {
        // Validate itemCode: must not be null or blank.
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("Item code cannot be null or blank.");
        }
        // Validate name: must not be null or blank.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be null or blank.");
        }
        // Validate description: must not be null or blank.
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Item description cannot be null or blank.");
        }
        // Validate image: must be provided and not empty.
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Image data cannot be null or empty.");
        }
        // Validate basePrice: must not be negative.
        if (basePrice < 0) {
            throw new IllegalArgumentException("Base price cannot be negative.");
        }
        // Validate sellerUserId: must be positive.
        if (sellerUserId <= 0) {
            throw new IllegalArgumentException("Invalid seller user ID.");
        }
    }

    /**
     * Static factory method to create an {@link Item} instance for database insertion (RIA version).
     * Initializes ID to 0.
     *
     * @param itemCode The unique item code.
     * @param name The item name.
     * @param description The item description.
     * @param image The image data as a byte array.
     * @param basePrice The item's base price.
     * @param sellerUserId The ID of the seller.
     * @return A new {@link Item} object.
     */
    public static Item createItemForInsert(String itemCode, String name, String description, byte[] image, double basePrice, int sellerUserId) {
        // Create and return a new Item, typically with ID 0 for auto-generation by the database.
        return new Item(0, itemCode, name, description, image, basePrice, sellerUserId);
    }

    // Helper method getBase64Image() is omitted as the conversion to Base64
    // for JSON will be handled by a custom ItemSerializer.
}