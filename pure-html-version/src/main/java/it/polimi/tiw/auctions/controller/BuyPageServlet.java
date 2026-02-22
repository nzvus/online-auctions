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

import it.polimi.tiw.auctions.beans.Auction; // Import Auction bean
import it.polimi.tiw.auctions.beans.User;    // Import User bean
import it.polimi.tiw.auctions.dao.AuctionDAO; // Import AuctionDAO
import it.polimi.tiw.auctions.dao.ItemDAO;    // Import ItemDAO (needed for displaying item details in auctions)
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler
import it.polimi.tiw.auctions.utils.DateTimeUtils;   // Import DateTimeUtils for time remaining

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles requests for the "Buy" page of the online auction application.
 * This servlet allows users to search for open auctions based on keywords and
 * view a list of auctions they have previously won.
 * It interacts with {@link AuctionDAO} and {@link ItemDAO} to retrieve auction and item data.
 * The page is rendered using the "buy.html" Thymeleaf template.
 */
public class BuyPageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;      // Database connection.
    private TemplateEngine templateEngine;     // Thymeleaf template engine.
    private AuctionDAO auctionDAO;             // DAO for auction operations.
    private ItemDAO itemDAO;                   // DAO for item operations (to display items in auctions).

    /**
     * Default constructor.
     */
    public BuyPageServlet() {
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
        // Initialize DAOs with the connection.
        auctionDAO = new AuctionDAO(connection);
        itemDAO = new ItemDAO(connection);

        // Initialize Thymeleaf template engine.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setPrefix("/WEB-INF/templates/");
        templateResolver.setSuffix(".html");
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(templateResolver);
    }

    /**
     * Handles HTTP GET requests for the Buy page.
     * Retrieves search results for open auctions (if a keyword is provided)
     * and a list of auctions won by the current user.
     * Passes this data to the "buy.html" template for rendering.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get the current user from the session.
        // AuthenticationFilter should ensure the user is logged in.
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user"); // Assumes user is always present due to filter.

        // Step 2: Retrieve request parameters.
        // Get the search keyword, if any.
        String keyword = request.getParameter("keyword");
        // Get any error or success messages passed via redirect.
        String errorMessage = request.getParameter("errorMessage");
        String successMessage = request.getParameter("successMessage");

        // Step 3: Initialize lists to hold auction data.
        List<Auction> searchResults = Collections.emptyList(); // For auctions matching the keyword.
        List<Auction> wonAuctions = Collections.emptyList();   // For auctions won by the user.

        // Step 4: Fetch auction data from DAOs.
        try {
            // If a keyword is provided and not blank, search for open auctions.
            if (keyword != null && !keyword.isBlank()) {
                searchResults = auctionDAO.findOpenAuctionsByKeyword(keyword);
            }
            // Always fetch auctions won by the current user.
            wonAuctions = auctionDAO.findWonAuctionsByUser(user.id());
        } catch (SQLException e) {
            // Handle SQL exceptions during data retrieval.
            e.printStackTrace(); // Log error.
            // Set an error message to be displayed on the page.
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Error retrieving auction data from the database.";
        }

        // Step 5: Prepare data for the Thymeleaf template.
        // Create a WebContext.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
        WebContext ctx = new WebContext(application.buildExchange(request, response), request.getLocale());
        // Add data to the context.
        ctx.setVariable("user", user);                                      // Current user.
        ctx.setVariable("searchResults", searchResults);                    // List of search results.
        ctx.setVariable("wonAuctions", wonAuctions);                        // List of won auctions.
        ctx.setVariable("keyword", keyword);                                // The search keyword used (can be null).
        ctx.setVariable("errorMessage", errorMessage);                      // Error message, if any.
        ctx.setVariable("successMessage", successMessage);                  // Success message, if any.
        ctx.setVariable("itemDAO", itemDAO);                                // Pass ItemDAO to template for fetching items in auctions.
        ctx.setVariable("dateTimeUtils", new DateTimeUtils());              // Pass DateTimeUtils for formatting time remaining.
        ctx.setVariable("loginTimestamp", (Timestamp) session.getAttribute("loginTimestamp")); // User's login time.

        // Step 6: Render the "buy.html" template.
        String path = "buy";
        templateEngine.process(path, ctx, response.getWriter());
    }

    /**
     * Cleans up resources when the servlet is destroyed.
     * Closes the database connection.
     */
    @Override
    public void destroy() {
        // Close the database connection.
        ConnectionHandler.closeConnection(connection);
    }
}