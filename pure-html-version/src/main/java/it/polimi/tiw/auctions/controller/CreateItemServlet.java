package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig; // For handling multipart/form-data (file uploads)
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part; // For accessing uploaded file parts

import it.polimi.tiw.auctions.beans.Item;    // Import Item bean
import it.polimi.tiw.auctions.beans.User;   // Import User bean
import it.polimi.tiw.auctions.dao.ItemDAO; // Import ItemDAO
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler

/**
 * Handles requests for creating new items to be put up for auction.
 * This servlet processes POST requests, typically from a form on the "Sell" page,
 * which includes item details and an image upload.
 * It validates the input, checks for item code uniqueness, and saves the new item to the database.
 * After processing, it redirects back to the "Sell" page with a success or error message.
 * The {@code @MultipartConfig} annotation is essential for handling file uploads.
 */
@MultipartConfig // Enables this servlet to handle multipart/form-data requests (for file uploads).
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
     * @throws ServletException if a servlet-specific error occurs or if request is not multipart.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get current user from session. AuthenticationFilter ensures user is logged in.
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        // Step 2: Retrieve item details from request parameters and file part.
        String itemCode = request.getParameter("itemCode");
        String itemName = request.getParameter("itemName");
        String description = request.getParameter("description");
        String basePriceStr = request.getParameter("basePrice");
        Part imagePart = request.getPart("image"); // Retrieves the uploaded file part.

        // Step 3: Initialize variables for messages and validation.
        String successMessage = null;
        String errorMessage = null;

        // Step 4: Perform initial validation for presence of required fields.
        if (itemCode == null || itemCode.isBlank() ||
            itemName == null || itemName.isBlank() ||
            description == null || description.isBlank() ||
            basePriceStr == null || basePriceStr.isBlank() ||
            imagePart == null || imagePart.getSize() == 0) {
            errorMessage = "All fields, including an image, are required for creating an item.";
        }

        // Step 5: Validate base price format and value.
        double basePrice = 0;
        if (errorMessage == null) { // Proceed only if previous checks passed.
            try {
                basePrice = Double.parseDouble(basePriceStr);
                if (basePrice <= 0) { // Base price must be positive.
                    errorMessage = "Base price must be a positive value.";
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid base price format. Please enter a valid number.";
            }
        }

        // Step 6: Validate and process the uploaded image.
        byte[] imageBytes = null; // To store the image data as a byte array.
        if (errorMessage == null) { // Proceed only if previous checks passed.
            // Get the submitted file name from the Part.
            String submittedFileName = imagePart.getSubmittedFileName();
            if (submittedFileName == null || submittedFileName.isBlank()) {
                errorMessage = "Image file name is missing. Please select an image.";
            } else {
                // Get the content type of the uploaded file.
                String contentType = imagePart.getContentType();
                // Validate that the content type indicates an image.
                if (contentType == null || !contentType.startsWith("image/")) {
                    errorMessage = "Invalid image file type. Only image files (e.g., JPG, PNG, GIF) are allowed.";
                } else {
                    // Read the image file content into a byte array.
                    try (InputStream fileContent = imagePart.getInputStream()) {
                        imageBytes = fileContent.readAllBytes();
                        if (imageBytes.length == 0) { // Check if the file is empty after reading.
                            errorMessage = "Image file is empty.";
                        }
                    } catch (IOException e) {
                        e.printStackTrace(); // Log I/O error.
                        errorMessage = "Error reading image file.";
                    }
                }
            }
        }

        // Step 7: If all validations pass, attempt to create the item in the database.
        if (errorMessage == null) {
            try {
                // Check if the item code already exists to ensure uniqueness.
                if (itemDAO.itemCodeExists(itemCode)) {
                    errorMessage = "Item code '" + itemCode + "' already exists. Please choose a unique code.";
                } else {
                    // Create an Item bean with the validated data.
                    Item newItem = Item.createItemForInsert(itemCode, itemName, description, imageBytes, basePrice,
                            user.id()); // Associate the item with the current user.
                    // Call DAO to persist the new item.
                    itemDAO.createItem(newItem);
                    successMessage = "Item '" + itemName + "' created successfully!";
                    // Set session attribute for RIA version compatibility.
                    session.setAttribute("lastAction", "sell_related");
                }
            } catch (SQLException e) {
                // Handle database errors during item creation.
                e.printStackTrace(); // Log SQL error.
                errorMessage = "Database error while creating item: " + e.getMessage();
            } catch (IllegalArgumentException e) {
                // Handle validation errors from the Item bean constructor.
                errorMessage = "Invalid item data: " + e.getMessage();
            }
        }

        // Step 8: Redirect back to the SellPageServlet.
        // Construct the redirect URL with appropriate messages.
        String redirectPath = request.getContextPath() + "/SellPageServlet";
        if (errorMessage != null) {
            redirectPath += "?errorMessage=" + java.net.URLEncoder.encode(errorMessage, "UTF-8");
        } else if (successMessage != null) {
            redirectPath += "?successMessage=" + java.net.URLEncoder.encode(successMessage, "UTF-8");
        }
        // Perform the redirect.
        response.sendRedirect(redirectPath);
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