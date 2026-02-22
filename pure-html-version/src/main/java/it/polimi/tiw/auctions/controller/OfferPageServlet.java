package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import it.polimi.tiw.auctions.beans.Auction;   // Import Auction bean
import it.polimi.tiw.auctions.beans.Bid;       // Import Bid bean
import it.polimi.tiw.auctions.beans.Item;      // Import Item bean
import it.polimi.tiw.auctions.beans.User;       // Import User bean
import it.polimi.tiw.auctions.dao.AuctionDAO;  // Import AuctionDAO
import it.polimi.tiw.auctions.dao.BidDAO;      // Import BidDAO
import it.polimi.tiw.auctions.dao.ItemDAO;     // Import ItemDAO
import it.polimi.tiw.auctions.dao.UserDAO;     // Import UserDAO
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler
import it.polimi.tiw.auctions.utils.DateTimeUtils;   // Import DateTimeUtils

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles requests for displaying the page where a user can place a bid on an auction.
 * This servlet retrieves details of a specific auction (items, current bids) and
 * presents a form for the user to submit a new bid if the auction is open,
 * not created by the current user, and has not yet ended.
 * The page is rendered using the "offer.html" Thymeleaf template.
 */
public class OfferPageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;        // Unique ID for serialization.
    private Connection connection = null;                   // Database connection.
    private TemplateEngine templateEngine;                  // Thymeleaf template engine.
    private AuctionDAO auctionDAO;                          // DAO for auction operations.
    private ItemDAO itemDAO;                                // DAO for item operations.
    private BidDAO bidDAO;                                  // DAO for bid operations.
    private UserDAO userDAO;                                // DAO for user operations (e.g., getting bidder usernames).

    /**
     * Default constructor.
     */
    public OfferPageServlet() {
        super();
    }

    /**
     * Initializes the servlet.
     * Establishes a database connection and configures the Thymeleaf template engine and DAOs.
     *
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init() throws ServletException {
        // Get ServletContext for application resources.
        ServletContext servletContext = getServletContext();
        // Obtain database connection.
        connection = ConnectionHandler.getConnection(servletContext);
        // Initialize DAOs.
        auctionDAO = new AuctionDAO(connection);
        itemDAO = new ItemDAO(connection);
        bidDAO = new BidDAO(connection);
        userDAO = new UserDAO(connection);

        // Initialize Thymeleaf.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setPrefix("/WEB-INF/templates/");
        templateResolver.setSuffix(".html");
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(templateResolver);
    }

    /**
     * Handles HTTP GET requests for the offer page.
     * Retrieves and displays details of a specific auction, along with a bid form if applicable.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get current user from session. AuthenticationFilter ensures user is logged in.
        HttpSession session = request.getSession(false);
        User currentUser = (User) session.getAttribute("user");

        // Step 2: Retrieve request parameters.
        String auctionIdStr = request.getParameter("auctionId"); // ID of the auction to bid on.
        // Get any error or success messages passed via redirect (e.g., after a bid attempt).
        String errorMessage = request.getParameter("errorMessage");
        String successMessage = request.getParameter("successMessage");

        // Step 3: Initialize variables to hold auction details.
        Auction auction = null;
        List<Item> itemsInAuction = Collections.emptyList();
        List<Bid> bidsForAuction = Collections.emptyList();
        Bid highestBid = null;
        Map<Integer, String> bidderUsernames = Collections.emptyMap();

        // Step 4: Validate auctionId and fetch auction data, applying bidding rules.
        if (auctionIdStr == null || auctionIdStr.isBlank()) {
            // If auctionId is missing, append to error message.
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Auction ID is missing.";
        } else {
            try {
                // Parse auctionId to integer.
                int auctionId = Integer.parseInt(auctionIdStr);
                // Fetch the auction details.
                auction = auctionDAO.findAuctionById(auctionId);

                if (auction == null) {
                    errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Auction not found.";
                } else if (auction.status() != Auction.AuctionStatus.OPEN) {
                    // Auction must be open for bidding.
                    errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "This auction is not open for bidding.";
                    auction = null; // Prevent displaying details/bid form if not open.
                } else if (auction.deadline().before(Timestamp.from(java.time.Instant.now()))) {
                    // Auction deadline must not have passed.
                    errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "This auction has already ended.";
                    auction = null; // Prevent displaying if ended.
                } else if (auction.creatorUserId() == currentUser.id()) {
                    // User cannot bid on their own auction.
                    errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "You cannot bid on your own auction.";
                    auction = null; // Prevent displaying if user is the creator.
                } else {
                    // If all checks pass, fetch items and bids for the auction.
                    itemsInAuction = itemDAO.findItemsByAuctionId(auctionId);
                    bidsForAuction = bidDAO.findBidsByAuctionId(auctionId);
                    highestBid = bidDAO.findHighestBidForAuction(auctionId);

                    // If there are bids, fetch usernames of bidders.
                    if (!bidsForAuction.isEmpty()) {
                        bidderUsernames = bidsForAuction.stream()
                                .map(Bid::userId).distinct()
                                .collect(Collectors.toMap(
                                        userId -> userId,
                                        userId -> {
                                            try {
                                                User bidder = userDAO.findUserById(userId);
                                                return bidder != null ? bidder.username() : "Unknown";
                                            } catch (SQLException e) {
                                                e.printStackTrace();
                                                return "Error";
                                            }
                                        }
                                ));
                    }
                }
            } catch (NumberFormatException e) {
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Invalid Auction ID format.";
            } catch (SQLException e) {
                e.printStackTrace();
                errorMessage = (errorMessage == null ? "" : errorMessage + " ")
                        + "Database error retrieving auction details for offer page.";
            }
        }

        // Step 5: Prepare data for Thymeleaf template.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
        WebContext ctx = new WebContext(application.buildExchange(request, response), request.getLocale());
        ctx.setVariable("user", currentUser);
        ctx.setVariable("auction", auction); // Will be null if any validation failed.
        ctx.setVariable("itemsInAuction", itemsInAuction);
        ctx.setVariable("bidsForAuction", bidsForAuction);
        ctx.setVariable("highestBid", highestBid);
        ctx.setVariable("errorMessage", errorMessage);
        ctx.setVariable("successMessage", successMessage);
        ctx.setVariable("dateTimeUtils", new DateTimeUtils());
        ctx.setVariable("loginTimestamp", (Timestamp) session.getAttribute("loginTimestamp"));
        ctx.setVariable("bidderUsernames", bidderUsernames);
        ctx.setVariable("userDAO", userDAO); // For fetching creator username in template.

        // Step 6: Render the "offer.html" template.
        String path = "offer";
        templateEngine.process(path, ctx, response.getWriter());
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