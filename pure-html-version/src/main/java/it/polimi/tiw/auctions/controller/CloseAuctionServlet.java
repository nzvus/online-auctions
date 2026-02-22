package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
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
 * Handles requests to close an auction.
 * This servlet processes POST requests, typically from a form on the auction detail page.
 * It verifies that the current user is the creator of the auction, that the auction is open,
 * and that its deadline has passed. If all conditions are met, it closes the auction,
 * determining the winner and winning price based on the highest bid.
 * After processing, it redirects back to the auction detail page with a success or error message.
 */
public class CloseAuctionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;      // Database connection.
    private AuctionDAO auctionDAO;             // DAO for auction operations.
    private BidDAO bidDAO;                     // DAO for bid operations (to find the highest bid).

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
        // Step 1: Get current user and auction ID from request.
        HttpSession session = request.getSession(false); // Assumes user is logged in (checked by filter).
        User currentUser = (User) session.getAttribute("user");
        String auctionIdStr = request.getParameter("auctionId"); // ID of the auction to close.

        // Step 2: Initialize variables for messages and redirection.
        String errorMessage = null;
        String successMessage = null;
        // Keep auctionIdStr for redirect URL, even if parsing fails or other errors occur.
        String redirectAuctionIdParam = auctionIdStr;

        // Step 3: Validate auction ID parameter.
        if (auctionIdStr == null || auctionIdStr.isBlank()) {
            errorMessage = "Auction ID is missing for closing.";
        } else {
            try {
                // Parse auctionId to integer.
                int auctionId = Integer.parseInt(auctionIdStr);
                // Fetch the auction to be closed.
                Auction auction = auctionDAO.findAuctionById(auctionId);

                // Step 4: Perform validation checks before closing.
                if (auction == null) {
                    errorMessage = "Auction to close not found.";
                } else if (auction.creatorUserId() != currentUser.id()) {
                    // Check if the current user is the creator of the auction.
                    errorMessage = "You are not authorized to close this auction.";
                } else if (auction.status() != Auction.AuctionStatus.OPEN) {
                    // Check if the auction is currently open.
                    errorMessage = "Auction is not open, cannot be closed through this action.";
                } else if (auction.deadline().after(Timestamp.from(java.time.Instant.now()))) {
                    // Check if the auction's deadline has passed.
                    // Compare with current server time.
                    errorMessage = "Auction deadline has not passed yet.";
                } else {
                    // Step 5: If all checks pass, proceed to close the auction.
                    // Find the highest bid to determine the winner and winning price.
                    Bid highestBid = bidDAO.findHighestBidForAuction(auctionId);
                    Integer winnerId = null;
                    Double winningPrice = null;
                    // If there was a highest bid, set winner and price.
                    if (highestBid != null) {
                        winnerId = highestBid.userId();
                        winningPrice = highestBid.amount();
                    }

                    // Call DAO to close the auction.
                    // Pass current timestamp to ensure consistent deadline check in DAO if re-verified.
                    int rowsAffected = auctionDAO.closeAuction(auctionId, winnerId, winningPrice,
                            Timestamp.from(java.time.Instant.now()));
                    // Check if the auction was successfully closed.
                    if (rowsAffected > 0) {
                        successMessage = "Auction closed successfully.";
                        // Set a session attribute to indicate the last action related to selling,
                        // which might be used by the RIA version for navigation.
                        session.setAttribute("lastAction", "sell_related");
                    } else {
                        errorMessage = "Failed to close the auction. It might have already been closed or conditions not met.";
                    }
                }
            } catch (NumberFormatException e) {
                // Handle invalid auctionId format.
                errorMessage = "Invalid Auction ID format for closing.";
            } catch (SQLException e) {
                // Handle database errors.
                e.printStackTrace(); // Log error.
                errorMessage = "Database error while closing auction.";
            }
        }

        // Step 6: Redirect back to the AuctionDetailServlet page.
        // Construct the redirect URL, including the auctionId and any messages.
        String redirectURL = request.getContextPath() + "/AuctionDetailServlet?auctionId="
                + (redirectAuctionIdParam != null ? redirectAuctionIdParam : ""); // Use the original auctionId string.
        // Append error or success messages to the URL, URL-encoded.
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