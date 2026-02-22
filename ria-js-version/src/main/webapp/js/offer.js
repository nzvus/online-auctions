
/**
 * @fileOverview Client-side logic for the Offer Page view within the RIA.
 * Manages displaying auction details relevant for bidding and the bid placement form.
 * Interacts with AuctionDetailServlet (to get auction data) and PlaceBidServlet (to submit bids) via AJAX.
 */

// Establish the application namespace and the OfferPage module.
window.App = window.App || {};
App.OfferPage = (function() {
    "use strict"; // Enforce stricter JavaScript parsing and error handling.

    // --- DOM Element References ---
    // The main container div for the Offer page view in app.html.
    const offerViewContainer = document.getElementById('offer-view');
    // Reference to the main page title element.
    const pageTitleElement = document.getElementById('page-title'); // Usually managed by Orchestrator

    // --- Module State ---
    let currentAuctionIdOffer = null; // Stores the ID of the auction currently being viewed for bidding.

    // --- Private Helper Functions for Rendering UI Components ---

    /**
     * Renders the main information card for the auction on the offer page.
     * @private
     * @param {object} auction - The auction data object.
     * @param {number} loginTimestampMillis - The user's login timestamp.
     * @param {string} creatorUsername - The username of the auction creator.
     * @returns {string} HTML string for the auction info card.
     */
    function _renderAuctionInfoOffer(auction, loginTimestampMillis, creatorUsername) {
        // Basic check for auction data.
        if (!auction) return '<p class="error-message">Auction data for offer not available.</p>';
        // Determine status string.
        const statusText = (typeof auction.status === 'string' ? auction.status : (auction.status && typeof auction.status.statusValue === 'string' ? auction.status.statusValue : 'N/A')).toUpperCase();
        const deadlineMillis = typeof auction.deadline === 'number' ? auction.deadline : null;

        return `
            <section class="auction-info-card">
                <h2>Auction Information</h2>
                <p><strong>Auction ID:</strong> ${auction.id || 'N/A'}</p>
                <p><strong>Status:</strong> ${App.Utils.escapeHTML(statusText)}</p>
                <p><strong>Initial Price:</strong> €${(typeof auction.initialPrice === 'number' ? auction.initialPrice.toFixed(2) : 'N/A')}</p>
                <p><strong>Minimum Bid Increment:</strong> €${(typeof auction.minimumBidIncrement === 'number' ? auction.minimumBidIncrement.toFixed(0) : 'N/A')}</p>
                <p><strong>Deadline:</strong> ${deadlineMillis ? App.Utils.formatDateTime(deadlineMillis) : 'N/A'}</p>
                <p><strong>Time Remaining (from your login):</strong> ${deadlineMillis ? App.Utils.getTimeRemaining(loginTimestampMillis, deadlineMillis) : 'N/A'}</p>
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
    function _renderItemsInAuctionOffer(items) {
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
     * Renders the card displaying current bids on the auction.
     * @private
     * @param {Array<object>} bids - Array of bid objects.
     * @param {object} bidderUsernames - Map of user IDs to usernames for bidders.
     * @returns {string} HTML string for the bids card.
     */
    function _renderBidsOffer(bids, bidderUsernames) {
        let bidsHTML = '<section class="bids-card"><h2>Current Bids</h2>';
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
        bidsHTML += '</section>';
        return bidsHTML;
    }

    /**
     * Renders the bid placement form if the auction is eligible for bidding by the current user.
     * @private
     * @param {object} auction - The auction data object.
     * @param {object|null} highestBid - The current highest bid object, or null.
     * @param {object} currentUser - The currently logged-in user object.
     * @returns {string} HTML string for the bid form card, or a message if bidding is not allowed.
     */
    function _renderBidFormOffer(auction, highestBid, currentUser) {
        // Determine auction status and deadline for validation.
        const auctionStatus = (typeof auction.status === 'string' ? auction.status : (auction.status && typeof auction.status.statusValue === 'string' ? auction.status.statusValue : '')).toUpperCase();
        const deadlineMillis = typeof auction.deadline === 'number' ? auction.deadline : 0;

        // Conditions under which bidding is NOT allowed:
        let reason = "";
        if (!currentUser) reason = "You must be logged in to bid.";
        else if (auctionStatus !== 'OPEN') reason = "This auction is not open for bidding.";
        else if (auction.creatorUserId === currentUser.id) reason = "You cannot bid on your own auction.";
        else if (deadlineMillis < new Date().getTime()) reason = "This auction has expired.";

        if (reason) {
            // If bidding is not allowed, display the reason.
            return `<section class="action-card"><p>${App.Utils.escapeHTML(reason)}</p></section>`;
        }

        // Calculate the minimum next bid required.
        const currentHighestBidAmount = highestBid && typeof highestBid.amount === 'number' ? highestBid.amount : 0;
        const minNextBidValue = highestBid ? (currentHighestBidAmount + auction.minimumBidIncrement) : auction.initialPrice;
        // Ensure minNextBid is at least 0.01 to prevent issues with 0 or negative inputs.
        const minNextBid = Math.max(0.01, parseFloat(minNextBidValue.toFixed(2)));

        // Informational text for the user about bidding.
        const highestBidDisplay = currentHighestBidAmount > 0 ?
                                 `Current highest bid: €${currentHighestBidAmount.toFixed(2)}. ` :
                                 `Starting price: €${(typeof auction.initialPrice === 'number' ? auction.initialPrice.toFixed(2) : 'N/A')}. `;
        const minBidInstruction = `Your bid must be at least €${minNextBid.toFixed(2)}. Minimum increment: €${auction.minimumBidIncrement.toFixed(0)}.`;

        // HTML for the bid form.
        return `
            <section class="action-card">
                <h2>Place Your Bid</h2>
                <form id="placeBidFormOffer">
                    <input type="hidden" name="auctionId" value="${auction.id}" />
                    <div>
                        <label for="bidAmountInputOffer">Your Bid (€):</label>
                        <input type="number" id="bidAmountInputOffer" name="bidAmount"
                               min="${minNextBid.toFixed(2)}" step="0.01" required 
                               placeholder="e.g., ${minNextBid.toFixed(2)}" />
                        <small>${App.Utils.escapeHTML(highestBidDisplay)}${App.Utils.escapeHTML(minBidInstruction)}</small>
                    </div>
                    <div>
                        <input type="submit" value="Place Bid" />
                    </div>
                </form>
            </section>
        `;
    }

    /**
     * Handles submission of the "Place Bid" form.
     * Performs client-side validation and sends data to PlaceBidServlet via AJAX.
     * @private
     * @param {Event} event - The form submission event.
     */
    function _handlePlaceBidSubmitOffer(event) {
        event.preventDefault(); // Prevent default form submission.
        const form = event.target;

        // Perform client-side validation using HTML5 `checkValidity`.
        if (!form.checkValidity()) {
            form.reportValidity(); // Show browser's validation messages.
            App.Orchestrator.showMessage("Please enter a valid bid amount.", true);
            return;
        }

        // Additional client-side check for bid amount against dynamic minimum.
        const bidAmountInput = form.querySelector('#bidAmountInputOffer');
        const bidAmount = parseFloat(bidAmountInput.value);
        const currentMin = parseFloat(bidAmountInput.min); // `min` attribute was set dynamically.

        if (isNaN(bidAmount) || bidAmount < currentMin) {
            // If bid is less than current dynamic minimum, show an error.
            App.Orchestrator.showMessage(`Your bid (€${bidAmount.toFixed(2)}) must be at least €${currentMin.toFixed(2)}.`, true);
            bidAmountInput.reportValidity(); // This might highlight the field if browser supports it.
            return;
        }

        const formData = new FormData(form); // Get form data.
        // Make AJAX POST request to PlaceBidServlet.
        makeCall("POST", "PlaceBidServlet", formData, function(req) {
            if (req.readyState === XMLHttpRequest.DONE) {
                App.Orchestrator.clearMessages(); // Clear previous messages.
                try {
                    const response = JSON.parse(req.responseText);
                    if (req.status === 200 && response.successMessage) {
                        // On successful bid, show success message and refresh the offer page view
                        // to display the updated bid list and potentially new highest bid.
                        App.Orchestrator.showMessage(response.successMessage, false);
                        if (currentAuctionIdOffer) {
                             App.OfferPage.init(currentAuctionIdOffer); // Re-initialize to refresh data.
                        }
                    } else {
                        // On failure, show error message from server.
                        App.Orchestrator.showMessage(response.errorMessage || "Error placing bid. Status: " + req.status, true);
                    }
                } catch(e) {
                    // Handle errors parsing JSON response.
                    console.error("OfferPage: Error processing PlaceBid response:", e, req.responseText);
                    App.Orchestrator.showMessage("Error processing server response for bid placement.", true);
                }
            }
        }, false); // `false` means makeCall should not reset this form automatically.
    }

    /**
     * Renders navigation links for the Offer page (e.g., "Back to Buy Page").
     * @private
     * @returns {string} HTML string for navigation links.
     */
    function _renderNavigationOffer() {
        // Simple back link to the Buy Page.
        return `<p><a href="#" id="backToBuyLinkFromOffer" class="button-link secondary">Back to Buy Page</a></p>`;
    }

    /**
     * Attaches event listeners to navigation links on the Offer page.
     * @private
     */
    function _attachNavigationListenersOffer() {
        const backToBuyLink = document.getElementById('backToBuyLinkFromOffer');
        if (backToBuyLink) {
            backToBuyLink.addEventListener('click', (e) => {
                e.preventDefault(); App.Orchestrator.navigateToView('buy'); // Navigate to Buy page.
            });
        }
    }

    // --- Public API for App.OfferPage ---
    return {
        /**
         * Initializes the Offer Page view for a given auction ID.
         * Fetches auction data (details, items, bids) from the server and renders the view.
         * Records the auction ID as visited in user-specific localStorage.
         *
         * @param {number} auctionId - The ID of the auction to display for bidding.
         */
        init: function(auctionId) {
            currentAuctionIdOffer = auctionId; // Store current auction ID.
            offerViewContainer.innerHTML = '<p>Loading auction for offer...</p>'; // Show loading message.
            if (pageTitleElement) pageTitleElement.textContent = `Place Bid on Auction #${auctionId}`; // Update page title.

            // Manage "recently visited auctions" in localStorage.
            try {
                let visitedAuctions = App.Orchestrator.getUserDataWithExpiry('visitedAuctions') || [];
                if (!Array.isArray(visitedAuctions)) visitedAuctions = [];
                if (!visitedAuctions.includes(auctionId)) {
                    visitedAuctions.push(auctionId);
                    if (visitedAuctions.length > 20) visitedAuctions.shift(); // Keep list size manageable.
                    App.Orchestrator.setUserDataWithExpiry('visitedAuctions', visitedAuctions);
                }
            } catch (e) {
                console.error("Error updating visited auctions in localStorage (offer.js):", e);
            }

            // Make AJAX GET request to AuctionDetailServlet to get auction data.
            // Note: OfferPageServlet in pure HTML version might have different logic. Here we reuse AuctionDetailServlet.
            makeCall("GET", `AuctionDetailServlet?auctionId=${auctionId}`, null, function(req) {
                if (req.readyState === XMLHttpRequest.DONE) {
                    offerViewContainer.innerHTML = ''; // Clear loading message.
                    if (req.status === 200) {
                        // Request successful.
                        try {
                            const data = JSON.parse(req.responseText); // Parse JSON response.
                            // Get login timestamp and current user.
                            const loginTs = data.loginTimestamp || App.Orchestrator.getLoginTimestamp();
                            const currentUser = data.currentUser || App.Orchestrator.getCurrentUser();

                            // If server sent an error within 200 OK (e.g., auction not found).
                            if (data.errorMessage && !data.auction) {
                                App.Orchestrator.showMessage(data.errorMessage, true);
                                offerViewContainer.innerHTML = '<p class="error-message">Auction details could not be loaded for offer.</p>' + _renderNavigationOffer();
                                _attachNavigationListenersOffer();
                                return;
                            }

                            // If auction data and user data are present, render the page.
                            if (data.auction && currentUser) {
                                // Check bidding eligibility again based on potentially more up-to-date server data.
                                const auctionStatus = (typeof data.auction.status === 'string' ? data.auction.status : (data.auction.status && typeof data.auction.status.statusValue === 'string' ? data.auction.status.statusValue : '')).toUpperCase();
                                const deadlineMillis = typeof data.auction.deadline === 'number' ? data.auction.deadline : 0;
                                const canActuallyBid = (auctionStatus === 'OPEN' && data.auction.creatorUserId !== currentUser.id && deadlineMillis >= new Date().getTime());

                                let content = '<div class="auction-details-grid">'; // Use same grid as detail for consistency.
                                content += _renderAuctionInfoOffer(data.auction, loginTs, data.creatorUsername);
                                content += _renderItemsInAuctionOffer(data.itemsInAuction || []);
                                content += _renderBidsOffer(data.bidsForAuction || [], data.bidderUsernames || {});
                                // Render bid form or "bidding not available" message.
                                content += _renderBidFormOffer(data.auction, data.highestBid, currentUser);
                                content += '</div>'; // End of auction-details-grid
                                content += _renderNavigationOffer(); // Add navigation links.
                                offerViewContainer.insertAdjacentHTML('beforeend', content);

                                // Attach event listener to the bid form if it was rendered.
                                const placeBidForm = document.getElementById('placeBidFormOffer');
                                if (placeBidForm) {
                                    placeBidForm.addEventListener('submit', _handlePlaceBidSubmitOffer);
                                }
                                _attachNavigationListenersOffer(); // Attach listeners to back link.

                            } else {
                                // Handle missing essential data.
                                App.Orchestrator.showMessage('Essential auction data or user data missing for offer page.', true);
                                offerViewContainer.innerHTML = '<p class="error-message">Offer page could not be fully loaded.</p>'  + _renderNavigationOffer();
                                _attachNavigationListenersOffer();
                            }
                        } catch (e) {
                            // Handle errors parsing JSON.
                            console.error("OfferPage: Error parsing data from AuctionDetailServlet:", e, "Response:", req.responseText);
                            App.Orchestrator.showMessage('Error processing offer page details.', true);
                            offerViewContainer.innerHTML = '<p class="error-message">Could not load offer page details due to a client-side processing error.</p>' + _renderNavigationOffer();
                            _attachNavigationListenersOffer();
                        }
                    } else if (req.status === 401) { // Unauthorized
                        App.Orchestrator.showMessage('Session expired or unauthorized. Redirecting to login...', true);
                        setTimeout(() => window.location.href = 'login.html', 2000);
                    }
                     else {
                        // Handle other HTTP error statuses.
                        App.Orchestrator.showMessage('Error loading auction data for offer from server. Status: ' + req.status, true);
                        offerViewContainer.innerHTML = '<p class="error-message">Could not load auction data for offer from server.</p>' + _renderNavigationOffer();
                        _attachNavigationListenersOffer();
                    }
                }
            });
        },

        /**
         * Hides the Offer Page view and clears its content.
         * Called by the orchestrator when switching to another view.
         */
        hide: function() {
            offerViewContainer.innerHTML = ''; // Clear all content.
            currentAuctionIdOffer = null; // Reset current auction ID.
        }
    };
})(); // End of App.OfferPage IIFE.