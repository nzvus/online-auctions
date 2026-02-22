package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson; // For JSON response.

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig; // For FormData from client.
// No @WebServlet if defined in web.xml
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.auctions.beans.Auction; // Import Auction bean
import it.polimi.tiw.auctions.beans.Bid;     // Import Bid bean
import it.polimi.tiw.auctions.beans.User;    // Import User bean
import it.polimi.tiw.auctions.dao.AuctionDAO; // Import AuctionDAO
import it.polimi.tiw.auctions.dao.BidDAO;     // Import BidDAO
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler

/**
 * Handles requests to place a bid on an auction in the RIA version.
 * This servlet processes POST requests, typically AJAX calls using FormData from `offer.js`.
 * It validates the bid against auction rules (open, deadline, not self-bidding, bid amount)
 * and, if valid, records the bid in the database.
 * Responds with JSON indicating success or failure.
 * `@MultipartConfig` is used because the client might send data using `FormData`.
 */
@MultipartConfig // Ensures parameters are parsed correctly when client sends FormData.
public class PlaceBidServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;      // Database connection.
    private BidDAO bidDAO;                     // DAO for bid operations.
    private AuctionDAO auctionDAO;             // DAO for auction operations (for validation).

    /**
     * Default constructor.
     */
    public PlaceBidServlet() {
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
        bidDAO = new BidDAO(connection);
        auctionDAO = new AuctionDAO(connection);
    }

    /**
     * Handles HTTP POST requests to place a new bid.
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
        User currentUser = (User) session.getAttribute("user");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Gson gson = new Gson();
        Map<String, Object> responseData = new HashMap<>();

        if (currentUser == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized.
            responseData.put("errorMessage", "User not authenticated.");
            response.getWriter().write(gson.toJson(responseData));
            return;
        }

        // Step 2: Retrieve and validate request parameters.
        String auctionIdStr = request.getParameter("auctionId");
        String bidAmountStr = request.getParameter("bidAmount");
        String errorMessage = null;

        if (auctionIdStr == null || auctionIdStr.isBlank() || bidAmountStr == null || bidAmountStr.isBlank()) {
            errorMessage = "Auction ID and bid amount are required.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request.
        } else {
            try {
                // Parse parameters.
                int auctionId = Integer.parseInt(auctionIdStr);
                double bidAmount = Double.parseDouble(bidAmountStr);
                // Round bidAmount to 2 decimal places to avoid client-side floating point precision issues.
                bidAmount = Math.round(bidAmount * 100.0) / 100.0;

                // Fetch auction for validation.
                Auction auction = auctionDAO.findAuctionById(auctionId);

                // Step 3: Perform bid validation checks.
                if (auction == null) {
                    errorMessage = "Auction not found.";
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404 Not Found.
                } else if (auction.status() != Auction.AuctionStatus.OPEN) {
                    errorMessage = "This auction is not open for bidding.";
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden.
                } else if (auction.deadline().before(Timestamp.from(java.time.Instant.now()))) {
                    errorMessage = "This auction has already ended.";
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                } else if (auction.creatorUserId() == currentUser.id()) {
                    errorMessage = "You cannot bid on your own auction.";
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                } else {
                    // Step 3a: Determine minimum next bid.
                    Bid highestBid = bidDAO.findHighestBidForAuction(auctionId);
                    double minNextBid;
                    if (highestBid != null) {
                        minNextBid = highestBid.amount() + auction.minimumBidIncrement();
                    } else {
                        minNextBid = auction.initialPrice();
                    }
                    // Round for accurate comparison.
                    minNextBid = Math.round(minNextBid * 100.0) / 100.0;

                    // Step 3b: Check if bid amount meets minimum. Using a small epsilon for float comparison.
                    if (bidAmount < minNextBid - 0.001) {
                        errorMessage = "Your bid of €" + String.format("%.2f", bidAmount)
                                       + " must be at least €" + String.format("%.2f", minNextBid);
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    } else {
                        // Step 4: If all validations pass, record the bid.
                        Bid newBid = Bid.createBidForInsert(auctionId, currentUser.id(), bidAmount,
                                Timestamp.from(java.time.Instant.now()));
                        bidDAO.createBid(newBid);
                        responseData.put("successMessage", "Your bid of €" + String.format("%.2f", bidAmount)
                                + " has been placed successfully.");
                        response.setStatus(HttpServletResponse.SC_OK); // 200 OK.
                    }
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid auction ID or bid amount format.";
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } catch (SQLException e) {
                e.printStackTrace(); // Log SQL error.
                errorMessage = "Database error while placing bid: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500.
            } catch (IllegalArgumentException e) { // From Bid bean validation.
                errorMessage = "Invalid bid data: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }

        // Step 5: Populate and send JSON response.
        if (errorMessage != null) {
            responseData.put("errorMessage", errorMessage);
            // Ensure an appropriate error status code is set if not already.
            if (response.getStatus() == HttpServletResponse.SC_OK && errorMessage != null) {
                 response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // Default if not set by specific error.
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