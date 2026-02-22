package it.polimi.tiw.auctions.filters;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
// If using web.xml for mapping, @WebFilter annotation is not strictly necessary here,
// but if used, it should typically be "/*" to apply to all requests.
// The provided web.xml already maps this filter to "/*".

/**
 * Ensures that character encoding is consistently set for requests and responses.
 * This filter sets the character encoding for incoming requests (if not already set)
 * and for outgoing responses to a specified encoding (typically UTF-8).
 * This helps prevent issues with character display and data handling, especially
 * for international characters.
 */
public class CharacterEncodingFilter implements Filter {
    private String encoding; // Stores the character encoding to be used.

    /**
     * Default constructor.
     */
    public CharacterEncodingFilter() {
        // No specific initialization needed in the constructor.
    }

    /**
     * Initializes the filter with the configured character encoding.
     * The encoding is read from an initialization parameter "requestEncoding" in {@code web.xml}.
     * If the parameter is not found, UTF-8 is used as the default encoding.
     *
     * @param fConfig The filter configuration object.
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init(FilterConfig fConfig) throws ServletException {
        // Get the "requestEncoding" init parameter from web.xml.
        encoding = fConfig.getInitParameter("requestEncoding");
        // If the encoding is not specified in web.xml, default to UTF-8.
        if (encoding == null) {
            encoding = "UTF-8";
        }
    }

    /**
     * Sets the character encoding for the request and response.
     * For requests, it sets the encoding if one has not already been set.
     * For responses, it sets the character encoding. It does NOT set the Content-Type,
     * as that should be handled by the specific servlet or view technology (e.g., Thymeleaf)
     * to ensure correct MIME types for different content (HTML, CSS, JSON, etc.).
     *
     * @param request  The {@link ServletRequest} object.
     * @param response The {@link ServletResponse} object.
     * @param chain    The {@link FilterChain} to pass the request to the next filter or resource.
     * @throws IOException      if an I/O error occurs.
     * @throws ServletException if a servlet-specific error occurs.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Step 1: Set character encoding for the request.
        // Check if the request's character encoding has already been set.
        if (null == request.getCharacterEncoding()) {
            // If not set, apply the configured encoding (e.g., UTF-8).
            request.setCharacterEncoding(encoding);
        }

        // Step 2: Set character encoding for the response.
        // This ensures that the output stream uses the correct encoding.
        // Note: Content-Type (e.g., "text/html; charset=UTF-8") should be set by the
        // servlet or view rendering engine, not globally here, to allow for different
        // content types (CSS, JS, images, etc.).
        response.setCharacterEncoding("UTF-8"); // Typically, UTF-8 is a good default for responses.

        // Step 3: Pass the request and response along the filter chain.
        // This invokes the next filter in the chain, or the target servlet/JSP if this is the last filter.
        chain.doFilter(request, response);
    }

    /**
     * Called by the servlet container when the filter is being taken out of service.
     * Used for releasing resources.
     */
    @Override
    public void destroy() {
        // This filter does not hold any resources that need explicit cleanup.
    }
}