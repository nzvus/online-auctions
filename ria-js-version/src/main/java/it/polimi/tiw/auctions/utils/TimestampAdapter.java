package it.polimi.tiw.auctions.utils;

import com.google.gson.*; // Import Gson classes.
import java.lang.reflect.Type;
import java.sql.Timestamp;

/**
 * A Gson TypeAdapter for serializing and deserializing {@link java.sql.Timestamp} objects.
 * This adapter converts Timestamps to their epoch millisecond representation (a long)
 * for JSON serialization, which is easily consumable by JavaScript `new Date(milliseconds)`.
 * It also handles deserialization from epoch milliseconds back to Timestamp objects.
 */
public class TimestampAdapter implements JsonSerializer<Timestamp>, JsonDeserializer<Timestamp> {

    /**
     * Serializes a {@link Timestamp} object into its JSON representation.
     * The Timestamp is converted to the number of milliseconds since the epoch (January 1, 1970, 00:00:00 GMT).
     *
     * @param src       The {@link Timestamp} object to serialize.
     * @param typeOfSrc The actual type of the source object (unused here, but part of the interface).
     * @param context   Context for serialization, can be used for more complex scenarios (unused here).
     * @return A {@link JsonElement} representing the Timestamp as a JSON primitive (long number),
     *         or {@code null} if the source Timestamp is null.
     */
    @Override
    public JsonElement serialize(Timestamp src, Type typeOfSrc, JsonSerializationContext context) {
        // If the source Timestamp is null, return a JSON null.
        // Otherwise, return a JSON primitive containing the Timestamp's time in milliseconds.
        return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.getTime());
    }

    /**
     * Deserializes a JSON element into a {@link Timestamp} object.
     * Expects the JSON element to be a JSON primitive number representing epoch milliseconds.
     *
     * @param json      The {@link JsonElement} to deserialize.
     * @param typeOfT   The type of the object to deserialize to (unused here).
     * @param context   Context for deserialization (unused here).
     * @return A {@link Timestamp} object created from the epoch milliseconds,
     *         or {@code null} if the JSON element is null, not a primitive, or not a number.
     * @throws JsonParseException if the JSON element is not in the expected format.
     */
    @Override
    public Timestamp deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        // Check if the JSON element is suitable for conversion (not null, is a primitive, is a number).
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
            // If not suitable, return null or throw a JsonParseException depending on desired strictness.
            // Returning null is often more lenient for optional fields.
            return null;
        }
        // If suitable, get the long value (milliseconds since epoch) and create a new Timestamp.
        return new Timestamp(json.getAsLong());
    }
}