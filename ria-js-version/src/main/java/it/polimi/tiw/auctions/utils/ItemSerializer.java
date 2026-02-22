package it.polimi.tiw.auctions.utils;

import com.google.gson.*; // Import Gson classes.
import it.polimi.tiw.auctions.beans.Item; // Import the Item bean.
import java.lang.reflect.Type;
import java.util.Base64; // For encoding byte[] image data.

/**
 * A custom Gson JsonSerializer for the {@link Item} class.
 * This serializer ensures that the {@code image} byte array within an Item object
 * is converted to a Base64 encoded string when the Item is serialized to JSON.
 * This makes the image data readily usable by web clients (e.g., in an <img> src attribute).
 * Other fields of the Item are serialized as usual.
 */
public class ItemSerializer implements JsonSerializer<Item> {

    /**
     * Serializes an {@link Item} object into its JSON representation.
     * The {@code image} byte array is converted to a Base64 string and included
     * in the JSON output as a field named "base64Image".
     * The original {@code image} byte array itself is not included in the output
     * to avoid sending raw binary data over JSON and to prevent redundancy.
     *
     * @param src       The {@link Item} object to serialize.
     * @param typeOfSrc The actual type of the source object (unused here, part of the interface).
     * @param context   Context for serialization, can be used for recursive serialization (unused here for simple fields).
     * @return A {@link JsonElement} representing the Item as a JSON object.
     *         Returns {@link JsonNull#INSTANCE} if the source Item is null.
     */
    @Override
    public JsonElement serialize(Item src, Type typeOfSrc, JsonSerializationContext context) {
        // Step 1: Handle null source object.
        if (src == null) {
            return JsonNull.INSTANCE; // Return JSON null if the item itself is null.
        }

        // Step 2: Create a JsonObject to build the JSON representation.
        JsonObject jsonObject = new JsonObject();

        // Step 3: Add standard Item properties to the JsonObject.
        // These will be serialized using Gson's default behavior for their respective types.
        jsonObject.addProperty("id", src.id());
        jsonObject.addProperty("itemCode", src.itemCode());
        jsonObject.addProperty("name", src.name());
        jsonObject.addProperty("description", src.description());
        jsonObject.addProperty("basePrice", src.basePrice());
        jsonObject.addProperty("sellerUserId", src.sellerUserId());

        // Step 4: Handle the image data.
        // Convert the byte[] image to a Base64 encoded string.
        if (src.image() != null && src.image().length > 0) {
            // If image data exists, encode it and add as "base64Image" property.
            jsonObject.addProperty("base64Image", Base64.getEncoder().encodeToString(src.image()));
        } else {
            // If no image data, add a JSON null for "base64Image" or an empty string.
            // Using JsonNull.INSTANCE is often cleaner for optional fields.
            jsonObject.add("base64Image", JsonNull.INSTANCE);
            // Alternatively: jsonObject.addProperty("base64Image", "");
        }

        // Note: The raw byte[] 'image' field from the Item record is intentionally NOT added
        // to the jsonObject here. The client will use the 'base64Image' string.
        // This avoids sending potentially large binary data in the JSON and redundancy.

        // Step 5: Return the constructed JsonObject.
        return jsonObject;
    }
}