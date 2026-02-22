package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// No stream Collectors needed here if bidderUsernames is built iteratively.

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import it.polimi.tiw.auctions.beans.Auction;   // Import Auction bean
import it.polimi.tiw.auctions.beans.Bid;       // Import Bid bean
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
 * Handles requests for detailed information about a specific auction in the RIA version.
 * This servlet responds to GET requests, typically AJAX calls from `auctionDetail.js`.
 * It fetches comprehensive data for the specified auction, including its items, all bids,
 * the highest bid, and details about the auction creator and winner (if applicable).
 * The response is a JSON object containing all this information, which the client-side
 * JavaScript uses to render the auction detail view.
 * Custom Gson serializers ({@link ItemSerializer}, {@link TimestampAdapter}) are used for appropriate JSON formatting.
 */
public class AuctionDetailServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;        // Unique ID for serialization.
    private Connection connection = null;                   // Database connection.
    private AuctionDAO auctionDAO;                          // DAO for auction operations.
    private ItemDAO itemDAO;                                // DAO for item operations.
    private BidDAO bidDAO;                                  // DAO for bid operations.
    private UserDAO userDAO;                                // DAO for user operations.
    private Gson gson;                                      // Gson instance for JSON serialization.

    /**
     * Default constructor.
     */
    public AuctionDetailServlet() {
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
        auctionDAO = new AuctionDAO(connection);
        itemDAO = new ItemDAO(connection);
        bidDAO = new BidDAO(connection);
        userDAO = new UserDAO(connection);

        // Configure Gson with custom adapters for Timestamp and Item serialization.
        // ItemSerializer handles Base64 encoding of images within Item objects.
        // TimestampAdapter handles conversion of Timestamps to epoch milliseconds.
        this.gson = new GsonBuilder()
                        .registerTypeAdapter(Timestamp.class, new TimestampAdapter())
                        .registerTypeAdapter(Item.class, new ItemSerializer())
                        .create();
    }

    /**
     * Handles HTTP GET requests to fetch detailed data for a specific auction.
     * Expects an "auctionId" parameter in the request.
     * Responds with a JSON object containing auction details, items, bids, etc.
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
        User currentUser = (User) session.getAttribute("user"); // Current logged-in user.

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> responseData = new HashMap<>(); // To hold data for JSON response.

        // Step 2: Retrieve and validate the "auctionId" request parameter.
        String auctionIdStr = request.getParameter("auctionId");
        if (auctionIdStr == null || auctionIdStr.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request.
            responseData.put("errorMessage", "Auction ID is missing.");
            response.getWriter().write(this.gson.toJson(responseData));
            return;
        }

        // Step 3: Initialize variables for auction data.
        Auction auction;
        List<Item> itemsInAuction; // Serialized with ItemSerializer.
        List<Bid> bidsForAuction;
        User winner = null;
        User creator;
        Bid highestBid;
        Map<Integer, String> bidderUsernames = new HashMap<>(); // Map of bidder UserID to Username.

        // Step 4: Fetch auction data from DAOs.
        try {
            int auctionId = Integer.parseInt(auctionIdStr); // Parse auction ID.
            auction = auctionDAO.findAuctionById(auctionId); // Fetch auction details.

            if (auction == null) {
                // If auction not found, set 404 status and error message.
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                responseData.put("errorMessage", "Auction not found.");
            } else {
                // If auction found, fetch related data.
                itemsInAuction = itemDAO.findItemsByAuctionId(auctionId); // Items in the auction.
                bidsForAuction = bidDAO.findBidsByAuctionId(auctionId);   // All bids for the auction.
                highestBid = bidDAO.findHighestBidForAuction(auctionId);  // Current highest bid.
                creator = userDAO.findUserById(auction.creatorUserId());   // Auction creator details.

                // Populate bidderUsernames map for all unique bidders.
                if (!bidsForAuction.isEmpty()) {
                    for (Bid bid : bidsForAuction) {
                        if (!bidderUsernames.containsKey(bid.userId())) {
                            User bidder = userDAO.findUserById(bid.userId());
                            bidderUsernames.put(bid.userId(), bidder != null ? bidder.username() : "Unknown Bidder");
                        }
                    }
                }
                // If highest bid exists and its bidder is not yet in the map, add them.
                if (highestBid != null && !bidderUsernames.containsKey(highestBid.userId())) {
                     User highestBidder = userDAO.findUserById(highestBid.userId());
                     bidderUsernames.put(highestBid.userId(), highestBidder != null ? highestBidder.username() : "Unknown Bidder");
                }


                // If auction is closed and has a winner, fetch winner details.
                if (auction.status() == Auction.AuctionStatus.CLOSED && auction.winnerUserId() != null) {
                    winner = userDAO.findUserById(auction.winnerUserId());
                }

                // Populate responseData map with all fetched information.
                responseData.put("auction", auction);
                responseData.put("itemsInAuction", itemsInAuction); // Will be serialized using ItemSerializer.
                responseData.put("bidsForAuction", bidsForAuction);
                responseData.put("bidderUsernames", bidderUsernames);
                responseData.put("highestBid", highestBid);
                responseData.put("winner", winner); // Can be null.
                responseData.put("creatorUsername", (creator != null ? creator.username() : "Unknown Creator"));
                responseData.put("currentUser", currentUser); // Send current user details if needed by client.

                // Include login timestamp for client-side calculations (e.g., time remaining).
                if (session.getAttribute("loginTimestamp") != null) {
                    Object loginTsObj = session.getAttribute("loginTimestamp");
                    if (loginTsObj instanceof Timestamp) {
                        responseData.put("loginTimestamp", ((Timestamp) loginTsObj).getTime());
                    } else if (loginTsObj instanceof Long) {
                        responseData.put("loginTimestamp", loginTsObj);
                    }
                }
                response.setStatus(HttpServletResponse.SC_OK); // 200 OK.
            }
        } catch (NumberFormatException e) {
            // Handle invalid auction ID format.
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("errorMessage", "Invalid Auction ID format.");
        } catch (SQLException e) {
            // Handle database errors.
            e.printStackTrace(); // Log error.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 Internal Server Error.
            responseData.put("errorMessage", "Database error retrieving auction details: " + e.getMessage());
        }

        // Step 5: Send JSON response.
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