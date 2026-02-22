package it.polimi.tiw.auctions.utils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Utility class for date and time related operations.
 * This class provides helper methods for formatting and calculating time differences,
 * particularly useful for displaying auction deadlines and remaining times.
 */
public class DateTimeUtils {

    /**
     * Calculates the time remaining between a reference timestamp (e.g., user's login time)
     * and a future deadline timestamp.
     * The output is a human-readable string, such as "X day(s), Y hour(s)",
     * "Y hour(s)", "Z minute(s)", "Less than a minute", or "Expired".
     *
     * @param referenceTime The reference {@link Timestamp} (e.g., login time).
     * @param deadline      The deadline {@link Timestamp} of an event (e.g., auction end).
     * @return A string representing the time remaining. Returns "N/A" if either timestamp is null.
     *         Returns "Expired" if the deadline has passed relative to the reference time.
     */
    public String getTimeRemaining(Timestamp referenceTime, Timestamp deadline) {
        // Step 1: Validate inputs. If either timestamp is null, return "N/A".
        if (referenceTime == null || deadline == null) {
            return "N/A";
        }

        // Step 2: Convert SQL Timestamps to LocalDateTime objects for easier comparison and calculation.
        LocalDateTime refDateTime = referenceTime.toLocalDateTime();
        LocalDateTime deadlineDateTime = deadline.toLocalDateTime();

        // Step 3: Check if the deadline has already passed relative to the reference time.
        if (deadlineDateTime.isBefore(refDateTime)) {
            return "Expired";
        }

        // Step 4: Calculate the duration between the reference time and the deadline.
        Duration duration = Duration.between(refDateTime, deadlineDateTime);

        // Step 5: Extract days, hours, and minutes from the duration.
        long days = duration.toDays();          // Total days in the duration.
        long hours = duration.toHoursPart();    // Hours part of the duration (0-23).
        long minutes = duration.toMinutesPart(); // Minutes part of the duration (0-59).

        // Step 6: Format the output string based on the remaining time.
        if (days > 0) {
            // If there are one or more days remaining, show days and hours.
            return String.format("%d day(s), %d hour(s)", days, hours);
        } else if (hours > 0) {
            // If less than a day but one or more hours remaining, show hours and minutes.
            // As per spec, only days and hours: return String.format("%d hour(s), %d minute(s)", hours, minutes);
            return String.format("%d hour(s)", hours); // Adhering to spec "numero di giorni e ore"
        } else if (minutes > 0) {
            // If less than an hour but one or more minutes remaining, show minutes.
            return String.format("%d minute(s)", minutes);
        } else if (duration.toSeconds() > 0) {
            // If less than a minute but some seconds remaining.
            return "Less than a minute";
        } else {
            // If the duration is zero or negative (though "Expired" should catch negative).
            // This case might be for exactly at the deadline or very close to it.
            return "Closing very soon";
        }
    }
}