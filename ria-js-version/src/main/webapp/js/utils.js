
/**
 * @fileOverview Utility functions for the Online Auctions RIA application.
 * This includes an AJAX call helper, date/time formatting utilities, and HTML escaping.
 */

window.App = window.App || {};
window.App.Utils = window.App.Utils || {};

/**
 * Makes an asynchronous HTTP request (AJAX call).
 *
 * @param {string} method - The HTTP method to use (e.g., "GET", "POST").
 * @param {string} url - The URL to send the request to (typically a servlet endpoint).
 * @param {FormData|HTMLFormElement|null} formElementOrData - The data to send with the request.
 * @param {function} callback - The function to call when the request's readyState changes.
 *        It receives the XMLHttpRequest object as its argument. The callback should typically
 *        check `req.readyState === XMLHttpRequest.DONE` and `req.status` to handle the response.
 * @param {boolean} [resetFormIfElement=true] - Optional. If true (default) and `formElementOrData`
 *        is an HTMLFormElement and the method is "POST", the form will be reset after sending.
 *        Set to false to prevent automatic form reset.
 */
function makeCall(method, url, formElementOrData, callback, resetFormIfElement = true) {
    const req = new XMLHttpRequest();

    req.onreadystatechange = function() {
        callback(req);
    };

    // Initialize the request.
    req.open(method, url, true);

    // Prepare the data to be sent with the request.
    let dataToSend = null; // Initialize to null; used for GET or if no data is provided.

    if (formElementOrData) {
        // Check if FormData is directly provided.
        if (formElementOrData instanceof FormData) {
            dataToSend = formElementOrData;
        }
        // Check if an HTMLFormElement is provided.
        else if (formElementOrData.tagName === 'FORM') {
            dataToSend = new FormData(formElementOrData);
            // Optionally reset the form fields after data is captured, common for POST requests.
            if (resetFormIfElement && method.toUpperCase() === "POST") {
                formElementOrData.reset();
            }
        } else {
            console.error("makeCall: formElementOrData is not a FormData object or an HTMLFormElement.", formElementOrData);
        }
    }

    req.send(dataToSend);
}

// --- Date/Time Formatting Utilities ---

/**
 * Formats a date input (Date object, timestamp string, or epoch milliseconds number)
 * into a "dd-MM-yyyy HH:mm" string format.
 *
 * @param {Date|string|number} dateInput - The date information to format.
 * @returns {string} The formatted date-time string (e.g., "29-05-2024 15:30").
 *         Returns "Invalid Date" if the input cannot be parsed into a valid date.
 */
App.Utils.formatDateTime = function(dateInput) {
    const d = (dateInput instanceof Date) ? dateInput : new Date(dateInput);
    if (isNaN(d.getTime())) {
        return "Invalid Date";
    }
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${day}-${month}-${year} ${hours}:${minutes}`;
};

/**
 * Calculates and formats the time remaining between a reference time and a future deadline.
 * Both times are expected as epoch milliseconds.
 *
 * @param {number} referenceTimeMillis - The reference time in milliseconds since epoch.
 * @param {number} deadlineMillis - The deadline time in milliseconds since epoch.
 * @returns {string} A human-readable string representing the time remaining.
 *         Returns "N/A" if inputs are invalid or null.
 */
App.Utils.getTimeRemaining = function(referenceTimeMillis, deadlineMillis) {
    if (referenceTimeMillis == null || deadlineMillis == null ||
        isNaN(Number(referenceTimeMillis)) || isNaN(Number(deadlineMillis))) {
        return "N/A";
    }
    const now = Number(referenceTimeMillis);
    const deadline = Number(deadlineMillis);

    if (deadline < now) {
        return "Expired";
    }
    let diff = deadline - now;
    const msPerDay = 1000 * 60 * 60 * 24;
    const msPerHour = 1000 * 60 * 60;
    const msPerMinute = 1000 * 60;

    const days = Math.floor(diff / msPerDay);
    diff %= msPerDay;
    const hours = Math.floor(diff / msPerHour);
    diff %= msPerHour;
    const minutes = Math.floor(diff / msPerMinute);

    if (days > 0) {
        return `${days} day(s), ${hours} hour(s)`;
    } else if (hours > 0) {
        return `${hours} hour(s)`;
    } else if (minutes > 0) {
        return `${minutes} minute(s)`;
    } else if (diff > 0) {
        return "Less than a minute";
    } else {
        return "Closing very soon";
    }
};

/**
 * Escapes HTML special characters in a string to prevent XSS vulnerabilities
 * when inserting untrusted data into HTML content.
 *
 * @param {string|null|undefined} str - The string to escape. If null or undefined, an empty string is returned.
 * @returns {string} The escaped string.
 */
App.Utils.escapeHTML = function(str) {
    if (str === null || str === undefined) {
        return '';
    }
	return String(str)
	    .replace(/&/g, '&amp;')   
	    .replace(/</g, '&lt;')
	    .replace(/>/g, '&gt;')
	    .replace(/"/g, '&quot;')
	    .replace(/'/g, '&#039;');
};