package it.polimi.tiw.auctions.filters;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Implements authentication control for the web application.
 * This filter intercepts requests to protected resources and checks if a user is logged in.
 * If the user is not authenticated, they are redirected to the login page.
 * Public resources like the login page itself or static assets (CSS, JS) are allowed through without authentication.
 * The specific paths protected by this filter are configured in the {@code web.xml} deployment descriptor.
 */
public class AuthenticationFilter implements Filter {

    /**
     * Default constructor.
     */
    public AuthenticationFilter() {
        // No specific initialization needed in the constructor.
    }

    /**
     * Initializes the filter. This method is called by the servlet container when the filter is first created.
     *
     * @param fConfig The filter configuration object, which can be used to get initialization parameters.
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init(FilterConfig fConfig) throws ServletException {
        // This filter does not require any specific initialization parameters.
    }

    /**
     * Performs the filtering logic for authentication.
     * This method is called by the container for each request that matches the filter's URL mapping.
     * It checks if the user is logged in by looking for a "user" attribute in the HTTP session.
     * If the user is not logged in and is trying to access a protected resource, they are redirected to the login page.
     *
     * @param request  The {@link ServletRequest} object containing the client's request.
     * @param response The {@link ServletResponse} object for sending the response to the client.
     * @param chain    The {@link FilterChain} for invoking the next filter or the target resource.
     * @throws IOException      if an I/O error occurs during processing.
     * @throws ServletException if a servlet-specific error occurs.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Cast ServletRequest and ServletResponse to their HTTP-specific counterparts.
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Define paths that should be accessible without authentication.
        String loginServletPath = req.getContextPath() + "/LoginServlet"; // Path to the LoginServlet.
        String cssPath = req.getContextPath() + "/CSS";                   // Path to the CSS directory.
        // Add other public paths if necessary (e.g., JavaScript files, images).

        // Get the current session, but do not create a new one if it doesn't exist.
        HttpSession session = req.getSession(false);
        // Determine if the user is logged in by checking for a "user" attribute in the session.
        boolean isLoggedIn = (session != null && session.getAttribute("user") != null);
        // Get the requested URI.
        String requestPath = req.getRequestURI();

        // Step 1: Allow access to LoginServlet and static resources (CSS) without authentication.
        // If the requested path is the login servlet or starts with the CSS path,
        // allow the request to proceed to the next filter or target resource.
        if (requestPath.equals(loginServletPath) || requestPath.startsWith(cssPath + "/")) { // Ensure trailing slash for folder matching
            chain.doFilter(request, response); // Continue processing the request.
            return; // Exit the filter.
        }

        // Step 2: Check if the user is logged in.
        if (isLoggedIn) {
            // If logged in, allow access to the requested resource.
            chain.doFilter(request, response); // Continue processing the request.
        } else {
            // If not logged in and trying to access a protected resource,
            // redirect to the LoginServlet.
            // An error message can be passed as a query parameter to inform the user.
            String targetRedirect = loginServletPath + "?error=" + java.net.URLEncoder.encode("Please login to continue", "UTF-8");
            res.sendRedirect(targetRedirect); // Perform the redirect.
        }
    }

    /**
     * Called by the servlet container to indicate to a filter that it is being taken out of service.
     * This method is only called once all threads operations of the filter's doFilter method have completed.
     */
    @Override
    public void destroy() {
        // This filter does not require any cleanup.
    }
}