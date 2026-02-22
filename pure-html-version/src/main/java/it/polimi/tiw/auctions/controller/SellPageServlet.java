package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import it.polimi.tiw.auctions.beans.Auction;   // Import Auction bean
import it.polimi.tiw.auctions.beans.Item;      // Import Item bean
import it.polimi.tiw.auctions.beans.User;       // Import User bean
import it.polimi.tiw.auctions.dao.AuctionDAO;  // Import AuctionDAO
import it.polimi.tiw.auctions.dao.BidDAO;      // Import BidDAO (for displaying highest bids)
import it.polimi.tiw.auctions.dao.ItemDAO;     // Import ItemDAO
import it.polimi.tiw.auctions.dao.UserDAO;     // Import UserDAO (for displaying winner usernames)
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler
import it.polimi.tiw.auctions.utils.DateTimeUtils;   // Import DateTimeUtils

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles requests for the "Sell" page of the online auction application.
 * This servlet displays items available for the user to sell, forms for creating new items
 * and new auctions, and lists of the user's open and closed auctions.
 * It interacts with various DAOs (Item, Auction, Bid, User) to retrieve and display data.
 * The page is rendered using the "sell.html" Thymeleaf template.
 */
public class SellPageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;        // Unique ID for serialization.
    private Connection connection = null;                   // Database connection.
    private TemplateEngine templateEngine;                  // Thymeleaf template engine.
    private ItemDAO itemDAO;                                // DAO for item operations.
    private AuctionDAO auctionDAO;                          // DAO for auction operations.
    private BidDAO bidDAO;                                  // DAO for bid operations (to get highest bids).
    private UserDAO userDAO;                                // DAO for user operations (to get winner usernames).

    /**
     * Default constructor.
     */
    public SellPageServlet() {
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
        itemDAO = new ItemDAO(connection);
        auctionDAO = new AuctionDAO(connection);
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
     * Handles HTTP GET requests for the Sell page.
     * Retrieves and displays user's available items, open auctions, and closed auctions.
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
        User user = (User) session.getAttribute("user");

        // Step 2: Initialize lists to hold data for the page.
        List<Auction> openAuctions = Collections.emptyList();   // User's open auctions.
        List<Auction> closedAuctions = Collections.emptyList(); // User's closed auctions.
        List<Item> availableItems = Collections.emptyList();    // User's items available for new auctions.
        // Get any error or success messages passed via redirect.
        String errorMessage = request.getParameter("errorMessage");
        String successMessage = request.getParameter("successMessage");

        // Step 3: Fetch data from DAOs.
        try {
            // Fetch open auctions created by the current user.
            openAuctions = auctionDAO.findAuctionsByCreatorAndStatus(user.id(), Auction.AuctionStatus.OPEN);
            // Fetch closed auctions created by the current user.
            closedAuctions = auctionDAO.findAuctionsByCreatorAndStatus(user.id(), Auction.AuctionStatus.CLOSED);
            // Fetch items available for the current user to sell.
            availableItems = itemDAO.findAvailableItemsBySellerId(user.id());
        } catch (SQLException e) {
            // Handle database errors.
            e.printStackTrace(); // Log error.
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Error retrieving data from the database.";
        }

        // Step 4: Prepare data for Thymeleaf template.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
        WebContext ctx = new WebContext(application.buildExchange(request, response), request.getLocale());
        // Add retrieved data and utility objects to the context.
        ctx.setVariable("user", user);
        ctx.setVariable("openAuctions", openAuctions);
        ctx.setVariable("closedAuctions", closedAuctions);
        ctx.setVariable("availableItems", availableItems);
        ctx.setVariable("errorMessage", errorMessage);
        ctx.setVariable("successMessage", successMessage);
        ctx.setVariable("dateTimeUtils", new DateTimeUtils()); // For formatting time remaining.
        ctx.setVariable("loginTimestamp", (Timestamp) session.getAttribute("loginTimestamp")); // User's login time.
        // Pass DAOs needed by the template to display related information (e.g., items in auction, highest bid).
        ctx.setVariable("itemDAO", itemDAO);
        ctx.setVariable("bidDAO", bidDAO);
        ctx.setVariable("userDAO", userDAO);

        // Step 5: Render the "sell.html" template.
        String path = "sell";
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