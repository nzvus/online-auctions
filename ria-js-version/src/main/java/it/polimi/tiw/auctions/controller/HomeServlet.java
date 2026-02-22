package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import jakarta.servlet.RequestDispatcher; // For forwarding to app.html
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles requests for the main application entry point in the RIA version.
 * If the user is authenticated (session exists), this servlet forwards the request
 * to the main application shell page (`app.html`).
 * If the user is not authenticated, the {@link AuthenticationFilter} should have already
 * redirected them to the login page. This servlet acts as a final check and entry
 * point to the SPA.
 */
public class HomeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.

    /**
     * Default constructor.
     */
    public HomeServlet() {
        super();
    }

    /**
     * Initializes the servlet. No specific initialization is typically needed for this
     * simple forwarding servlet in an RIA context, as Thymeleaf or database connections
     * are not directly used here.
     *
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init() throws ServletException {
        // No database connection or template engine needed for this servlet in RIA context.
        // Its main role is to serve/forward to the SPA shell.
    }

    /**
     * Handles HTTP GET requests for the application's home/entry point.
     * Checks if the user is authenticated. If so, forwards to `app.html`.
     * If not, it redirects to the login page (though this should ideally be
     * preempted by the AuthenticationFilter).
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Step 1: Get the current session without creating a new one.
        HttpSession session = request.getSession(false);

        // Step 2: Check if the user is authenticated.
        // The AuthenticationFilter should handle unauthenticated access to this servlet
        // if /HomeServlet is a protected path in web.xml.
        // This check here is an additional safeguard or if /HomeServlet is the welcome-file
        // and might be hit before the filter fully processes a redirect for unauthenticated root access.
        if (session == null || session.getAttribute("user") == null) {
            // If not authenticated, redirect to the static login page.
            response.sendRedirect(request.getContextPath() + "/login.html");
            return; // Exit after redirect.
        }

        // Step 3: If authenticated, forward to the main application shell (app.html).
        // The client-side JavaScript within app.html will then take over rendering the UI.
        String appShellPage = "/app.html";
        RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(appShellPage);
        dispatcher.forward(request, response);
    }

    /**
     * Handles HTTP POST requests. For this servlet, POST requests are typically
     * not expected or are handled the same way as GET requests (i.e., forward to app.html
     * if authenticated).
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Delegate POST requests to the doGet method for consistent behavior.
        doGet(request, response);
    }

    /**
     * Cleans up resources when the servlet is destroyed.
     * No specific resources to clean up for this servlet.
     */
    @Override
    public void destroy() {
        // No resources like database connections to close here.
    }
}