/**
 * @fileOverview Main orchestrator for the Online Auctions RIA (Single Page Application).
 * Manages page initialization, view navigation, display of messages, user session data,
 * and interaction with localStorage for persistent client-side state.
 * It acts as a central controller for the client-side application.
 */


window.App = window.App || {};

// Orchestrator module's IIFE.
App.Orchestrator = (function() {
    "use strict";

    const pageTitleElement = document.getElementById('page-title');          
    const userInfoHeaderElement = document.getElementById('user-info-header'); // welcome message and nav links.
    const messagesContainerElement = document.getElementById('messages-container'); // success/error messages.

    const viewElements = {
        buy: document.getElementById('buy-view'),
        sell: document.getElementById('sell-view'),
        auctionDetail: document.getElementById('auction-detail-view'),
        offer: document.getElementById('offer-view')
    };

    // --- State Variables ---
    let currentUser = null;             
    let loginTimestampMillis = null;   
    const ONE_MONTH_IN_MS = 30 * 24 * 60 * 60 * 1000; // Constant for localStorage item expiry (1 month).

    // --- Private Utility Functions for User-Specific localStorage with Expiry ---

    /**
     * Generates a user-specific key for localStorage items.
     * This ensures that data stored for one user does not conflict with another's on the same browser.
     * @private
     * @param {string} baseKey - The base key for the localStorage item (e.g., 'lastAction').
     * @returns {string|null} The user-specific key (e.g., 'lastAction_userId_123'), or null if no user is logged in or user ID is missing.
     */
    function _getUserSpecificKey(baseKey) {
        if (!currentUser || typeof currentUser.id !== 'number') {
            // console.warn(`Cannot generate user-specific key for '${baseKey}' without a logged-in user with a valid ID.`);
            return null;
        }
        return `${baseKey}_userId_${currentUser.id}`;
    }

    /**
     * Retrieves an item from localStorage, checking its expiry.
     * Uses a user-specific key.
     * @private
     * @param {string} baseKey - The base key of the item to retrieve.
     * @returns {any|null} The value of the item if found and not expired, otherwise null.
     */
    function _getLocalStorageItemWithExpiry(baseKey) {
        const userSpecificKey = _getUserSpecificKey(baseKey);
        if (!userSpecificKey) return null;

        const itemStr = localStorage.getItem(userSpecificKey);
        if (!itemStr) {
            return null;
        }
        try {
            const item = JSON.parse(itemStr);
            if (item && typeof item.value !== 'undefined' && item.expiresAt && new Date().getTime() < item.expiresAt) {
                return item.value;
            } else {
                localStorage.removeItem(userSpecificKey); // Item expired or malformed
                return null;
            }
        } catch (e) {
            console.error(`Error parsing item '${userSpecificKey}' from localStorage:`, e);
            localStorage.removeItem(userSpecificKey);
            return null;
        }
    }

    /**
     * Sets an item in localStorage with an expiry time and user-specific key.
     * @private
     * @param {string} baseKey - The base key for the item.
     * @param {any} value - The value to store.
     * @param {number} [durationMs=ONE_MONTH_IN_MS] - The duration in milliseconds for which the item is valid.
     */
    function _setLocalStorageItemWithExpiry(baseKey, value, durationMs = ONE_MONTH_IN_MS) {
        const userSpecificKey = _getUserSpecificKey(baseKey);
        if (!userSpecificKey) return;

        const now = new Date().getTime();
        const item = {
            value: value,
            expiresAt: now + durationMs
			
        };
		
		console.log(now+durationMs)
		
        try {
            localStorage.setItem(userSpecificKey, JSON.stringify(item));
        } catch (e) {
            console.error(`Error setting item '${userSpecificKey}' in localStorage:`, e);
        }
    }

    /**
     * Removes a user-specific item from localStorage.
     * @private
     * @param {string} baseKey - The base key of the item to remove.
     */
    function _removeLocalStorageItem(baseKey) {
        const userSpecificKey = _getUserSpecificKey(baseKey);
        if (userSpecificKey) {
            localStorage.removeItem(userSpecificKey);
        }
    }

    // --- Core Orchestrator Logic ---

    /**
     * Updates the header section of `app.html` with user information and navigation links.
     * @private
     */
    function _updateHeader() {
        if (currentUser && currentUser.name && currentUser.surname) {
            userInfoHeaderElement.innerHTML = `
                Welcome, <span>${App.Utils.escapeHTML(currentUser.name)} ${App.Utils.escapeHTML(currentUser.surname)}</span>!
                <nav>
                    <a href="#" id="nav-home-ria">Home (Default View)</a>
                    <a href="#" id="nav-sell-ria">Sell Page</a>
                    <a href="#" id="nav-buy-ria">Buy Page</a>
                    <a href="LogoutServlet" id="logout-link-ria">Logout</a>
                </nav>
            `;
            // Attach event listeners to navigation links
            document.getElementById('nav-home-ria').addEventListener('click', (e) => {
                e.preventDefault();
                _navigateToInitialViewLogic(); // This function determines the actual initial view
            });
            document.getElementById('nav-sell-ria').addEventListener('click', (e) => {
                e.preventDefault();
                navigateToView('sell');
            });
            document.getElementById('nav-buy-ria').addEventListener('click', (e) => {
                e.preventDefault();
                // When directly clicking "Buy Page", buyPage.js's init will handle
                // checking for visitedAuctions from localStorage if no overriding params are given.
                navigateToView('buy');
            });
            document.getElementById('logout-link-ria').addEventListener('click', () => {
				
				//nella traccia viene richiesto (implicitamente) di lasciare i dati al logout
                // Clear user-specific localStorage items on logout attempt.
                // Server-side session invalidation is handled by LogoutServlet.
                //if (currentUser && typeof currentUser.id === 'number') {
                 //   _removeLocalStorageItem('lastAction');
                  //  _removeLocalStorageItem('visitedAuctions');
                  //  _removeLocalStorageItem('lastSearchKeyword'); // Also clear last search keyword
               // }
            });
        } else {
            // This case should ideally not be reached if app.html is protected.
            userInfoHeaderElement.innerHTML = `<a href="login.html">Login</a>`;
        }
    }

    /**
     * Determines and navigates to the appropriate initial view based on stored client-side state.
     * Priority:
     * 1. If 'lastAction' was 'sell_related', navigate to Sell page.
     * 2. Otherwise, navigate to Buy page, passing any 'visitedAuctions' as initial parameters.
     * `currentUser` must be set before calling this function.
     * @private
     */
    function _navigateToInitialViewLogic() {
        if (!currentUser) {
            console.error("Cannot determine initial view: currentUser is not set. Defaulting to Buy page.");
            navigateToView('buy'); // Fallback, though start() should prevent this.
            return;
        }

        const lastAction = _getLocalStorageItemWithExpiry('lastAction');
        const visitedAuctionIds = _getLocalStorageItemWithExpiry('visitedAuctions');

        if (lastAction === 'sell_related') {
            // console.log("Navigating to SELL page due to 'lastAction'.");
            _removeLocalStorageItem('lastAction'); // Clear the one-time action flag.
            navigateToView('sell');
        } else {
            // Default to Buy page. Pass visitedAuctionIds if they exist;
            // buyPage.js will decide whether to use them or a keyword.
            // The keywordFromOrchestrator is null here because this initial logic
            // prioritizes visited auctions over a potentially stale search keyword for the very first view.
            // console.log("Navigating to BUY page, potentially with visitedAuctionIds:", visitedAuctionIds);
            navigateToView('buy', { initialAuctionIds: visitedAuctionIds, keywordFromOrchestrator: null });
        }
    }

    /**
     * Hides all view containers and calls their respective `hide()` methods.
     * @private
     */
    function _hideAllViews() {
        for (const viewKey in viewElements) {
            if (viewElements[viewKey]) {
                viewElements[viewKey].style.display = 'none';
                viewElements[viewKey].innerHTML = ''; // Clear content.
            }
        }
        // Call hide methods of individual page modules if they exist.
        if (App.BuyPage && typeof App.BuyPage.hide === 'function') App.BuyPage.hide();
        if (App.SellPage && typeof App.SellPage.hide === 'function') App.SellPage.hide();
        if (App.AuctionDetail && typeof App.AuctionDetail.hide === 'function') App.AuctionDetail.hide();
        if (App.OfferPage && typeof App.OfferPage.hide === 'function') App.OfferPage.hide();
    }

    /**
     * Navigates to the specified view within the single-page application.
     * @param {string} viewName - The key of the view to navigate to (e.g., 'buy', 'sell').
     * @param {object} [params=null] - Optional parameters to pass to the view's init function.
     */
    function navigateToView(viewName, params = null) {
        _hideAllViews();
        clearMessages();

        const viewContainer = viewElements[viewName];
        if (!viewContainer) {
            console.error(`View container for '${viewName}' not found.`);
            if(pageTitleElement) pageTitleElement.textContent = "Error - View Not Found";
            showMessage(`View '${viewName}' is not available.`, true);
            return;
        }

        if(pageTitleElement) { // Ensure pageTitleElement exists
            switch (viewName) {
                case 'buy': pageTitleElement.textContent = "Find Auctions"; break;
                case 'sell': pageTitleElement.textContent = "Sell Your Items"; break;
                case 'auctionDetail': pageTitleElement.textContent = "Auction Details"; break; // Title might be refined by module
                case 'offer': pageTitleElement.textContent = "Place a Bid"; break; // Title might be refined by module
                default: pageTitleElement.textContent = "Online Auctions";
            }
        }

        viewContainer.style.display = 'block'; // Make the target view container visible.

        // Initialize the corresponding JavaScript module for the view.
        switch (viewName) {
            case 'buy':
                if (App.BuyPage && typeof App.BuyPage.init === 'function') {
                    App.BuyPage.init(params || {});
                } else { console.error("BuyPage module or init not loaded."); }
                break;
            case 'sell':
                if (App.SellPage && typeof App.SellPage.init === 'function') {
                    App.SellPage.init();
                } else { console.error("SellPage module or init not loaded."); }
                break;
            case 'auctionDetail':
                if (App.AuctionDetail && typeof App.AuctionDetail.init === 'function' && params && typeof params.auctionId !== 'undefined') {
                    App.AuctionDetail.init(params.auctionId, params.origin);
                } else {
                    console.error("AuctionDetail module, init, or auctionId missing.");
                    showMessage("Could not load auction details.", true);
                }
                break;
            case 'offer':
                if (App.OfferPage && typeof App.OfferPage.init === 'function' && params && typeof params.auctionId !== 'undefined') {
                    App.OfferPage.init(params.auctionId);
                } else {
                    console.error("OfferPage module, init, or auctionId missing.");
                    showMessage("Could not load the offer page.", true);
                }
                break;
            default:
                console.error(`Unknown view: ${viewName}`);
                if(pageTitleElement) pageTitleElement.textContent = "Unknown Page";
                showMessage(`The page '${viewName}' does not exist.`, true);
        }
    }

    /**
     * Displays a global message (error or success).
     * @param {string} message - The message text.
     * @param {boolean} [isError=false] - True if it's an error message.
     */
    function showMessage(message, isError = false) {
        clearMessages();
        const messageDiv = document.createElement('div');
        messageDiv.textContent = message;
        messageDiv.className = isError ? 'message-base error-message' : 'message-base success-message';
        messagesContainerElement.appendChild(messageDiv);
        messagesContainerElement.style.display = 'block';
    }

    /**
     * Clears any global messages.
     */
    function clearMessages() {
        messagesContainerElement.innerHTML = '';
        messagesContainerElement.style.display = 'none';
    }

    // --- Public API of the Orchestrator ---
    return {
        /**
         * Starts the application. Called once the DOM is ready.
         * Initializes user session from sessionStorage and navigates to the initial view.
         */
        start: function() {
            const userJson = sessionStorage.getItem('user');
            const loginTimestampJson = sessionStorage.getItem('loginTimestamp');

            if (userJson && loginTimestampJson) {
                try {
                    currentUser = JSON.parse(userJson); // Set for use by _getUserSpecificKey
                    loginTimestampMillis = parseInt(loginTimestampJson, 10);

                    if (!currentUser || typeof currentUser.id !== 'number' || isNaN(loginTimestampMillis)) {
                        throw new Error("Invalid user data or login timestamp in session storage.");
                    }
                    _updateHeader(); // Now currentUser is set.
                    _navigateToInitialViewLogic();
                } catch (e) {
                    console.error("Error initializing orchestrator from session data:", e);
                    sessionStorage.clear();
                    window.location.href = 'login.html'; // Redirect to login on error.
                }
            } else {
                window.location.href = 'login.html'; // Redirect if no session data.
            }
        },
        navigateToView: navigateToView,
        showMessage: showMessage,
        clearMessages: clearMessages,
        getCurrentUser: function() { return currentUser; },
        getLoginTimestamp: function() { return loginTimestampMillis; },
        setUserDataWithExpiry: _setLocalStorageItemWithExpiry,
        getUserDataWithExpiry: _getLocalStorageItemWithExpiry,
        removeUserData: _removeLocalStorageItem
    };
})();

// --- Application Entry Point ---
window.addEventListener('DOMContentLoaded', () => {
    if (App && App.Orchestrator && typeof App.Orchestrator.start === 'function') {
        App.Orchestrator.start();
    } else {
        console.error("App.Orchestrator or App.Orchestrator.start is not defined at DOMContentLoaded.");
        document.body.innerHTML = "<p style='color:red; font-weight:bold; text-align:center; margin-top: 50px;'>Critical Application Error: Core component (Orchestrator) failed to load. Please try refreshing the page or contact support.</p>";
    }
});
