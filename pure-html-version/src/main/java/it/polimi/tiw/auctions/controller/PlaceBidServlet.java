package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
// No @WebServlet annotation if defined in web.xml
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
 * Handles requests to place a bid on an auction.
 * This servlet processes POST requests, typically from a form on the "Offer" page.
 * It validates the bid amount against the auction's current state (open, deadline,
 * minimum next bid, not self-bidding) and, if valid, records the new bid in the database.
 * After processing, it redirects back to the "Offer" page for the same auction,
 * displaying a success or error message.
 */
public class PlaceBidServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;      // Database connection.
    private BidDAO bidDAO;                     // DAO for bid operations.
    private AuctionDAO auctionDAO;             // DAO for auction operations (to get auction details for validation).

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
     * Handles HTTP POST requests to place a bid.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get current user and request parameters.
        HttpSession session = request.getSession(false); // Assumes user is logged in (checked by filter).
        User currentUser = (User) session.getAttribute("user");
        String auctionIdStr = request.getParameter("auctionId"); // ID of the auction being bid on.
        String bidAmountStr = request.getParameter("bidAmount"); // The amount of the bid.

        // Step 2: Initialize variables for messages and redirection.
        String errorMessage = null;
        String successMessage = null;
        // Keep auctionIdStr for redirect URL, even if parsing fails or other errors occur.
        String redirectAuctionIdParam = auctionIdStr;

        // Step 3: Validate that auctionId and bidAmount parameters are provided.
        if (auctionIdStr == null || auctionIdStr.isBlank() || bidAmountStr == null || bidAmountStr.isBlank()) {
            errorMessage = "Auction ID and bid amount are required.";
        } else {
            try {
                // Parse auctionId and bidAmount.
                int auctionId = Integer.parseInt(auctionIdStr);
                double bidAmount = Double.parseDouble(bidAmountStr);
                // Round bidAmount to 2 decimal places to avoid precision issues from client-side floats
                bidAmount = Math.round(bidAmount * 100.0) / 100.0;


                // Fetch the auction details for validation.
                Auction auction = auctionDAO.findAuctionById(auctionId);

                // Step 4: Perform validation checks for placing the bid.
                if (auction == null) {
                    errorMessage = "Auction not found.";
                } else if (auction.status() != Auction.AuctionStatus.OPEN) {
                    // Auction must be open.
                    errorMessage = "This auction is not open for bidding.";
                } else if (auction.deadline().before(Timestamp.from(java.time.Instant.now()))) {
                    // Auction deadline must not have passed.
                    errorMessage = "This auction has already ended.";
                } else if (auction.creatorUserId() == currentUser.id()) {
                    // User cannot bid on their own auction.
                    errorMessage = "You cannot bid on your own auction.";
                } else {
                    // Step 4a: Determine the minimum next bid amount.
                    Bid highestBid = bidDAO.findHighestBidForAuction(auctionId);
                    double minNextBid;
                    if (highestBid != null) {
                        // If there's a highest bid, the next bid must be at least highestBid + minIncrement.
                        minNextBid = highestBid.amount() + auction.minimumBidIncrement();
                    } else {
                        // If no bids yet, the bid must be at least the initial price.
                        minNextBid = auction.initialPrice();
                    }
                    // Round minNextBid for accurate comparison
                    minNextBid = Math.round(minNextBid * 100.0) / 100.0;


                    // Step 4b: Validate the bid amount against the minimum next bid.
                    // Use a small tolerance (epsilon) for floating-point comparisons.
                    if (bidAmount < minNextBid - 0.001) {
                        errorMessage = "Your bid of €" + String.format("%.2f", bidAmount)
                                       + " must be at least €" + String.format("%.2f", minNextBid);
                    } else {
                        // Step 5: If all validations pass, create and record the new bid.
                        Bid newBid = Bid.createBidForInsert(auctionId, currentUser.id(), bidAmount,
                                Timestamp.from(java.time.Instant.now())); // Use current server time for bid timestamp.
                        bidDAO.createBid(newBid);
                        successMessage = "Your bid of €" + String.format("%.2f", bidAmount)
                                + " has been placed successfully.";
                    }
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid auction ID or bid amount format.";
            } catch (SQLException e) {
                e.printStackTrace(); // Log SQL error.
                errorMessage = "Database error while placing bid.";
            } catch (IllegalArgumentException e) { // Catch validation errors from Bid bean
                errorMessage = "Invalid bid data: " + e.getMessage();
            }
        }

        // Step 6: Redirect back to the OfferPageServlet for the same auction.
        // Construct the redirect URL, including auctionId and any messages.
        String redirectURL = request.getContextPath() + "/OfferPageServlet?auctionId="
                + (redirectAuctionIdParam != null ? redirectAuctionIdParam : "");
        // Append error or success messages, URL-encoded.
        if (errorMessage != null) {
            redirectURL += "&errorMessage=" + java.net.URLEncoder.encode(errorMessage, "UTF-8");
        }
        if (successMessage != null) {
            redirectURL += "&successMessage=" + java.net.URLEncoder.encode(successMessage, "UTF-8");
        }
        // Perform the redirect.
        response.sendRedirect(redirectURL);
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