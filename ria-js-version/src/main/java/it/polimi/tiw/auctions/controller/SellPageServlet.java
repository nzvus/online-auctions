package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import it.polimi.tiw.auctions.beans.Auction;   // Import Auction bean
import it.polimi.tiw.auctions.beans.Bid;       // Import Bid bean (for DTO)
import it.polimi.tiw.auctions.beans.Item;      // Import Item bean
import it.polimi.tiw.auctions.beans.User;       // Import User bean
import it.polimi.tiw.auctions.dao.AuctionDAO;  // Import AuctionDAO
import it.polimi.tiw.auctions.dao.BidDAO;      // Import BidDAO
import it.polimi.tiw.auctions.dao.ItemDAO;     // Import ItemDAO
import it.polimi.tiw.auctions.dao.UserDAO;     // Import UserDAO
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler
import it.polimi.tiw.auctions.utils.ItemSerializer;   // Custom serializer for Item
import it.polimi.tiw.auctions.utils.TimestampAdapter; // Custom adapter for Timestamp

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
// No @WebServlet if defined in web.xml
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles requests for the "Sell" page data in the RIA version.
 * This servlet responds to GET requests, typically AJAX calls from `sellPage.js`.
 * It fetches data relevant to the current user's selling activities:
 * - Items available for the user to put into new auctions.
 * - The user's currently open auctions (with details like items and highest bid).
 * - The user's closed auctions (with details like items and winner).
 * The response is a JSON object containing these collections of data.
 * A DTO {@link SellerAuctionListItemDTO} is used for auction listings, and {@link ItemSerializer}
 * handles image data for items.
 */
public class SellPageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;      // Database connection.
    private ItemDAO itemDAO;                   // DAO for item operations.
    private AuctionDAO auctionDAO;             // DAO for auction operations.
    private BidDAO bidDAO;                     // DAO for bid operations (to get highest bids).
    private UserDAO userDAO;                   // DAO for user operations (to get winner details).
    private Gson gson;                         // Gson instance for JSON serialization.

    /**
     * Data Transfer Object (DTO) for sending auction list items (open/closed) to the client for the Sell page.
     * Includes the auction, its items (which will be serialized using {@link ItemSerializer}),
     * the highest bid (for open auctions), and the winner (for closed auctions).
     */
    private static class SellerAuctionListItemDTO {
        Auction auction;    // The auction object.
        List<Item> items;   // List of items in the auction (serialized with ItemSerializer).
        Bid highestBid;     // Highest bid for open auctions (null for closed).
        User winner;        // Winning user for closed auctions (null for open or no winner).

        /**
         * Constructor for SellerAuctionListItemDTO.
         * @param auction The auction.
         * @param items List of items in the auction.
         * @param highestBid The highest bid (if applicable).
         * @param winner The winning user (if applicable).
         */
        public SellerAuctionListItemDTO(Auction auction, List<Item> items, Bid highestBid, User winner) {
            this.auction = auction;
            this.items = items;
            this.highestBid = highestBid;
            this.winner = winner;
        }
    }

    /**
     * Default constructor.
     */
    public SellPageServlet() {
        super();
    }

    /**
     * Initializes the servlet.
     * Establishes a database connection, initializes DAOs, and configures Gson with custom serializers.
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

        // Configure Gson with custom adapters for Timestamp and Item serialization.
        this.gson = new GsonBuilder()
                        .registerTypeAdapter(Timestamp.class, new TimestampAdapter())
                        .registerTypeAdapter(Item.class, new ItemSerializer()) // For items in DTOs and availableItems.
                        .create();
    }

    /**
     * Handles HTTP GET requests to fetch data for the Sell page.
     * Responds with a JSON object containing lists of available items, open auctions, and closed auctions for the user.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Authenticate user and prepare JSON response.
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(this.gson.toJson(Map.of("errorMessage", "User not authenticated.")));
            return;
        }
        User currentUser = (User) session.getAttribute("user");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> responseData = new HashMap<>(); // Map to hold all data for JSON response.

        // Step 2: Fetch data for the Sell page.
        try {
            // Fetch raw lists of open and closed auctions for the current user.
            List<Auction> rawOpenAuctions = auctionDAO.findAuctionsByCreatorAndStatus(currentUser.id(), Auction.AuctionStatus.OPEN);
            List<Auction> rawClosedAuctions = auctionDAO.findAuctionsByCreatorAndStatus(currentUser.id(), Auction.AuctionStatus.CLOSED);

            // Fetch items available for the user to sell (these Item objects will be serialized by ItemSerializer).
            List<Item> availableItems = itemDAO.findAvailableItemsBySellerId(currentUser.id());

            // Process open auctions into DTOs.
            List<SellerAuctionListItemDTO> openAuctionsDTO = new ArrayList<>();
            for (Auction auction : rawOpenAuctions) {
                List<Item> itemsInAuction = itemDAO.findItemsByAuctionId(auction.id());
                Bid highestBid = bidDAO.findHighestBidForAuction(auction.id());
                // Winner is null for open auctions.
                openAuctionsDTO.add(new SellerAuctionListItemDTO(auction, itemsInAuction, highestBid, null));
            }

            // Process closed auctions into DTOs.
            List<SellerAuctionListItemDTO> closedAuctionsDTO = new ArrayList<>();
            for (Auction auction : rawClosedAuctions) {
                List<Item> itemsInAuction = itemDAO.findItemsByAuctionId(auction.id());
                User winner = null;
                // Highest bid is not typically shown for closed auctions (winner/price is key).
                if (auction.winnerUserId() != null) {
                    winner = userDAO.findUserById(auction.winnerUserId()); // Fetch winner details.
                }
                closedAuctionsDTO.add(new SellerAuctionListItemDTO(auction, itemsInAuction, null, winner));
            }

            // Step 3: Populate the response data map.
            responseData.put("openAuctions", openAuctionsDTO);
            responseData.put("closedAuctions", closedAuctionsDTO);
            responseData.put("availableItems", availableItems); // List of Item beans directly.

            // Include login timestamp for client-side calculations.
            if (session.getAttribute("loginTimestamp") != null) {
                Object loginTsObj = session.getAttribute("loginTimestamp");
                if (loginTsObj instanceof Timestamp) {
                    responseData.put("loginTimestamp", ((Timestamp) loginTsObj).getTime());
                } else if (loginTsObj instanceof Long) {
                     responseData.put("loginTimestamp", loginTsObj);
                }
            }
            response.setStatus(HttpServletResponse.SC_OK); // 200 OK.

        } catch (SQLException e) {
            // Handle database errors.
            e.printStackTrace(); // Log error.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 Internal Server Error.
            responseData.put("errorMessage", "Database error retrieving sell page data: " + e.getMessage());
        }

        // Step 4: Send JSON response.
        response.getWriter().write(this.gson.toJson(responseData));
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