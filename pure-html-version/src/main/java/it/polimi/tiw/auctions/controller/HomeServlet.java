package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import java.sql.Timestamp; // Ensure Timestamp is imported for session attribute casting

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

// No specific bean imports needed if just forwarding/rendering a static-like home page for logged-in users.
// If home.html needs specific data beyond user, those beans/DAOs would be used here.

/**
 * Handles requests for the main home page of the application after a user has logged in.
 * This servlet is responsible for displaying the primary landing page for authenticated users.
 * It ensures that only authenticated users can access this page.
 * The content of the home page is rendered using the "home.html" Thymeleaf template.
 */
public class HomeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.
    private TemplateEngine templateEngine; // Thymeleaf template engine.

    /**
     * Default constructor.
     */
    public HomeServlet() {
        super();
    }

    /**
     * Initializes the servlet, primarily setting up the Thymeleaf template engine.
     *
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init() throws ServletException {
        // Get the ServletContext for application-wide resources.
        ServletContext servletContext = getServletContext();
        // Initialize and configure Thymeleaf.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
        templateResolver.setTemplateMode(TemplateMode.HTML); // Set HTML mode.
        templateResolver.setPrefix("/WEB-INF/templates/");   // Template location.
        templateResolver.setSuffix(".html");                 // Template file extension.
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(templateResolver);
    }

    /**
     * Handles HTTP GET requests for the home page.
     * It verifies that the user is logged in (this should also be enforced by {@link AuthenticationFilter}).
     * If logged in, it renders the "home.html" template, passing user information to it.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get the current session.
        HttpSession session = request.getSession(false); // Do not create a new session if one doesn't exist.

        // Step 2: Verify authentication (primarily as a safeguard; AuthenticationFilter should handle this).
        // If there's no session or no "user" attribute, redirect to the login page.
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/LoginServlet");
            return; // Exit after redirect.
        }

        // Step 3: Prepare data for the Thymeleaf template.
        // Create a WebContext for Thymeleaf.
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
        WebContext ctx = new WebContext(application.buildExchange(request, response), request.getLocale());
        // Pass the "user" object from the session to the template (e.g., for a welcome message).
        ctx.setVariable("user", session.getAttribute("user"));
        // Pass the "loginTimestamp" if it's used by the home.html template.
        ctx.setVariable("loginTimestamp", (Timestamp) session.getAttribute("loginTimestamp"));

        // Step 4: Render the "home.html" template.
        String path = "home"; // Path to the home.html template (relative to prefix/suffix).
        templateEngine.process(path, ctx, response.getWriter());
    }
}