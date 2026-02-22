package it.polimi.tiw.auctions.utils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Utility class for date and time related operations.
 * Provides helper methods for formatting and calculating time differences,
 * which can be useful for displaying auction deadlines and remaining times.
 * In an RIA, such calculations might also be performed client-side using JavaScript,
 * but having server-side utilities can be useful for consistency or if needed by other components.
 */
public class DateTimeUtils {

    /**
     * Calculates the time remaining between a reference timestamp (e.g., user's login time)
     * and a future deadline timestamp.
     * The output is a human-readable string formatted as "X day(s), Y hour(s)",
     * "Y hour(s)", "Z minute(s)", "Less than a minute", or "Expired".
     *
     * @param referenceTime The reference {@link Timestamp} (e.g., login time or current server time).
     * @param deadline      The deadline {@link Timestamp} of an event (e.g., auction end).
     * @return A string representing the time remaining.
     *         Returns "N/A" if either {@code referenceTime} or {@code deadline} is null.
     *         Returns "Expired" if the {@code deadline} has passed relative to the {@code referenceTime}.
     */
    public String getTimeRemaining(Timestamp referenceTime, Timestamp deadline) {
        // Step 1: Handle null inputs.
        if (referenceTime == null || deadline == null) {
            return "N/A"; // Not Applicable if time data is missing.
        }

        // Step 2: Convert SQL Timestamps to Java 8 LocalDateTime for easier manipulation.
        LocalDateTime refDateTime = referenceTime.toLocalDateTime();
        LocalDateTime deadlineDateTime = deadline.toLocalDateTime();

        // Step 3: Check if the deadline has already passed.
        if (deadlineDateTime.isBefore(refDateTime)) {
            return "Expired"; // Deadline is in the past relative to the reference time.
        }

        // Step 4: Calculate the duration between the reference time and the deadline.
        Duration duration = Duration.between(refDateTime, deadlineDateTime);

        // Step 5: Extract days, hours, and minutes from the duration.
        long days = duration.toDays();          // Total number of full days remaining.
        long hours = duration.toHoursPart();    // Remaining hours part (0-23) after extracting full days.
        long minutes = duration.toMinutesPart(); // Remaining minutes part (0-59) after extracting full hours.

        // Step 6: Format the output string based on the calculated duration parts.
        if (days > 0) {
            // If more than a day remains, show days and hours.
            return String.format("%d day(s), %d hour(s)", days, hours);
        } else if (hours > 0) {
            // If less than a day but more than an hour remains, show hours (and minutes, as per original code).
            // Adhering to the "numero di giorni e ore" spec, this could be just hours.
            // return String.format("%d hour(s), %d minute(s)", hours, minutes);
            return String.format("%d hour(s)", hours); // As per specification: "numero di giorni e ore"
        } else if (minutes > 0) {
            // If less than an hour but more than a minute remains, show minutes.
            return String.format("%d minute(s)", minutes);
        } else if (duration.toSeconds() > 0) {
            // If less than a minute but some seconds remain.
            return "Less than a minute";
        } else {
            // If the duration is exactly zero or has just passed while calculating.
            return "Closing very soon";
        }
    }
}