package it.polimi.tiw.auctions.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
// No @WebServlet if configured in web.xml
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Handles user logout for the RIA (Rich Internet Application) version.
 * This servlet invalidates the current user's session, effectively logging them out.
 * After invalidating the session, it redirects the user to the static login page (`login.html`).
 * It responds to both GET and POST requests for logout.
 */
public class LogoutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L; // Unique ID for serialization.

    /**
     * Default constructor.
     */
    public LogoutServlet() {
        super();
    }

    /**
     * Handles HTTP GET requests for logging out.
     * Invalidates the user's session and redirects to the login page.
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

        // Step 2: If a session exists, invalidate it.
        if (session != null) {
            // Remove user-specific attributes before invalidating, if necessary for any cleanup logic,
            // though invalidate() typically handles unbinding attributes.
            session.removeAttribute("user");
            session.removeAttribute("loginTimestamp");
            session.invalidate(); // Invalidate the session, removing all its attributes.
        }

        // Step 3: Redirect the user to the static login page.
        // For an RIA, the client-side application (app.js/login.js) will handle
        // clearing any client-side session storage upon detecting logout or session expiry.
        String loginPageURL = request.getContextPath() + "/login.html";
        response.sendRedirect(loginPageURL);
    }

    /**
     * Handles HTTP POST requests for logging out.
     * Delegates to the {@code doGet} method to perform the logout.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Call doGet to handle the logout logic, as it's idempotent for logout.
        doGet(request, response);
    }

    // No init() or destroy() method needed as this servlet does not manage
    // long-lived resources like database connections.
}