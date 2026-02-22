package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import it.polimi.tiw.auctions.beans.Auction;
import it.polimi.tiw.auctions.beans.Item; // Item bean è necessario per la lista completa
import it.polimi.tiw.auctions.beans.User;
import it.polimi.tiw.auctions.dao.AuctionDAO;
import it.polimi.tiw.auctions.dao.ItemDAO;
import it.polimi.tiw.auctions.utils.ConnectionHandler;
import it.polimi.tiw.auctions.utils.TimestampAdapter;
import it.polimi.tiw.auctions.utils.ItemSerializer; // Assicurati che ItemSerializer sia usato per la lista di Item

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles requests for the "Buy" page data in the RIA version.
 * This servlet responds to GET requests, typically AJAX calls from `buyPage.js`.
 * It fetches open auctions based on a search keyword or a list of previously visited auction IDs.
 * It also retrieves auctions won by the current user.
 * The response is a JSON object containing these lists of auctions.
 * An inner DTO {@link AuctionWithItemsDTO} is used to package auction data along with all its items.
 */
public class BuyPageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private AuctionDAO auctionDAO;
    private ItemDAO itemDAO;
    private Gson gson;

    /**
     * Data Transfer Object (DTO) for sending auction list items to the client.
     * Includes the auction object and a complete list of its associated Item objects.
     * The Item objects themselves will be serialized using ItemSerializer if registered with Gson.
     */
    private static class AuctionWithItemsDTO {
        Auction auction;        // The auction object.
        List<Item> items;       // Complete list of items in the auction.

        /**
         * Constructor for AuctionWithItemsDTO.
         * @param auction The auction.
         * @param items The list of all items in this auction.
         */
        public AuctionWithItemsDTO(Auction auction, List<Item> items) {
            this.auction = auction;
            this.items = items;
        }
    }

    public BuyPageServlet() {
        super();
    }

    @Override
    public void init() throws ServletException {
        ServletContext servletContext = getServletContext();
        connection = ConnectionHandler.getConnection(servletContext);
        auctionDAO = new AuctionDAO(connection);
        itemDAO = new ItemDAO(connection);

        // Gson configured to use TimestampAdapter and ItemSerializer for lists of Items
        this.gson = new GsonBuilder()
                        .registerTypeAdapter(Timestamp.class, new TimestampAdapter())
                        .registerTypeAdapter(Item.class, new ItemSerializer()) // Crucial for List<Item>
                        .create();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(this.gson.toJson(Map.of("errorMessage", "User not authenticated.")));
            return;
        }
        User user = (User) session.getAttribute("user");
        String keyword = request.getParameter("keyword");
        String idsParam = request.getParameter("ids");

        List<Auction> rawSearchResults = Collections.emptyList();
        List<Auction> rawWonAuctions;

        // MODIFIED: Use AuctionWithItemsDTO
        List<AuctionWithItemsDTO> searchResultsDTO = new ArrayList<>();
        List<AuctionWithItemsDTO> wonAuctionsDTO = new ArrayList<>();

        Map<String, Object> responseData = new HashMap<>();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (idsParam != null && !idsParam.isEmpty() && (keyword == null || keyword.isBlank())) {
                List<Integer> auctionIdsToShow = new ArrayList<>();
                try {
                    String[] idStrings = idsParam.split(",");
                    for (String idStr : idStrings) {
                        auctionIdsToShow.add(Integer.parseInt(idStr.trim()));
                    }
                    if (!auctionIdsToShow.isEmpty()) {
                         rawSearchResults = auctionDAO.findOpenAuctionsByIds(auctionIdsToShow);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("BuyPageServlet: Invalid format for auction IDs parameter: " + idsParam);
                    responseData.put("errorMessage", "Invalid format for previously visited auction IDs.");
                }
            } else if (keyword != null && !keyword.isBlank()) {
                rawSearchResults = auctionDAO.findOpenAuctionsByKeyword(keyword);
            }

            // MODIFIED: Populate DTO with the full list of items
            for (Auction auction : rawSearchResults) {
                List<Item> items = itemDAO.findItemsByAuctionId(auction.id()); // Get all items for this auction
                searchResultsDTO.add(new AuctionWithItemsDTO(auction, items));
            }

            rawWonAuctions = auctionDAO.findWonAuctionsByUser(user.id());
            // MODIFIED: Populate DTO with the full list of items
            for (Auction auction : rawWonAuctions) {
                List<Item> items = itemDAO.findItemsByAuctionId(auction.id()); // Get all items for this auction
                wonAuctionsDTO.add(new AuctionWithItemsDTO(auction, items));
            }

            responseData.put("searchResults", searchResultsDTO);
            responseData.put("wonAuctions", wonAuctionsDTO);
            if (keyword != null) {
                 responseData.put("keyword", keyword);
            }
            if (session.getAttribute("loginTimestamp") != null) {
                Object loginTsObj = session.getAttribute("loginTimestamp");
                if (loginTsObj instanceof Timestamp) {
                    responseData.put("loginTimestamp", ((Timestamp) loginTsObj).getTime());
                } else if (loginTsObj instanceof Long) {
                     responseData.put("loginTimestamp", loginTsObj);
                }
            }
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("errorMessage", "Database error retrieving auction data: " + e.getMessage());
        }

        response.getWriter().write(this.gson.toJson(responseData));
    }

    @Override
    public void destroy() {
        ConnectionHandler.closeConnection(connection);
    }
}