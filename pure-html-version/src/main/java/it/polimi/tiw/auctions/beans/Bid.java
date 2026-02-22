package it.polimi.tiw.auctions.beans;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Represents a bid placed on an auction.
 * This record encapsulates the bid's details, including the auction and user involved,
 * the bid amount, and when it was placed.
 * It implements {@link Serializable} for potential use in sessions or other serialization contexts.
 */
public record Bid(
    int id,
    int auctionId,
    int userId,
    double amount,
    Timestamp timestamp
) implements Serializable {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.

    /**
     * Compact constructor for the {@link Bid} record.
     * This constructor performs validation on the bid's attributes.
     *
     * @param id The unique identifier of the bid.
     * @param auctionId The ID of the auction this bid is for. Must be a positive integer.
     * @param userId The ID of the user who placed the bid. Must be a positive integer.
     * @param amount The monetary amount of the bid. Must be a positive value.
     * @param timestamp The date and time when the bid was placed. Must not be null.
     * @throws IllegalArgumentException if any of the validation rules are violated.
     */
    public Bid {
        // Validate that the auction ID is a positive integer (assuming IDs are positive).
        if (auctionId <= 0) {
            throw new IllegalArgumentException("Invalid auction ID.");
        }
        // Validate that the user ID is a positive integer (assuming IDs are positive).
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID.");
        }
        // Validate that the bid amount is positive.
        if (amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be positive.");
        }
        // Validate that the bid timestamp is not null.
        if (timestamp == null) {
            throw new IllegalArgumentException("Bid timestamp cannot be null.");
        }
    }

    /**
     * Static factory method to create a {@link Bid} instance for insertion into the database.
     * The bid ID is set to 0 (or a convention indicating it's not yet assigned by the database).
     *
     * @param auctionId The ID of the auction this bid is for.
     * @param userId The ID of the user placing the bid.
     * @param amount The amount of the bid.
     * @param timestamp The timestamp when the bid is placed.
     * @return A new {@link Bid} object configured for database insertion.
     */
    public static Bid createBidForInsert(int auctionId, int userId, double amount, Timestamp timestamp) {
        // Create and return a new Bid object.
        // ID is typically 0 or handled by auto-increment in the database.
        return new Bid(0, auctionId, userId, amount, timestamp);
    }

    /**
     * Helper method to get the bid's timestamp as a {@link LocalDateTime} object.
     * This is useful for date/time manipulations and formatting, especially with Thymeleaf's #temporals utility.
     * @return The timestamp as a {@link LocalDateTime}, or null if the bid's timestamp is null.
     */
    public LocalDateTime getTimestampAsLocalDateTime() {
        // Convert the SQL Timestamp to LocalDateTime if not null.
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}