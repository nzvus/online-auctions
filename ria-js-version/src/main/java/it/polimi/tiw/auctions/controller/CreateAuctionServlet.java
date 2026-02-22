package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList; // Added for itemIds list
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson; // For JSON response.

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig; // For FormData from client.
// No @WebServlet if defined in web.xml
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
 * Handles requests for creating new auctions in the RIA version.
 * This servlet processes POST requests, typically AJAX calls using FormData from `sellPage.js`.
 * It validates input data (selected items, minimum bid increment, deadline), calculates
 * the initial auction price based on the sum of base prices of selected items,
 * and then creates the auction in the database.
 * Responds with JSON indicating success or failure.
 * `@MultipartConfig` is used because the client might send data using `FormData`.
 */
@MultipartConfig // Ensures parameters are parsed correctly when client sends FormData.
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
        // Step 1: Authenticate user and prepare JSON response.
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Gson gson = new Gson();
        Map<String, Object> responseData = new HashMap<>();

        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized.
            responseData.put("errorMessage", "User not authenticated.");
            response.getWriter().write(gson.toJson(responseData));
            return;
        }

        // Step 2: Retrieve request parameters for auction creation.
        String[] itemIdsStr = request.getParameterValues("itemIds"); // `getParameterValues` for multi-select.
        String minIncrementStr = request.getParameter("minIncrement");
        String deadlineStr = request.getParameter("deadline");       // Expecting "yyyy-MM-ddTHH:mm" format.

        // Step 3: Initialize variables for messages and validation.
        String errorMessage = null;

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

        // Step 5: If initial checks pass, proceed with detailed validation and data processing.
        List<Integer> itemIds = new ArrayList<>(); // Initialize as ArrayList.
        double minIncrement = 0;
        Timestamp deadline = null;
        double initialPrice = 0; // Calculated from selected items.

        // Step 5a: Validate selected item IDs and calculate initial price.
        if (errorMessage == null) {
            try {
                // Convert item ID strings to integers.
                itemIds = Arrays.stream(itemIdsStr)
                                .map(Integer::parseInt)
                                .collect(Collectors.toList());
                if (itemIds.isEmpty()) { // Should be caught by itemIdsStr.length == 0.
                    errorMessage = "No valid items selected.";
                } else {
                    // Validate each selected item and sum base prices.
                    for (int itemId : itemIds) {
                        Item item = itemDAO.findItemById(itemId);
                        if (item == null || item.sellerUserId() != user.id()) {
                            errorMessage = "Invalid item selected or item does not belong to you (ID: " + itemId + ").";
                            break;
                        }
                        // Re-fetch available items to ensure current availability.
                        List<Item> availableItems = itemDAO.findAvailableItemsBySellerId(user.id());
                        boolean isAvailable = availableItems.stream().anyMatch(availItem -> availItem.id() == itemId);
                        if (!isAvailable) {
                            errorMessage = "Item '" + (item.name() != null ? item.name() : "ID: " + itemId)
                                    + "' is already in an open auction or unavailable.";
                            break;
                        }
                        initialPrice += item.basePrice(); // Sum base prices.
                    }
                }
            } catch (NumberFormatException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Invalid item ID format.";
            } catch (SQLException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Database error validating items: " + e.getMessage();
                e.printStackTrace();
            }
        }

        // Step 5b: Validate minimum bid increment.
        if (errorMessage == null) {
            try {
                minIncrement = Double.parseDouble(minIncrementStr);
                if (minIncrement < 1) { // Must be at least 1 euro.
                    errorMessage = "Minimum bid increment must be at least 1 euro.";
                }
                // Specification: "numero intero di euro".
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
                // HTML datetime-local input provides "yyyy-MM-ddTHH:mm".
                LocalDateTime localDeadline = LocalDateTime.parse(deadlineStr); 
                // Definisci il fuso orario in cui l'input dell'utente deve essere interpretato.
                // Se il tuo server Tomcat gira con il fuso orario di Roma (o il fuso "logico" della tua app è Roma),
                // puoi usare ZoneId.of("Europe/Rome"). Se vuoi usare il default della JVM del server:
                ZoneId applicationZoneId = ZoneId.systemDefault(); // O più esplicitamente: ZoneId.of("Europe/Rome");

                // Crea un ZonedDateTime dall'input locale nel fuso orario dell'applicazione
                ZonedDateTime zonedDeadlineInAppZone = localDeadline.atZone(applicationZoneId);

                // Converti questo istante in UTC
                ZonedDateTime utcDeadlineZoned = zonedDeadlineInAppZone.withZoneSameInstant(ZoneId.of("UTC"));

                // Crea il java.sql.Timestamp dall'istante UTC per il salvataggio nel DB
                deadline = Timestamp.from(utcDeadlineZoned.toInstant());
                // --- FINE BLOCCO DI MODIFICA PER DEADLINE ---


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
                Timestamp creationTimestamp = Timestamp.from(java.time.Instant.now());; // Current server time.
                Auction newAuction = Auction.createAuctionForInsert(initialPrice, minIncrement, deadline,
                        creationTimestamp, user.id());
                auctionDAO.createAuction(newAuction, itemIds); // Persist auction and item links.
                responseData.put("successMessage", "Auction created successfully!");
                // Client-side (JS) will handle updating its view or local storage (e.g., 'lastAction').
                response.setStatus(HttpServletResponse.SC_CREATED); // 201 Created.
            } catch (SQLException e) {
                e.printStackTrace();
                errorMessage = "Database error while creating auction: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (IllegalArgumentException e) { // From Auction bean validation.
                e.printStackTrace();
                errorMessage = "Error creating auction due to invalid data: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }

        // Step 7: Populate and send JSON response.
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