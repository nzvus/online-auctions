package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson; // For JSON serialization.

import jakarta.servlet.RequestDispatcher; // For forwarding to login.html.
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig; // Necessary for FormData from JS.
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.auctions.beans.User;    // Import User bean.
import it.polimi.tiw.auctions.dao.UserDAO; // Import UserDAO.
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler.

/**
 * Handles user login for the RIA (Rich Internet Application) version.
 * GET requests display the static `login.html` page.
 * POST requests, typically AJAX calls from `login.js` using FormData,
 * authenticate user credentials.
 * Responds with JSON indicating success or failure, and user data/redirect URL upon success.
 * `@MultipartConfig` is used to ensure `request.getParameter()` works correctly when
 * the client sends data using `FormData`.
 */
@MultipartConfig // Enables parsing of parameters when client uses FormData for POST.
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null;             // Database connection.

    /**
     * Default constructor.
     */
    public LoginServlet() {
        super();
    }

    /**
     * Initializes the servlet.
     * Establishes a database connection using parameters from `web.xml`.
     *
     * @throws ServletException if database connection fails.
     */
    @Override
    public void init() throws ServletException {
        // Get ServletContext to access initialization parameters.
        ServletContext servletContext = getServletContext();
        // Obtain a database connection via ConnectionHandler.
        connection = ConnectionHandler.getConnection(servletContext);
    }

    /**
     * Handles HTTP GET requests.
     * If the user is already logged in (session exists with a "user" attribute),
     * it redirects them to the main application page (`app.html`).
     * Otherwise, it forwards the request to the static `login.html` page to display the login form.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Check for an existing session and if the user is already logged in.
        HttpSession session = request.getSession(false); // false: don't create if no session exists.
        if (session != null && session.getAttribute("user") != null) {
            // If logged in, redirect to the main application page (app.html).
            response.sendRedirect(request.getContextPath() + "/app.html");
            return; // Important to return after redirect.
        }

        // Step 2: If not logged in, display the login page.
        // Forward to the static login.html page.
        String loginPage = "/login.html";
        RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(loginPage);
        dispatcher.forward(request, response);
    }

    /**
     * Handles HTTP POST requests for user authentication.
     * Expects "username" and "password" parameters, typically sent via an AJAX call
     * from the client-side login form (e.g., using FormData).
     * Responds with a JSON object indicating success or failure.
     * On success, includes user data (excluding password for security in client-side storage)
     * and a redirect URL to the main application page (`app.html`).
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Retrieve username and password from request parameters.
        // Thanks to @MultipartConfig, request.getParameter() works even if client uses FormData.
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // Debugging: Log received parameters.
        // System.out.println("LoginServlet POST - Username: " + username + ", Password: " + (password != null ? "Present" : "Missing"));

        // Step 2: Prepare JSON response.
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Gson gson = new Gson(); // Gson instance for JSON conversion.
        Map<String, Object> responseData = new HashMap<>(); // Map to hold response data.

        // Step 3: Validate that credentials are provided.
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request.
            responseData.put("success", false);
            responseData.put("error", "Username and password are required.");
            response.getWriter().write(gson.toJson(responseData));
            return;
        }

        // Step 4: Attempt to authenticate user against the database.
        UserDAO userDAO = new UserDAO(connection);
        User user = null; // Will hold the authenticated User object.
        try {
            user = userDAO.findUserByUsernameAndPassword(username, password);
        } catch (SQLException e) {
            // Handle database errors.
            System.err.println("SQL Error during login: " + e.getMessage());
            e.printStackTrace(); // Log for server-side debugging.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 Internal Server Error.
            responseData.put("success", false);
            responseData.put("error", "Database error during login. Please try again later.");
            response.getWriter().write(gson.toJson(responseData));
            return;
        }

        // Step 5: Process authentication result.
        if (user != null) {
            // Authentication successful.
            // Step 5a: Create or get existing session and store user information.
            HttpSession session = request.getSession(); // true by default (create if not exists).
            session.setAttribute("user", user); // Store the full User object in server-side session.
            Timestamp loginTime = new Timestamp(System.currentTimeMillis());
            session.setAttribute("loginTimestamp", loginTime); // Store login timestamp.

            // Step 5b: Prepare user data for the client (excluding sensitive info like raw password for client storage).
            Map<String, Object> userClientData = new HashMap<>();
            userClientData.put("id", user.id());
            userClientData.put("username", user.username());
            userClientData.put("name", user.name());
            userClientData.put("surname", user.surname());
            userClientData.put("shippingAddress", user.shippingAddress());
            // Note: The actual password is not sent back to the client for client-side storage.

            // Step 5c: Populate JSON response for successful login.
            responseData.put("success", true);
            responseData.put("user", userClientData); // User data for client-side (e.g., sessionStorage).
            responseData.put("loginTimestamp", loginTime.getTime()); // Login timestamp in milliseconds.
            responseData.put("redirectUrl", request.getContextPath() + "/app.html"); // URL for client-side redirect.
            response.setStatus(HttpServletResponse.SC_OK); // 200 OK.
        } else {
            // Authentication failed.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized.
            responseData.put("success", false);
            responseData.put("error", "Invalid username or password.");
        }
        // Step 6: Send JSON response to the client.
        response.getWriter().write(gson.toJson(responseData));
    }

    /**
     * Cleans up resources when the servlet is destroyed.
     * Closes the database connection.
     */
    @Override
    public void destroy() {
        // Close the database connection using the utility handler.
        ConnectionHandler.closeConnection(connection);
    }
}