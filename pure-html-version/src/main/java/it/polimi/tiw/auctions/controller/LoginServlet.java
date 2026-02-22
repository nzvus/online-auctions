package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.auctions.beans.User; // Import the User bean
import it.polimi.tiw.auctions.dao.UserDAO; // Import the UserDAO
import it.polimi.tiw.auctions.utils.ConnectionHandler; // Import ConnectionHandler utility

/**
 * Handles user login functionality for the online auction application.
 * This servlet processes both GET requests (to display the login page)
 * and POST requests (to authenticate user credentials).
 * Upon successful login, it establishes a user session and redirects to the home page.
 * If login fails, it redisplays the login page with an error message.
 */
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private Connection connection = null; // Database connection shared by servlet methods.
    private TemplateEngine templateEngine; // Thymeleaf template engine for rendering HTML.

    /**
     * Default constructor.
     */
    public LoginServlet() {
        super();
    }

    /**
     * Initializes the servlet. This method is called by the servlet container when the servlet is first loaded.
     * It establishes a database connection and configures the Thymeleaf template engine.
     *
     * @throws ServletException if an error occurs during initialization (e.g., database connection failure).
     */
    @Override
    public void init() throws ServletException {
        // Get the ServletContext for accessing web application resources and parameters.
        ServletContext servletContext = getServletContext();
        // Obtain a database connection using the ConnectionHandler utility.
        // Connection parameters are expected to be in web.xml.
        connection = ConnectionHandler.getConnection(servletContext);

        // Initialize and configure the Thymeleaf template engine.
        // Create a JakartaServletWebApplication instance for Thymeleaf.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        // Create a template resolver to find HTML templates in /WEB-INF/templates/.
        WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
        templateResolver.setTemplateMode(TemplateMode.HTML); // Set template mode to HTML.
        templateResolver.setPrefix("/WEB-INF/templates/");   // Specify the prefix for template paths.
        templateResolver.setSuffix(".html");                 // Specify the suffix for template files.
        // Create and configure the template engine instance.
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(templateResolver);
    }

    /**
     * Handles HTTP GET requests.
     * If the user is already logged in, it redirects them to the home page.
     * Otherwise, it displays the login page (login.html).
     * An "error" URL parameter can be used to display a message on the login page (e.g., from a failed POST attempt).
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Check if the user is already logged in.
        HttpSession session = request.getSession(false); // Get existing session, don't create a new one.
        // If a session exists and contains a "user" attribute, the user is logged in.
        if (session != null && session.getAttribute("user") != null) {
            // Redirect logged-in users to the HomeServlet.
            response.sendRedirect(request.getContextPath() + "/HomeServlet");
            return; // Important to return after sendRedirect.
        }

        // Step 2: Prepare to display the login page if not logged in.
        // Retrieve any error message passed as a URL parameter.
        String errorMessage = request.getParameter("error");

        // Create a WebContext for Thymeleaf, passing request, response, and locale.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
        WebContext ctx = new WebContext(application.buildExchange(request, response), request.getLocale());

        // If an error message exists, add it to the context for display in the template.
        if (errorMessage != null && !errorMessage.isEmpty()) {
            ctx.setVariable("errorMessage", errorMessage);
        }

        // Set the content type and character encoding for the response.
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        // Process the "login.html" template using Thymeleaf and send it to the client.
        templateEngine.process("login", ctx, response.getWriter());
    }

    /**
     * Handles HTTP POST requests, typically for submitting login credentials.
     * It attempts to authenticate the user based on the provided username and password.
     * If authentication is successful, it creates a session, stores user information,
     * and redirects to the home page.
     * If authentication fails, it redirects back to the login page (GET) with an error message.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Retrieve username and password from the request parameters.
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // Step 2: Validate that both username and password are provided.
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            // If parameters are missing, redirect back to the login page (GET) with an error message.
            // URL encode the error message to handle special characters.
            String errorMsg = java.net.URLEncoder.encode("Username and password are required.", "UTF-8");
            response.sendRedirect(request.getContextPath() + "/LoginServlet?error=" + errorMsg);
            return;
        }

        // Step 3: Attempt to authenticate the user.
        UserDAO userDAO = new UserDAO(connection); // Create a UserDAO instance.
        User user = null; // Variable to store the authenticated user.
        try {
            // Call the DAO method to check credentials.
            user = userDAO.findUserByUsernameAndPassword(username, password);
        } catch (SQLException e) {
            // Handle potential SQL exceptions during database interaction.
            System.err.println("SQL Error during login: " + e.getMessage());
            e.printStackTrace(); // Log the stack trace for debugging.
            // Redirect to login page with a generic database error message.
            String errorMsg = java.net.URLEncoder.encode("Database error during login. Please try again later.", "UTF-8");
            response.sendRedirect(request.getContextPath() + "/LoginServlet?error=" + errorMsg);
            return;
        }

        // Step 4: Process the authentication result.
        if (user != null) {
            // If authentication is successful (user object is not null):
            // Create a new session or get the existing one.
            HttpSession session = request.getSession();
            // Store the authenticated user object in the session.
            session.setAttribute("user", user);
            // Store the login timestamp in the session for features like "time remaining".
            session.setAttribute("loginTimestamp", Timestamp.from(java.time.Instant.now()));
            // Redirect the user to the HomeServlet.
            response.sendRedirect(request.getContextPath() + "/HomeServlet");
        } else {
            // If authentication fails (user object is null):
            // Redirect back to the login page (GET) with an "invalid credentials" error message.
            String errorMsg = java.net.URLEncoder.encode("Invalid username or password.", "UTF-8");
            response.sendRedirect(request.getContextPath() + "/LoginServlet?error=" + errorMsg);
        }
    }

    /**
     * Cleans up resources when the servlet is destroyed.
     * This method is called by the servlet container when the servlet is being taken out of service.
     * It closes the database connection.
     */
    @Override
    public void destroy() {
        // Close the database connection using the ConnectionHandler utility.
        ConnectionHandler.closeConnection(connection);
    }
}