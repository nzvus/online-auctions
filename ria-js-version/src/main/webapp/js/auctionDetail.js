
/**
 * @fileOverview Client-side logic for the Auction Detail view within the RIA.
 * Manages fetching and displaying detailed information about a specific auction,
 * including its items, bids, status, and provides functionality for the auction
 * creator to close the auction if applicable.
 * Interacts with AuctionDetailServlet and CloseAuctionServlet via AJAX.
 */

// Establish the application namespace and the AuctionDetail module.
window.App = window.App || {};
App.AuctionDetail = (function() {
    "use strict"; // Enforce stricter JavaScript parsing and error handling.

    // --- DOM Element References ---
    // The main container div for the Auction Detail view in app.html.
    const auctionDetailViewContainer = document.getElementById('auction-detail-view');
    // Reference to the main page title element (managed by Orchestrator but good to have if needed locally).
    const pageTitleElement = document.getElementById('page-title'); // Usually managed by Orchestrator

    // --- Module State ---
    let currentAuctionId = null; // Stores the ID of the auction currently being viewed.
    let originPage = 'buy';      // Stores where the user navigated from ('buy' or 'sell'), for back navigation.

    // --- Private Helper Functions for Rendering UI Components ---

    /**
     * Renders the main information card for the auction.
     * @private
     * @param {object} auction - The auction data object.
     * @param {number} loginTimestampMillis - The user's login timestamp.
     * @param {string} creatorUsername - The username of the auction creator.
     * @returns {string} HTML string for the auction info card.
     */
    function _renderAuctionInfo(auction, loginTimestampMillis, creatorUsername) {
        if (!auction) return '<p class="error-message">Auction data not available.</p>';
        // Determine status string, handling potential variations in how status is provided.
        const statusText = (typeof auction.status === 'string' ? auction.status : (auction.status && typeof auction.status.statusValue === 'string' ? auction.status.statusValue : 'N/A')).toUpperCase();
        const deadlineMillis = typeof auction.deadline === 'number' ? auction.deadline : null;
        const creationTimestampMillis = typeof auction.creationTimestamp === 'number' ? auction.creationTimestamp : null;

        return `
            <section class="auction-info-card">
                <h2>Auction Information</h2>
                <p><strong>Auction ID:</strong> ${auction.id || 'N/A'}</p>
                <p><strong>Status:</strong> ${App.Utils.escapeHTML(statusText)}</p>
                <p><strong>Initial Price:</strong> €${(typeof auction.initialPrice === 'number' ? auction.initialPrice.toFixed(2) : 'N/A')}</p>
                <p><strong>Minimum Bid Increment:</strong> €${(typeof auction.minimumBidIncrement === 'number' ? auction.minimumBidIncrement.toFixed(0) : 'N/A')}</p>
                <p><strong>Deadline:</strong> ${deadlineMillis ? App.Utils.formatDateTime(deadlineMillis) : 'N/A'}</p>
                <p><strong>Time Remaining (from your login):</strong> ${deadlineMillis ? App.Utils.getTimeRemaining(loginTimestampMillis, deadlineMillis) : 'N/A'}</p>
                <p><strong>Created on:</strong> ${creationTimestampMillis ? App.Utils.formatDateTime(creationTimestampMillis) : 'N/A'}</p>
                <p><strong>Created by:</strong> ${App.Utils.escapeHTML(creatorUsername) || 'Unknown'}</p>
            </section>
        `;
    }

    /**
     * Renders the card displaying items included in the auction.
     * @private
     * @param {Array<object>} items - Array of item objects (expected to have base64Image property).
     * @returns {string} HTML string for the items card.
     */
    function _renderItemsInAuction(items) {
        if (!items || items.length === 0) {
            return '<section class="items-card"><h2>Items in this Auction</h2><p>No items found for this auction.</p></section>';
        }
        let itemsHTML = `
            <section class="items-card">
                <h2>Items in this Auction</h2>
                <table class="item-table-details">
                    <thead>
                        <tr>
                            <th>Item Code</th>
                            <th>Name</th>
                            <th>Description</th>
                            <th>Image</th>
                        </tr>
                    </thead>
                    <tbody>
        `;
        items.forEach(item => {
            const base64Img = item.base64Image; // Assumes ItemSerializer provided this.
            itemsHTML += `
                <tr>
                    <td>${App.Utils.escapeHTML(item.itemCode) || 'N/A'}</td>
                    <td>${App.Utils.escapeHTML(item.name) || 'N/A'}</td>
                    <td class="item-description">${App.Utils.escapeHTML(item.description) || 'N/A'}</td>
                    <td>${base64Img ? `<img src="data:image/jpeg;base64,${base64Img}" alt="${App.Utils.escapeHTML(item.name) || 'Item'}" class="item-thumbnail"/>` : 'No Image'}</td>
                </tr>
            `;
        });
        itemsHTML += '</tbody></table></section>';
        return itemsHTML;
    }

    /**
     * Renders the card displaying bids placed on the auction.
     * @private
     * @param {Array<object>} bids - Array of bid objects.
     * @param {object} bidderUsernames - Map of user IDs to usernames for bidders.
     * @param {object|null} highestBid - The highest bid object, or null if no bids.
     * @returns {string} HTML string for the bids card.
     */
    function _renderBids(bids, bidderUsernames, highestBid) {
        let bidsHTML = '<section class="bids-card"><h2>Bids</h2>';
        if (!bids || bids.length === 0) {
            bidsHTML += '<p>No bids have been placed yet for this auction.</p>';
        } else {
            bidsHTML += `
                <table>
                    <thead>
                        <tr>
                            <th>Bidder</th>
                            <th>Amount</th>
                            <th>Timestamp</th>
                        </tr>
                    </thead>
                    <tbody>
            `;
            bids.forEach(bid => {
                const bidderName = bidderUsernames[bid.userId] ? App.Utils.escapeHTML(bidderUsernames[bid.userId]) : 'Unknown Bidder';
                const bidTimestampMillis = typeof bid.timestamp === 'number' ? bid.timestamp : null;
                bidsHTML += `
                    <tr>
                        <td>${bidderName}</td>
                        <td>€${(typeof bid.amount === 'number' ? bid.amount.toFixed(2) : 'N/A')}</td>
                        <td>${bidTimestampMillis ? App.Utils.formatDateTime(bidTimestampMillis) : 'N/A'}</td>
                    </tr>
                `;
            });
            bidsHTML += '</tbody></table>';
        }
        // Display current highest bid information.
        if (highestBid && typeof highestBid.amount === 'number') {
             const bidderName = bidderUsernames[highestBid.userId] ? App.Utils.escapeHTML(bidderUsernames[highestBid.userId]) : 'Unknown Bidder';
             bidsHTML += `<p><strong>Current Highest Bid:</strong> €${highestBid.amount.toFixed(2)} by ${bidderName}</p>`;
        }
        bidsHTML += '</section>';
        return bidsHTML;
    }

    /**
     * Renders the form for closing an auction, if applicable (creator, open, deadline passed).
     * @private
     * @param {object} auction - The auction data object.
     * @param {object} currentUser - The currently logged-in user object.
     * @returns {string} HTML string for the close auction action card, or empty string if not applicable.
     */
    function _renderCloseAuctionForm(auction, currentUser) {
        const auctionStatus = (typeof auction.status === 'string' ? auction.status : (auction.status && typeof auction.status.statusValue === 'string' ? auction.status.statusValue : '')).toUpperCase();
        const deadlineMillis = typeof auction.deadline === 'number' ? auction.deadline : 0;

        // Conditions for showing the close auction form:
        // 1. Auction must be OPEN.
        // 2. Current user must be the creator of the auction.
        if (auctionStatus !== 'OPEN' || !currentUser || auction.creatorUserId !== currentUser.id) {
            return ''; // Do not render the form if conditions are not met.
        }

        // If deadline has not passed, show a message indicating when it can be closed.
        if (deadlineMillis > new Date().getTime()) {
            return `
                <section class="action-card">
                    <h2>Close Auction</h2>
                    <p>The auction deadline has not yet passed. You can close it after <br/><strong>${App.Utils.formatDateTime(deadlineMillis)}</strong>.</p>
                </section>
            `;
        }
        // If deadline has passed, show the close auction form.
        return `
            <section class="action-card">
                <h2>Close Auction</h2>
                <form id="closeAuctionFormRIA">
                    <input type="hidden" name="auctionId" value="${auction.id}" />
                    <p>The deadline has passed. You can now close this auction.</p>
                    <div>
                        <input type="submit" value="Close Auction" class="danger"/>
                    </div>
                </form>
            </section>
        `;
    }

    /**
     * Renders winner information if the auction is closed and has a winner.
     * @private
     * @param {object} auction - The auction data object.
     * @param {object|null} winner - The winner user object, or null.
     * @returns {string} HTML string for the winner card, or empty string if not applicable.
     */
    function _renderWinnerInfo(auction, winner) {
        const auctionStatus = (typeof auction.status === 'string' ? auction.status : (auction.status && typeof auction.status.statusValue === 'string' ? auction.status.statusValue : '')).toUpperCase();
        // Show winner info only if auction is CLOSED.
        if (auctionStatus !== 'CLOSED') return '';

        let winnerHTML = '<section class="winner-card"><h2>Auction Closed</h2>';
        if (winner && typeof auction.winningPrice === 'number') {
            // If there's a winner and a winning price.
            winnerHTML += `
                <p><strong>Winner Username:</strong> ${App.Utils.escapeHTML(winner.username) || 'N/A'}</p>
                <p><strong>Winner Name:</strong> ${App.Utils.escapeHTML(winner.name) || ''} ${App.Utils.escapeHTML(winner.surname) || ''}</p>
                <p><strong>Winning Price:</strong> €${auction.winningPrice.toFixed(2)}</p>
                <p><strong>Shipping Address:</strong> ${App.Utils.escapeHTML(winner.shippingAddress) || 'N/A'}</p>
            `;
        } else {
            // If auction closed with no winner.
            winnerHTML += `<p>This auction closed without a winner.</p>`;
        }
        winnerHTML += '</section>';
        return winnerHTML;
    }

    /**
     * Handles submission of the "Close Auction" form.
     * Sends an AJAX POST request to CloseAuctionServlet.
     * @private
     * @param {Event} event - The form submission event.
     */
    function _handleCloseAuctionSubmit(event) {
        event.preventDefault(); // Prevent default form submission.
        const form = event.target;
        const formData = new FormData(form); // Get form data.

        // Make AJAX call to CloseAuctionServlet.
        makeCall("POST", "CloseAuctionServlet", formData, function(req) {
            if (req.readyState === XMLHttpRequest.DONE) {
                 App.Orchestrator.clearMessages(); // Clear previous messages.
                 try {
                    const response = JSON.parse(req.responseText);
                    if (req.status === 200 && response.successMessage) {
                        // On success, show success message and refresh the auction detail view.
                        App.Orchestrator.showMessage(response.successMessage, false);
                        // Set 'lastAction' for potential navigation logic in orchestrator (if still needed).
                        App.Orchestrator.setUserDataWithExpiry('lastAction', 'sell_related');
                        if (currentAuctionId) {
                             // Re-initialize this view to show updated auction status.
                             App.AuctionDetail.init(currentAuctionId, originPage);
                        }
                    } else {
                        // On failure, show error message.
                        App.Orchestrator.showMessage(response.errorMessage || "Error closing auction. Status: " + req.status, true);
                    }
                 } catch(e) {
                    console.error("AuctionDetail: Error processing CloseAuction response:", e, req.responseText);
                    App.Orchestrator.showMessage("Error processing server response for closing auction.", true);
                 }
            }
        }, false); // `false` means makeCall should not reset this simple form.
    }

    /**
     * Renders navigation links (e.g., "Back to ...").
     * @private
     * @returns {string} HTML string for navigation links.
     */
    function _renderNavigation() {
        let backLinkHTML = '';
        // Determine "Back" link based on where the user came from.
        if (originPage === 'sell') {
            backLinkHTML = `<p><a href="#" id="backToSellLinkRIA" class="button-link secondary">Back to Sell Page</a></p>`;
        } else { // Default or 'buy'
            backLinkHTML = `<p><a href="#" id="backToBuyLinkRIA" class="button-link secondary">Back to Buy Page</a></p>`;
        }
        // Always include a link back to the Home/Default view.
        return backLinkHTML + `<p><a href="#" id="backToHomeFromDetailRIA" class="button-link secondary">Back to Home (Default View)</a></p>`;
    }

    /**
     * Attaches event listeners to navigation links.
     * @private
     */
    function _attachNavigationListeners() {
        const backToBuyLink = document.getElementById('backToBuyLinkRIA');
        if (backToBuyLink) {
            backToBuyLink.addEventListener('click', (e) => {
                e.preventDefault(); App.Orchestrator.navigateToView('buy');
            });
        }
        const backToSellLink = document.getElementById('backToSellLinkRIA');
        if (backToSellLink) {
            backToSellLink.addEventListener('click', (e) => {
                e.preventDefault(); App.Orchestrator.navigateToView('sell');
            });
        }
        const backToHomeLink = document.getElementById('backToHomeFromDetailRIA');
        if (backToHomeLink) {
             backToHomeLink.addEventListener('click', (e) => {
                e.preventDefault(); App.Orchestrator.navigateToView('buy'); // Default home for RIA is often 'buy'.
            });
        }
    }

    // --- Public API for App.AuctionDetail ---
    return {
        /**
         * Initializes the Auction Detail view for a given auction ID.
         * Fetches auction data from the server and renders the view.
         * Records the auction ID as visited in user-specific localStorage.
         *
         * @param {number} auctionId - The ID of the auction to display.
         * @param {string} [pageOriginFromNav='buy'] - The page from which the user navigated ('buy' or 'sell').
         */
        init: function(auctionId, pageOriginFromNav = 'buy') {
            currentAuctionId = auctionId; // Store the current auction ID.
            originPage = pageOriginFromNav; // Store the origin page.
            auctionDetailViewContainer.innerHTML = '<p>Loading auction details...</p>'; // Show loading message.
            if (pageTitleElement) pageTitleElement.textContent = `Auction Details #${auctionId}`; // Update page title.

            // Manage "recently visited auctions" in localStorage (user-specific).
            try {
                let visitedAuctions = App.Orchestrator.getUserDataWithExpiry('visitedAuctions') || [];
                if (!Array.isArray(visitedAuctions)) visitedAuctions = []; // Ensure it's an array.

                if (!visitedAuctions.includes(auctionId)) {
                    visitedAuctions.push(auctionId);
                    // Keep the list to a manageable size, e.g., last 20 visited.
                    if (visitedAuctions.length > 20) {
                        visitedAuctions.shift(); // Remove oldest.
                    }
                    App.Orchestrator.setUserDataWithExpiry('visitedAuctions', visitedAuctions);
                }
            } catch (e) {
                console.error("Error updating visited auctions in localStorage (auctionDetail.js):", e);
            }

            // Make AJAX GET request to AuctionDetailServlet.
            makeCall("GET", `AuctionDetailServlet?auctionId=${auctionId}`, null, function(req) {
                if (req.readyState === XMLHttpRequest.DONE) {
                    auctionDetailViewContainer.innerHTML = ''; // Clear loading message.
                    if (req.status === 200) {
                        // Request successful.
                        try {
                            const data = JSON.parse(req.responseText); // Parse JSON response.
                            // Get login timestamp and current user from response or orchestrator.
                            const loginTs = data.loginTimestamp || App.Orchestrator.getLoginTimestamp();
                            const currentUser = data.currentUser || App.Orchestrator.getCurrentUser();

                            // If server sent an error message within a 200 OK (e.g., auction not found but request was fine).
                            if (data.errorMessage && !data.auction) {
                                App.Orchestrator.showMessage(data.errorMessage, true);
                                auctionDetailViewContainer.innerHTML = '<p class="error-message">Auction details could not be loaded.</p>' + _renderNavigation();
                                _attachNavigationListeners();
                                return;
                            }

                            // If auction data and user data are present, render the page.
                            if (data.auction && currentUser) {
                                let contentGrid = '<div class="auction-details-grid">';
                                contentGrid += _renderAuctionInfo(data.auction, loginTs, data.creatorUsername);
                                contentGrid += _renderItemsInAuction(data.itemsInAuction || []);
                                contentGrid += _renderBids(data.bidsForAuction || [], data.bidderUsernames || {}, data.highestBid);
                                contentGrid += _renderCloseAuctionForm(data.auction, currentUser);
                                contentGrid += _renderWinnerInfo(data.auction, data.winner);
                                contentGrid += '</div>'; // End of auction-details-grid
                                contentGrid += _renderNavigation(); // Add navigation links.

                                auctionDetailViewContainer.insertAdjacentHTML('beforeend', contentGrid);

                                // Attach event listener to the "Close Auction" form if it exists.
                                const closeAuctionForm = document.getElementById('closeAuctionFormRIA');
                                if (closeAuctionForm) {
                                    closeAuctionForm.addEventListener('submit', _handleCloseAuctionSubmit);
                                }
                                _attachNavigationListeners(); // Attach listeners to back/home links.
                            } else {
                                // Handle cases where essential data is missing from response.
                                App.Orchestrator.showMessage('Essential auction data or user data is missing from server response.', true);
                                auctionDetailViewContainer.innerHTML = '<p class="error-message">Auction details could not be fully loaded.</p>' + _renderNavigation();
                                 _attachNavigationListeners();
                            }
                        } catch (e) {
                            // Handle errors parsing JSON.
                            console.error("AuctionDetail: Error parsing data from AuctionDetailServlet:", e, "Response:", req.responseText);
                            App.Orchestrator.showMessage('Error processing auction details received from server.', true);
                            auctionDetailViewContainer.innerHTML = '<p class="error-message">Could not load details due to a client-side processing error.</p>' + _renderNavigation();
                            _attachNavigationListeners();
                        }
                    } else if (req.status === 401) { // Unauthorized
                        App.Orchestrator.showMessage('Session expired or unauthorized. Redirecting to login...', true);
                        setTimeout(() => window.location.href = 'login.html', 2000);
                    }
                    else {
                        // Handle other HTTP error statuses.
                        App.Orchestrator.showMessage('Error loading auction details from server. Status: ' + req.status, true);
                        auctionDetailViewContainer.innerHTML = '<p class="error-message">Could not load auction details from server.</p>' + _renderNavigation();
                        _attachNavigationListeners();
                    }
                }
            });
        },

        /**
         * Hides the Auction Detail view and clears its content.
         * Called by the orchestrator when switching to another view.
         */
        hide: function() {
            auctionDetailViewContainer.innerHTML = ''; // Clear all content.
            currentAuctionId = null; // Reset current auction ID.
        }
    };
})(); // End of App.AuctionDetail IIFE.