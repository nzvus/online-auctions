package it.polimi.tiw.auctions.beans;

import java.io.Serializable;
import java.sql.Timestamp;
// LocalDateTime import removed as getTimestampAsLocalDateTime() is omitted.

/**
 * Represents a bid placed on an auction for the RIA version.
 * This record encapsulates bid details for JSON serialization to the client.
 * Implements {@link Serializable} for potential session management.
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
     * Compact constructor for the {@link Bid} record (RIA version).
     * Performs validation on bid attributes.
     *
     * @param id The unique bid identifier.
     * @param auctionId ID of the associated auction, positive.
     * @param userId ID of the bidding user, positive.
     * @param amount Bid amount, positive.
     * @param timestamp Time of bid placement, not null.
     * @throws IllegalArgumentException if validation fails.
     */
    public Bid {
        // Validate auctionId: must be positive.
        if (auctionId <= 0) {
            throw new IllegalArgumentException("Invalid auction ID.");
        }
        // Validate userId: must be positive.
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID.");
        }
        // Validate amount: must be positive.
        if (amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be positive.");
        }
        // Validate timestamp: must not be null.
        if (timestamp == null) {
            throw new IllegalArgumentException("Bid timestamp cannot be null.");
        }
    }

    /**
     * Static factory method to create a {@link Bid} instance for database insertion (RIA version).
     * Initializes ID to 0.
     *
     * @param auctionId The ID of the associated auction.
     * @param userId The ID of the bidding user.
     * @param amount The bid amount.
     * @param timestamp The time of bid placement.
     * @return A new {@link Bid} object.
     */
    public static Bid createBidForInsert(int auctionId, int userId, double amount, Timestamp timestamp) {
        // Create and return a new Bid, typically with ID 0 for auto-generation by the database.
        return new Bid(0, auctionId, userId, amount, timestamp);
    }

    // Helper method getTimestampAsLocalDateTime() is omitted as timestamps will likely
    // be serialized as epoch milliseconds for JavaScript clients.
}