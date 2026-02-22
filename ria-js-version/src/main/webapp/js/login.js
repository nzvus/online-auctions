/**
 * @fileOverview Client-side logic for the login page (login.html).
 * Handles form submission via AJAX to the LoginServlet and processes the JSON response.
 * Manages display of error messages and redirection upon successful login.
 * It's an IIFE (Immediately Invoked Function Expression) to keep its scope local.
 */
(function() {
    "use strict";

    // Step 1: Get references to DOM elements.
    const loginForm = document.getElementById('loginForm'); // The login form itself.
    const errorMessageDiv = document.getElementById('login-error-message'); // Div for displaying error messages.

    // Step 2: Add event listener for form submission, if the form exists.
    if (loginForm) {
        loginForm.addEventListener('submit', function(event) {
            // Step 2a: Prevent the default form submission (which would cause a page reload).
            event.preventDefault();

            // Step 2b: Create a FormData object from the form to easily send its data.
            const formData = new FormData(loginForm);

            // Step 2c: Perform client-side validation (though HTML5 'required' handles basic cases).
            // This is a fallback or for more complex custom validation if needed.
            if (!loginForm.checkValidity()) {
                // If form is invalid (e.g., required fields empty), browser's default validation UI will show.
                // We can also display a custom message.
                loginForm.reportValidity(); // Trigger browser's validation UI.
                errorMessageDiv.textContent = "Please fill in all required fields.";
                errorMessageDiv.style.display = 'block'; // Make error message visible.
                return; // Stop further processing.
            }

            // Hide any previous error messages before making the call.
            errorMessageDiv.style.display = 'none';
            errorMessageDiv.textContent = '';

            // Step 2d: Make an AJAX POST request to LoginServlet.
            // The `makeCall` function is expected to be defined in `utils.js`.
            // `false` as the last argument to `makeCall` here means do not reset the form automatically
            // by `makeCall` itself, as we might want to keep values on certain errors.
            makeCall("POST", 'LoginServlet', formData, function(req) {
                // This callback function is executed when the XMLHttpRequest's readyState changes.
                // We are interested in the state when the request is DONE.
                if (req.readyState === XMLHttpRequest.DONE) {
                    try {
                        // Step 2e: Parse the JSON response from the servlet.
                        const data = JSON.parse(req.responseText);
                        // console.log("LoginServlet response data:", data); // For debugging.

                        // Step 2f: Handle the response based on status code and content.
                        if (req.status === 200 && data.success) {
                            // Login successful.
                            // console.log("Login successful. User data:", data.user);
                            // console.log("Login timestamp from server (ms):", data.loginTimestamp);

                            // Validate that essential data is present in the response.
                            if (data.user && typeof data.user.id === 'number' && typeof data.loginTimestamp === 'number') {
                                // Store user data and login timestamp in sessionStorage.
                                // sessionStorage persists for the duration of the page session.
                                sessionStorage.setItem('user', JSON.stringify(data.user));
                                sessionStorage.setItem('loginTimestamp', String(data.loginTimestamp)); // Store as string.

                                // console.log("Data stored in sessionStorage. Redirecting to:", data.redirectUrl);
                                // Redirect to the main application page (app.html).
                                window.location.href = data.redirectUrl;
                            } else {
                                // Data from server was incomplete or in unexpected format.
                                console.error("LoginServlet response missing critical user/timestamp data or incorrect type.");
                                errorMessageDiv.textContent = 'Login successful, but received incomplete data from server.';
                                errorMessageDiv.style.display = 'block';
                            }
                        } else {
                            // Login failed or other server error.
                            // Display the error message received from the server, or a default one.
                            errorMessageDiv.textContent = data.error || 'Login failed. Status: ' + req.status;
                            errorMessageDiv.style.display = 'block';
                        }
                    } catch (e) {
                        // Handle errors in parsing JSON or other client-side issues.
                        console.error("Error parsing JSON response or other client-side error:", e, "Response Text:", req.responseText);
                        errorMessageDiv.textContent = 'An unexpected error occurred during login. Please check console.';
                        errorMessageDiv.style.display = 'block';
                    }
                }
            }, false); // `false` indicates not to automatically reset the form by makeCall.
        });
    }

    // Step 3: Check for and display any error message passed as a URL parameter.
    // This handles cases where a redirect to login.html includes an error (e.g., from AuthenticationFilter).
    const urlParams = new URLSearchParams(window.location.search);
    const errorFromUrl = urlParams.get('error');
    if (errorFromUrl && errorMessageDiv) {
        errorMessageDiv.textContent = decodeURIComponent(errorFromUrl); // Decode URL-encoded message.
        errorMessageDiv.style.display = 'block';
        // Clean the URL by removing the error parameter to prevent it from showing again on refresh.
        window.history.replaceState({}, document.title, window.location.pathname);
    }
})(); // End of IIFE.