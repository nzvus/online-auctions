package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException; // For parsing deadline string
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.auctions.beans.Auction;   // Import Auction bean
import it.polimi.tiw.auctions.beans.Item;      // Import Item bean
import it.polimi.tiw.auctions.beans.User;       // Import User bean
import it.polimi.tiw.auctions.dao.AuctionDAO;  // Import AuctionDAO
import it.polimi.tiw.auctions.dao.ItemDAO;     // Import ItemDAO
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler

/**
 * Handles requests for creating new auctions.
 * This servlet processes POST requests, typically from a form on the "Sell" page.
 * It validates the input data (selected items, minimum bid increment, deadline),
 * calculates the initial price based on the sum of base prices of selected items,
 * and then creates the auction in the database.
 * After processing, it redirects back to the "Sell" page with a success or error message.
 */
public class CreateAuctionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;        // Unique ID for serialization.
    private Connection connection = null;                   // Database connection.
    private AuctionDAO auctionDAO;                          // DAO for auction operations.
    private ItemDAO itemDAO;                                // DAO for item operations.

    /**
     * Default constructor.
     */
    public CreateAuctionServlet() {
        super();
    }

    /**
     * Initializes the servlet.
     * Establishes a database connection and initializes DAOs.
     *
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init() throws ServletException {
        // Get ServletContext for application resources.
        ServletContext context = getServletContext();
        // Obtain database connection.
        connection = ConnectionHandler.getConnection(context);
        // Initialize DAOs.
        auctionDAO = new AuctionDAO(connection);
        itemDAO = new ItemDAO(connection);
    }

    /**
     * Handles HTTP POST requests to create a new auction.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get current user from session. AuthenticationFilter ensures user is logged in.
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        // Step 2: Retrieve request parameters for auction creation.
        String[] itemIdsStr = request.getParameterValues("itemIds"); // IDs of items to include in the auction.
        String minIncrementStr = request.getParameter("minIncrement"); // Minimum bid increment.
        String deadlineStr = request.getParameter("deadline");         // Auction deadline string (e.g., from datetime-local input).

        // Step 3: Initialize variables for messages and validation.
        String errorMessage = null;
        String successMessage = null;

        // Step 4: Perform initial validation for presence of required parameters.
        if (itemIdsStr == null || itemIdsStr.length == 0) {
            errorMessage = "You must select at least one item for the auction.";
        }
        if (minIncrementStr == null || minIncrementStr.isBlank()) {
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Minimum bid increment is required.";
        }
        if (deadlineStr == null || deadlineStr.isBlank()) {
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Auction deadline is required.";
        }

        // Step 5: If initial checks pass, proceed with more detailed validation and data processing.
        List<Integer> itemIds = new ArrayList<>();
        double minIncrement = 0;
        Timestamp deadline = null;
        double initialPrice = 0; // To be calculated from selected items' base prices.

        // Step 5a: Validate selected item IDs and calculate initial price.
        if (errorMessage == null) {
            try {
                // Convert item ID strings to integers.
                itemIds = Arrays.stream(itemIdsStr)
                                .map(Integer::parseInt)
                                .collect(Collectors.toList());
                // Ensure at least one valid item ID was parsed.
                if (itemIds.isEmpty()) {
                    errorMessage = "No valid items selected.";
                } else {
                    // Iterate through selected item IDs to validate them and sum their base prices.
                    for (int itemId : itemIds) {
                        Item item = itemDAO.findItemById(itemId);
                        // Check if item exists and belongs to the current user.
                        if (item == null || item.sellerUserId() != user.id()) {
                            errorMessage = "Invalid item selected or item does not belong to you (ID: " + itemId + ").";
                            break; // Stop further processing if an invalid item is found.
                        }
                        // Check if the item is available (not already in another open auction or sold).
                        List<Item> availableItems = itemDAO.findAvailableItemsBySellerId(user.id());
                        boolean isAvailable = availableItems.stream().anyMatch(availItem -> availItem.id() == itemId);
                        if (!isAvailable) {
                            errorMessage = "Item '" + (item.name() != null ? item.name() : "ID: " + itemId)
                                    + "' is already in an open auction or otherwise unavailable.";
                            break; // Stop if an unavailable item is found.
                        }
                        // Add the item's base price to the auction's initial price.
                        initialPrice += item.basePrice();
                    }
                }
            } catch (NumberFormatException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Invalid item ID format.";
            } catch (SQLException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Database error validating items.";
                e.printStackTrace(); // Log SQL error.
            }
        }

        // Step 5b: Validate minimum bid increment.
        if (errorMessage == null) {
            try {
                minIncrement = Double.parseDouble(minIncrementStr);
                // As per specification, minimum increment must be at least 1 (integer euro).
                if (minIncrement < 1) {
                    errorMessage = "Minimum bid increment must be at least 1 euro.";
                }
                // Ensure it's an integer value.
                if (minIncrement != Math.floor(minIncrement)) {
                    errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Minimum bid increment must be a whole number of euros.";
                }
            } catch (NumberFormatException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Invalid minimum bid increment format.";
            }
        }

        // Step 5c: Validate deadline.
        if (errorMessage == null) {
            try {
                // Parse the deadline string (from datetime-local input) to LocalDateTime.
                LocalDateTime localDeadline = LocalDateTime.parse(deadlineStr); 
                ZoneId applicationZoneId = ZoneId.systemDefault(); // O più esplicitamente: ZoneId.of("Europe/Rome");
                // Crea un ZonedDateTime dall'input locale nel fuso orario dell'applicazione
                ZonedDateTime zonedDeadlineInAppZone = localDeadline.atZone(applicationZoneId);
                // Converti questo istante in UTC
                ZonedDateTime utcDeadlineZoned = zonedDeadlineInAppZone.withZoneSameInstant(ZoneId.of("UTC"));
                // Crea il java.sql.Timestamp dall'istante UTC per il salvataggio nel DB
                deadline = Timestamp.from(utcDeadlineZoned.toInstant());

                // Il controllo della deadline deve usare l'ora corrente UTC
                // Confronta con (ora UTC attuale + 3 minuti)
                if (deadline.before(Timestamp.from(java.time.Instant.now().plusSeconds(3 * 60)))) {
                    errorMessage = "Deadline must be in the future (at least 3 minutes from now).";
                }
            } catch (DateTimeParseException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Invalid deadline format. Expected yyyy-MM-ddTHH:mm.";
            } catch (Exception e) { // Cattura più genericamente per robustezza
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Error processing deadline value.";
                e.printStackTrace(); // Logga l'eccezione per debug
            }
        }

        // Step 6: If all validations pass, create the auction.
        if (errorMessage == null) {
            try {
                // Set the creation timestamp to the current time.
                Timestamp creationTimestamp = Timestamp.from(java.time.Instant.now());;
                // Create an Auction bean for insertion.
                Auction newAuction = Auction.createAuctionForInsert(initialPrice, minIncrement, deadline,
                        creationTimestamp, user.id());
                // Call DAO to create the auction and link items.
                auctionDAO.createAuction(newAuction, itemIds);
                successMessage = "Auction created successfully!";
                // Set session attribute for RIA version compatibility (client might check this).
                //session.setAttribute("lastAction", "sell_related");
            } catch (SQLException e) {
                // Handle database errors during auction creation.
                e.printStackTrace(); // Log SQL error.
                errorMessage = "Database error while creating auction: " + e.getMessage();
            } catch (IllegalArgumentException e) {
                // Handle validation errors from the Auction bean constructor.
                errorMessage = "Error creating auction: " + e.getMessage();
                e.printStackTrace();
            }
        }

        // Step 7: Redirect back to the SellPageServlet.
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