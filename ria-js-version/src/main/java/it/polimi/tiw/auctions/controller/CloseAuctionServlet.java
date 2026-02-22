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
 * Handles requests to close an auction in the RIA version.
 * This servlet processes POST requests, typically AJAX calls from `auctionDetail.js`,
 * when the auction creator attempts to close their auction.
 * It performs validations: user is creator, auction is open, deadline has passed.
 * If valid, it closes the auction, determining winner and price from bids.
 * Responds with JSON indicating success or failure.
 * `@MultipartConfig` is used because the client might send data using `FormData`.
 */
@MultipartConfig // Ensures parameters are parsed correctly when client sends FormData.
public class CloseAuctionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;      // Database connection.
    private AuctionDAO auctionDAO;             // DAO for auction operations.
    private BidDAO bidDAO;                     // DAO for bid operations (to find highest bid).

    /**
     * Default constructor.
     */
    public CloseAuctionServlet() {
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
        bidDAO = new BidDAO(connection);
    }

    /**
     * Handles HTTP POST requests to close an auction.
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

        // Step 2: Retrieve and validate the "auctionId" request parameter.
        String auctionIdStr = request.getParameter("auctionId"); // ID of the auction to close.
        String errorMessage = null;

        if (auctionIdStr == null || auctionIdStr.isBlank()) {
            errorMessage = "Auction ID is missing for closing.";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request.
        } else {
            try {
                // Parse auctionId.
                int auctionId = Integer.parseInt(auctionIdStr);
                // Fetch the auction to be closed.
                Auction auction = auctionDAO.findAuctionById(auctionId);

                // Step 3: Perform validation checks before closing.
                if (auction == null) {
                    errorMessage = "Auction to close not found.";
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404 Not Found.
                } else if (auction.creatorUserId() != currentUser.id()) {
                    // User must be the creator.
                    errorMessage = "You are not authorized to close this auction.";
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden.
                } else if (auction.status() != Auction.AuctionStatus.OPEN) {
                    // Auction must be currently open.
                    errorMessage = "Auction is not open, cannot be closed now.";
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN); // Or SC_CONFLICT (409).
                } else if (auction.deadline().after(Timestamp.from(java.time.Instant.now()))) {
                    // Auction deadline must have passed.
                    errorMessage = "Auction deadline has not passed yet.";
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                } else {
                    // Step 4: If all checks pass, determine winner and close auction.
                    Bid highestBid = bidDAO.findHighestBidForAuction(auctionId);
                    Integer winnerId = null;
                    Double winningPrice = null;
                    if (highestBid != null) {
                        winnerId = highestBid.userId();
                        winningPrice = highestBid.amount();
                    }

                    // Attempt to close the auction in the database.
                    int rowsAffected = auctionDAO.closeAuction(auctionId, winnerId, winningPrice,
                            Timestamp.from(java.time.Instant.now()));
                    if (rowsAffected > 0) {
                        responseData.put("successMessage", "Auction closed successfully.");
                        // Client-side JS will typically re-fetch auction details or navigate.
                        response.setStatus(HttpServletResponse.SC_OK); // 200 OK.
                    } else {
                        errorMessage = "Failed to close the auction. It might have already been closed or conditions not met.";
                        response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict or another suitable error.
                    }
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid Auction ID format for closing.";
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } catch (SQLException e) {
                e.printStackTrace(); // Log SQL error.
                errorMessage = "Database error while closing auction: " + e.getMessage();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 Internal Server Error.
            }
        }

        // Step 5: Populate and send JSON response.
        if (errorMessage != null) {
            responseData.put("errorMessage", errorMessage);
            // If status hasn't been set by a specific error, default to Bad Request for client-side errors.
            if (response.getStatus() == HttpServletResponse.SC_OK && errorMessage != null) {
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