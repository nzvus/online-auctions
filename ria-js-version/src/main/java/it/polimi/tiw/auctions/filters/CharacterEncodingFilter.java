package it.polimi.tiw.auctions.filters;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
// @WebFilter("/*") can be used if not configured in web.xml, or if web.xml mapping is preferred, remove it.
// Assuming web.xml configures this for "/*".

/**
 * Ensures consistent character encoding (UTF-8) for all HTTP requests and responses.
 * This filter sets the request character encoding if it's not already set,
 * and always sets the response character encoding. This helps prevent character
 * corruption issues, especially with international characters or special symbols.
 */
public class CharacterEncodingFilter implements Filter {
    private String encoding; // Stores the target character encoding.

    /**
     * Default constructor.
     */
    public CharacterEncodingFilter() {
        // No specific initialization in constructor.
    }

    /**
     * Initializes the filter.
     * Reads the "requestEncoding" init-parameter from {@code web.xml}.
     * If not specified, defaults to "UTF-8".
     *
     * @param fConfig The filter configuration object.
     * @throws ServletException if an error occurs during initialization.
     */
    @Override
    public void init(FilterConfig fConfig) throws ServletException {
        // Retrieve the configured encoding from web.xml init parameters.
        encoding = fConfig.getInitParameter("requestEncoding");
        // Default to UTF-8 if no encoding is specified.
        if (encoding == null) {
            encoding = "UTF-8";
        }
    }

    /**
     * Applies the character encoding to the request and response.
     *
     * @param request  The {@link ServletRequest} object.
     * @param response The {@link ServletResponse} object.
     * @param chain    The {@link FilterChain} for invoking the next filter or resource.
     * @throws IOException      if an I/O error occurs.
     * @throws ServletException if a servlet-specific error occurs.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Step 1: Set character encoding for the request.
        // Only set if no encoding has been set previously for the request.
        if (null == request.getCharacterEncoding()) {
            request.setCharacterEncoding(encoding);
        }

        // Step 2: Set character encoding for the response.
        // This ensures the client interprets the response body using UTF-8.
        // The actual Content-Type header (e.g., "application/json; charset=UTF-8")
        // should be set by the specific servlet handling the response, as it depends on the content.
        response.setCharacterEncoding("UTF-8");

        // Step 3: Pass the request and response to the next entity in the filter chain.
        chain.doFilter(request, response);
    }

    /**
     * Called by the servlet container when the filter is being taken out of service.
     * Used for releasing any resources held by the filter.
     */
    @Override
    public void destroy() {
        // This filter does not have any resources to release.
    }
}