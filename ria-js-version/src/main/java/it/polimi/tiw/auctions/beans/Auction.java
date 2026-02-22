package it.polimi.tiw.auctions.beans;

import java.io.Serializable;
import java.sql.Timestamp;
// LocalDateTime is not strictly needed here if not used for internal logic
// and timestamps are serialized as longs for JS.

/**
 * Represents an auction entity within the online auction system for the RIA version.
 * This record encapsulates auction details for JSON serialization to the client.
 * It implements {@link Serializable} for potential session management.
 */
public record Auction(
    int id,
    double initialPrice,
    double minimumBidIncrement,
    Timestamp deadline,
    Timestamp creationTimestamp,
    int creatorUserId,
    AuctionStatus status,
    Integer winnerUserId,
    Double winningPrice
) implements Serializable {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.

    /**
     * Defines the possible statuses of an auction.
     */
    public enum AuctionStatus {
        OPEN("open"),
        CLOSED("closed");

        private final String statusValue;

        AuctionStatus(String statusValue) {
            this.statusValue = statusValue;
        }

        public String getStatusValue() {
            return statusValue;
        }

        public static AuctionStatus fromString(String text) {
            if (text != null) {
                for (AuctionStatus b : AuctionStatus.values()) {
                    if (text.equalsIgnoreCase(b.statusValue)) {
                        return b;
                    }
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found for AuctionStatus");
        }
    }

    /**
     * Compact constructor for the {@link Auction} record (RIA version).
     * Performs validation on auction attributes.
     *
     * @param id The unique identifier.
     * @param initialPrice Starting price, non-negative.
     * @param minimumBidIncrement Minimum bid step, positive integer (>=1).
     * @param deadline Auction end time, not null.
     * @param creationTimestamp Auction creation time, not null.
     * @param creatorUserId ID of the auction creator, positive.
     * @param status Current auction status, not null.
     * @param winnerUserId ID of the winner if closed and won, else null.
     * @param winningPrice Final price if closed and won, else null.
     * @throws IllegalArgumentException if validation fails.
     */
    public Auction {
        // Validate initialPrice: must not be negative.
        if (initialPrice < 0) {
            throw new IllegalArgumentException("Initial price cannot be negative.");
        }
        // Validate minimumBidIncrement: must be at least 1 (integer euro).
        if (minimumBidIncrement < 1) {
            throw new IllegalArgumentException("Minimum bid increment must be positive and at least 1.");
        }
        // Validate deadline: must not be null.
        if (deadline == null) {
            throw new IllegalArgumentException("Deadline cannot be null.");
        }
        // Validate creationTimestamp: must not be null.
        if (creationTimestamp == null) {
            throw new IllegalArgumentException("Creation timestamp cannot be null.");
        }
        // Validate creatorUserId: must be positive.
        if (creatorUserId <= 0) {
            throw new IllegalArgumentException("Invalid creator user ID.");
        }
        // Validate status: must not be null.
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null.");
        }
        // Validate winner details for OPEN auctions: must be null.
        if (status == AuctionStatus.OPEN && (winnerUserId != null || winningPrice != null)) {
            throw new IllegalArgumentException("Open auctions cannot have a winner or winning price.");
        }
        // Validate winner details for CLOSED auctions: either both null or both non-null.
        if (status == AuctionStatus.CLOSED) {
            if ((winnerUserId != null && winningPrice == null) || (winnerUserId == null && winningPrice != null)) {
                throw new IllegalArgumentException("For closed auctions, winner and winning price must both be set or both be null (if no bids).");
            }
        }
    }

    /**
     * Static factory method to create an {@link Auction} instance for database insertion (RIA version).
     * Initializes ID to 0, status to OPEN, and winner details to null.
     *
     * @param initialPrice The starting price.
     * @param minimumBidIncrement The minimum bid step.
     * @param deadline The auction end time.
     * @param creationTimestamp The auction creation time.
     * @param creatorUserId The ID of the auction creator.
     * @return A new {@link Auction} object.
     */
    public static Auction createAuctionForInsert(double initialPrice, double minimumBidIncrement, Timestamp deadline, Timestamp creationTimestamp, int creatorUserId) {
        // Create and return a new Auction, defaulting status to OPEN and winner details to null.
        return new Auction(0, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, AuctionStatus.OPEN, null, null);
    }

    // Helper methods like getDeadlineAsLocalDateTime() and getCreationTimestampAsLocalDateTime()
    // are omitted as timestamps will likely be serialized as epoch milliseconds for JavaScript clients,
    // using a custom Gson TimestampAdapter.
}