package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson; // For JSON response.

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig; // Essential for request.getPart() to work.
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part; // For handling uploaded file parts.

import it.polimi.tiw.auctions.beans.Item;    // Import Item bean.
import it.polimi.tiw.auctions.beans.User;   // Import User bean.
import it.polimi.tiw.auctions.dao.ItemDAO; // Import ItemDAO.
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler.

/**
 * Handles requests for creating new items in the RIA version.
 * This servlet processes POST requests, expected to be `multipart/form-data`
 * due to image file uploads, typically from `sellPage.js`.
 * It validates item details (code, name, description, price, image),
 * checks for item code uniqueness, and persists the new item to the database.
 * Responds with JSON indicating success or failure.
 */
@MultipartConfig // Enables handling of multipart/form-data requests, crucial for file uploads.
public class CreateItemServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;    // Unique ID for serialization.
    private Connection connection = null;               // Database connection.
    private ItemDAO itemDAO;                            // DAO for item operations.

    /**
     * Default constructor.
     */
    public CreateItemServlet() {
        super();
    }

    /**
     * Initializes the servlet.
     * Establishes a database connection and initializes the ItemDAO.
     *
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init() throws ServletException {
        // Get ServletContext for application resources.
        ServletContext context = getServletContext();
        // Obtain database connection.
        connection = ConnectionHandler.getConnection(context);
        // Initialize ItemDAO.
        itemDAO = new ItemDAO(connection);
    }

    /**
     * Handles HTTP POST requests to create a new item.
     *
     * @param request  The {@link HttpServletRequest} object, expected to be multipart/form-data.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Authenticate user and prepare JSON response.
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        // Prepare for JSON response.
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Gson gson = new Gson();
        Map<String, Object> responseData = new HashMap<>();

        // Ensure user is authenticated.
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized.
            responseData.put("errorMessage", "User not authenticated.");
            response.getWriter().write(gson.toJson(responseData));
            return;
        }

        // Step 2: Retrieve item details from request parameters and file part.
        String itemCode = request.getParameter("itemCode");
        String itemName = request.getParameter("itemName");
        String description = request.getParameter("description");
        String basePriceStr = request.getParameter("basePrice");
        Part imagePart = null;
        try {
            // request.getPart() is used to access uploaded files in a multipart request.
            imagePart = request.getPart("image");
        } catch (IOException | ServletException e) {
            // This can occur if the request is not actually multipart/form-data.
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request.
            responseData.put("errorMessage", "Invalid request format for image upload. Ensure form is multipart/form-data.");
            response.getWriter().write(gson.toJson(responseData));
            return;
        }

        // Step 3: Initialize error message variable.
        String errorMessage = null;

        // Step 4: Validate presence of required fields.
        if (itemCode == null || itemCode.isBlank() ||
            itemName == null || itemName.isBlank() ||
            description == null || description.isBlank() ||
            basePriceStr == null || basePriceStr.isBlank()) {
            errorMessage = "All textual fields (Item Code, Name, Description, Base Price) are required.";
        }
        // Also validate that an image was actually uploaded.
        if (imagePart == null || imagePart.getSize() == 0) {
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "An image file is required.";
        }

        // Step 5: Validate base price format and value if no prior errors.
        double basePrice = 0;
        if (errorMessage == null) {
            try {
                basePrice = Double.parseDouble(basePriceStr);
                if (basePrice <= 0) { // Base price must be positive.
                    errorMessage = "Base price must be a positive value.";
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid base price format. Please enter a valid number.";
            }
        }

        // Step 6: Validate and process the uploaded image if no prior errors.
        byte[] imageBytes = null;
        if (errorMessage == null && imagePart != null && imagePart.getSize() > 0) {
            String submittedFileName = imagePart.getSubmittedFileName();
            // Ensure a file was actually selected and has a name.
            if (submittedFileName == null || submittedFileName.isBlank()) {
                errorMessage = "Image file name is missing. Please select an image.";
            } else {
                String contentType = imagePart.getContentType();
                // Validate that the uploaded file is an image.
                if (contentType == null || !contentType.startsWith("image/")) {
                    errorMessage = "Invalid image file type. Only image files (e.g., JPG, PNG, GIF) are allowed.";
                } else {
                    // Read the image content into a byte array.
                    try (InputStream fileContent = imagePart.getInputStream()) {
                        imageBytes = fileContent.readAllBytes();
                        if (imageBytes.length == 0) { // Double-check if file is empty after reading.
                            errorMessage = "Image file appears to be empty.";
                        }
                    } catch (IOException e) {
                        e.printStackTrace(); // Log I/O error.
                        errorMessage = "Error reading image file.";
                    }
                }
            }
        } else if (errorMessage == null && (imagePart == null || imagePart.getSize() == 0)) {
            // This reinforces the earlier check that image is mandatory.
            errorMessage = "An image file is required and was not provided or is empty.";
        }

        // Step 7: If all validations pass, attempt to create the item in the database.
        if (errorMessage == null) {
            try {
                // Ensure item code uniqueness.
                if (itemDAO.itemCodeExists(itemCode)) {
                    errorMessage = "Item code '" + itemCode + "' already exists. Please choose a unique code.";
                    response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict.
                } else {
                    // Create an Item bean.
                    Item newItem = Item.createItemForInsert(itemCode, itemName, description, imageBytes, basePrice, user.id());
                    // Persist the new item.
                    itemDAO.createItem(newItem);
                    responseData.put("successMessage", "Item '" + itemName + "' created successfully!");
                    // No need for session.setAttribute("lastAction", "sell_related"); in pure servlet for RIA,
                    // client-side logic handles state/navigation after success.
                    response.setStatus(HttpServletResponse.SC_CREATED); // 201 Created.
                }
            } catch (SQLException e) {
                e.printStackTrace();
                errorMessage = "Database error while creating item: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (IllegalArgumentException e) { // From Item bean validation.
                e.printStackTrace();
                errorMessage = "Invalid item data: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }

        // Step 8: Populate and send JSON response.
        if (errorMessage != null) {
            responseData.put("errorMessage", errorMessage);
            // Ensure an appropriate error status code is set if not already.
            if (response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
                 response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }
        response.getWriter().write(gson.toJson(responseData));
    }

    /**
     * Cleans up resources when the servlet is destroyed.
     * Closes the database connection.
     */
    @Override
    public void destroy() {
        ConnectionHandler.closeConnection(connection);
    }
}