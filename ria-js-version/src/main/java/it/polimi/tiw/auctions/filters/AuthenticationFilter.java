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
 * Implements authentication control for the RIA (Rich Internet Application) version.
 * This filter intercepts requests to protected resources (typically API endpoints and the main app shell)
 * and checks if a user is authenticated via an active HTTP session.
 * Unauthenticated users attempting to access protected resources are redirected to the login page.
 * Public resources like the login page (`login.html`), the login servlet (`/LoginServlet`),
 * and static assets (CSS, JavaScript files) are allowed through without authentication.
 * Configuration of protected paths is done in `web.xml`.
 */
public class AuthenticationFilter implements Filter {

    /**
     * Default constructor.
     */
    public AuthenticationFilter() {
        // No specific initialization needed in the constructor.
    }

    /**
     * Initializes the filter. Called by the servlet container upon filter creation.
     *
     * @param fConfig The filter configuration object.
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init(FilterConfig fConfig) throws ServletException {
        // This filter does not require specific initialization parameters from fConfig.
    }

    /**
     * Performs the core filtering logic for authentication.
     * It checks the user's session for authentication status before allowing access
     * to protected resources or redirecting to the login page.
     *
     * @param request  The {@link ServletRequest} from the client.
     * @param response The {@link ServletResponse} to be sent to the client.
     * @param chain    The {@link FilterChain} to invoke the next entity in the chain.
     * @throws IOException      if an I/O error occurs during processing.
     * @throws ServletException if a servlet-specific error occurs.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Cast to HTTP-specific request and response objects.
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Define paths that are publicly accessible (do not require authentication).
        String loginServletPath = req.getContextPath() + "/LoginServlet"; // Servlet handling login POST.
        String loginHtmlPath = req.getContextPath() + "/login.html";   // The static login page.
        String appHtmlPath = req.getContextPath() + "/app.html";       // Main application shell (might be protected or public depending on flow).
        String cssPath = req.getContextPath() + "/CSS";                // Directory for CSS files.
        String jsPath = req.getContextPath() + "/js";                  // Directory for JavaScript files.

        // Get the current session (false means do not create one if it doesn't exist).
        HttpSession session = req.getSession(false);
        // Check if the user is logged in (i.e., a "user" attribute exists in the session).
        boolean isLoggedIn = (session != null && session.getAttribute("user") != null);
        // Get the URI of the current request.
        String requestURI = req.getRequestURI();

        // Step 1: Allow access to explicitly public resources.
        // These include the login page, login processing servlet, and static assets.
        if (requestURI.equals(loginServletPath) ||
            requestURI.equals(loginHtmlPath) ||
            requestURI.startsWith(cssPath + "/") || // Allow access to any file within /CSS/
            requestURI.startsWith(jsPath + "/")) {  // Allow access to any file within /js/
            chain.doFilter(request, response); // Proceed to the next filter or resource.
            return; // Exit the filter.
        }

        // Step 2: Handle access to protected resources based on login status.
        // The 'web.xml' defines which specific servlets or app.html are protected by this filter.
        if (isLoggedIn) {
            // If the user is logged in, allow them to proceed to the requested resource.
            chain.doFilter(request, response);
        } else {
            // If the user is not logged in and is trying to access a protected resource:
            // Prepare the target URL for redirection, defaulting to the LoginServlet.
            String targetPage = loginServletPath;
            // For RIA, often redirecting to the static login.html is preferred for the initial view.
            targetPage = loginHtmlPath;

            // If the request was for an API or a specific page (not login related),
            // include an error message to inform the user why they are being redirected.
            // This condition prevents adding the error message if they are already on/being sent to login.html.
            if (!requestURI.equals(loginServletPath) && !requestURI.equals(loginHtmlPath)) {
                 targetPage += "?error=" + java.net.URLEncoder.encode("Please login to continue.", "UTF-8");
            }
            res.sendRedirect(targetPage); // Redirect to the login page.
        }
    }

    /**
     * Called by the servlet container when the filter is being taken out of service.
     * Can be used for cleanup tasks.
     */
    @Override
    public void destroy() {
        // This filter does not hold any resources that require explicit cleanup.
    }
}