package it.polimi.tiw.auctions.beans;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Represents an auction entity within the online auction system.
 * This record encapsulates all the details of an auction, including its financial aspects,
 * timing, creator, status, and outcome if closed.
 * It implements {@link Serializable} to allow instances to be serialized, for example, for session management.
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
        /**
         * The auction is currently active and accepting bids.
         */
        OPEN("open"),
        /**
         * The auction has ended, either by reaching its deadline and being processed or manually closed.
         */
        CLOSED("closed");

        private final String statusValue; // The string representation of the status, used for database storage.

        /**
         * Constructor for AuctionStatus enum.
         * @param statusValue The string value associated with the status.
         */
        AuctionStatus(String statusValue) {
            this.statusValue = statusValue;
        }

        /**
         * Returns the string value of the auction status.
         * This is typically used for persisting the status to a database.
         * @return The string representation of the status (e.g., "open", "closed").
         */
        public String getStatusValue() {
            return statusValue;
        }

        /**
         * Converts a string representation of an auction status to its corresponding {@link AuctionStatus} enum constant.
         * This method is case-insensitive.
         * @param text The string to convert (e.g., "open", "OPEN", "closed").
         * @return The matching {@link AuctionStatus} enum constant.
         * @throws IllegalArgumentException if the provided text does not match any known auction status.
         */
        public static AuctionStatus fromString(String text) {
            // Check if the input text is not null.
            if (text != null) {
                // Iterate over all AuctionStatus enum values.
                for (AuctionStatus b : AuctionStatus.values()) {
                    // Compare the input text (case-insensitive) with the statusValue of each enum constant.
                    if (text.equalsIgnoreCase(b.statusValue)) {
                        // If a match is found, return the corresponding enum constant.
                        return b;
                    }
                }
            }
            // If no match is found or the text is null, throw an exception.
            throw new IllegalArgumentException("No constant with text " + text + " found for AuctionStatus");
        }
    }

    /**
     * Compact constructor for the {@link Auction} record.
     * This constructor performs validation on the auction's attributes.
     *
     * @param id The unique identifier of the auction.
     * @param initialPrice The starting price for the auction. Must not be negative.
     * @param minimumBidIncrement The smallest amount by which a new bid must exceed the current highest bid or initial price. Must be a positive integer (at least 1).
     * @param deadline The date and time when the auction ends. Must not be null.
     * @param creationTimestamp The date and time when the auction was created. Must not be null.
     * @param creatorUserId The ID of the user who created the auction. Must be a positive integer.
     * @param status The current status of the auction (OPEN or CLOSED). Must not be null.
     * @param winnerUserId The ID of the user who won the auction, if closed and won. Null otherwise.
     * @param winningPrice The final price at which the auction was won, if closed and won. Null otherwise.
     * @throws IllegalArgumentException if any of the validation rules are violated.
     */
    public Auction {
        // Validate that the initial price is not negative.
        if (initialPrice < 0) {
            throw new IllegalArgumentException("Initial price cannot be negative.");
        }
        // Validate that the minimum bid increment is a positive integer (at least 1 as per specification).
        if (minimumBidIncrement < 1) { // Specification: "numero intero di euro" implies >= 1.
            throw new IllegalArgumentException("Minimum bid increment must be positive and at least 1.");
        }
        // Validate that the deadline is not null.
        if (deadline == null) {
            throw new IllegalArgumentException("Deadline cannot be null.");
        }
        // Validate that the creation timestamp is not null.
        if (creationTimestamp == null) {
            throw new IllegalArgumentException("Creation timestamp cannot be null.");
        }
        // Validate that the creator user ID is a positive integer (assuming IDs are positive).
        if (creatorUserId <= 0) {
            throw new IllegalArgumentException("Invalid creator user ID.");
        }
        // Validate that the status is not null.
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null.");
        }
        // For OPEN auctions, there should be no winner or winning price.
        if (status == AuctionStatus.OPEN && (winnerUserId != null || winningPrice != null)) {
            throw new IllegalArgumentException("Open auctions cannot have a winner or winning price.");
        }
        // For CLOSED auctions, if there's a winner, there must be a winning price, and vice-versa.
        // Both can be null if the auction closed without bids.
        if (status == AuctionStatus.CLOSED) {
            // Check for inconsistency: one is set but the other is not.
            if ((winnerUserId != null && winningPrice == null) || (winnerUserId == null && winningPrice != null)) {
                throw new IllegalArgumentException("For closed auctions, winner and winning price must both be set or both be null (if no bids).");
            }
        }
    }

    /**
     * Static factory method to create an {@link Auction} instance for insertion into the database.
     * The auction ID is set to 0 (or a convention indicating it's not yet assigned by the database),
     * status is set to OPEN, and winner details are null.
     *
     * @param initialPrice The starting price for the auction.
     * @param minimumBidIncrement The minimum amount by which bids must increase.
     * @param deadline The timestamp indicating when the auction ends.
     * @param creationTimestamp The timestamp indicating when the auction was created.
     * @param creatorUserId The ID of the user who created the auction.
     * @return A new {@link Auction} object configured for database insertion.
     */
    public static Auction createAuctionForInsert(double initialPrice, double minimumBidIncrement, Timestamp deadline, Timestamp creationTimestamp, int creatorUserId) {
        // Create and return a new Auction object with default values for a new auction.
        // ID is typically 0 or handled by auto-increment in the database.
        // Status is OPEN for new auctions.
        // winnerUserId and winningPrice are null for new auctions.
        return new Auction(0, initialPrice, minimumBidIncrement, deadline, creationTimestamp, creatorUserId, AuctionStatus.OPEN, null, null);
    }

    /**
     * Helper method to get the auction's deadline as a {@link LocalDateTime} object.
     * This is useful for date/time manipulations and formatting, especially with Thymeleaf's #temporals utility.
     * @return The deadline as a {@link LocalDateTime}, or null if the deadline timestamp is null.
     */
    public LocalDateTime getDeadlineAsLocalDateTime() {
        // Convert the SQL Timestamp to LocalDateTime if not null.
        return deadline != null ? deadline.toLocalDateTime() : null;
    }

    /**
     * Helper method to get the auction's creation timestamp as a {@link LocalDateTime} object.
     * Useful for date/time manipulations and formatting, especially with Thymeleaf's #temporals utility.
     * @return The creation timestamp as a {@link LocalDateTime}, or null if the creation timestamp is null.
     */
    public LocalDateTime getCreationTimestampAsLocalDateTime() {
        // Convert the SQL Timestamp to LocalDateTime if not null.
        return creationTimestamp != null ? creationTimestamp.toLocalDateTime() : null;
    }
}