
/**
 * @fileOverview Client-side logic for the "Sell" page view within the RIA.
 * Manages forms for creating items and auctions, lists user's available items,
 * open auctions, and closed auctions. Handles AJAX interactions with respective servlets.
 */

window.App = window.App || {};
App.SellPage = (function() {
    "use strict"; // Enforce stricter JavaScript parsing and error handling.

    // --- DOM Element References ---
    // The main container div for the Sell page view in app.html.
    const sellViewContainer = document.getElementById('sell-view');

    // --- Private Helper Functions for Rendering ---

    /**
     * Renders the form for creating a new item.
     * @private
     */
    function _renderCreateItemForm() {
        const formHTML = `
            <section class="create-form">
                <h2>Create New Item</h2>
                <form id="sellPageCreateItemForm" enctype="multipart/form-data">
                    <div>
                        <label for="sellItemCode">Item Code:</label>
                        <input type="text" id="sellItemCode" name="itemCode" required />
                    </div>
                    <div>
                        <label for="sellItemName">Item Name:</label>
                        <input type="text" id="sellItemName" name="itemName" required />
                    </div>
                    <div>
                        <label for="sellDescription">Description:</label>
                        <textarea id="sellDescription" name="description" required></textarea>
                    </div>
                    <div>
                        <label for="sellImage">Image:</label>
                        <input type="file" id="sellImage" name="image" accept="image/*" required />
                    </div>
                    <div>
                        <label for="sellBasePrice">Base Price (€):</label>
                        <input type="number" id="sellBasePrice" name="basePrice" min="0.01" step="0.01" required />
                    </div>
                    <div>
                        <input type="submit" value="Create Item" />
                    </div>
                </form>
            </section>
        `;
        // Insert the form HTML into the sell view container.
        sellViewContainer.insertAdjacentHTML('beforeend', formHTML);
        // Add event listener for the form submission.
        const formElement = document.getElementById('sellPageCreateItemForm');
        if (formElement) {
            formElement.addEventListener('submit', _handleCreateItemSubmit);
        }
    }

    /**
     * Renders the form for creating a new auction, including a list of available items.
     * @private
     * @param {Array<object>} availableItems - Array of Item objects (with base64Image property).
     */
    function _renderCreateAuctionForm(availableItems) {
        let itemsOptionsHTML = ''; // To build <option> elements for the select list.
        if (availableItems && availableItems.length > 0) {
            availableItems.forEach(item => {
                const basePrice = typeof item.basePrice === 'number' ? item.basePrice.toFixed(2) : 'N/A';
                // Use escapeHTML for text content.
                itemsOptionsHTML += `<option value="${item.id}">${App.Utils.escapeHTML(item.name)} (Code: ${App.Utils.escapeHTML(item.itemCode)}, Base Price: €${basePrice})</option>`;
            });
        }

        // HTML for previewing available items in a table within the form.
        let availableItemsPreviewHTML = '<h4>Available Items for Auction:</h4>';
        if (availableItems && availableItems.length > 0) {
            availableItemsPreviewHTML += `
                <table>
                    <thead><tr><th>Code</th><th>Name</th><th>Base Price</th><th>Image</th></tr></thead>
                    <tbody>`;
            availableItems.forEach(item => {
                const base64Img = item.base64Image || ''; // ItemSerializer should provide this.
                const basePrice = typeof item.basePrice === 'number' ? item.basePrice.toFixed(2) : 'N/A';
                availableItemsPreviewHTML += `
                    <tr>
                        <td>${App.Utils.escapeHTML(item.itemCode) || 'N/A'}</td>
                        <td>${App.Utils.escapeHTML(item.name) || 'N/A'}</td>
                        <td>€${basePrice}</td>
                        <td>${base64Img ? `<img src="data:image/jpeg;base64,${base64Img}" alt="${App.Utils.escapeHTML(item.name) || 'Item'}" style="max-width:50px; height:auto;" />` : 'No Image'}</td>
                    </tr>`;
            });
            availableItemsPreviewHTML += `</tbody></table>`;
        } else {
            // Message if no items are available.
            availableItemsPreviewHTML = '<p>You have no available items to put up for auction. Please create an item first.</p>';
        }

        // Full HTML for the auction creation form.
        const formHTML = `
            <section class="create-form">
                <h2>Create New Auction</h2>
                ${ (availableItems && availableItems.length > 0) ? `
                <form id="sellPageCreateAuctionForm">
                    <div>
                        <label for="sellItemIds">Select Items for Auction (Hold Ctrl/Cmd to select multiple):</label>
                        <select id="sellItemIds" name="itemIds" multiple required size="5">
                            ${itemsOptionsHTML}
                        </select>
                        ${availableItemsPreviewHTML}
                    </div>
                    <div>
                        <label for="sellMinIncrement">Minimum Bid Increment (€, whole number):</label>
                        <input type="number" id="sellMinIncrement" name="minIncrement" min="1" step="1" required />
                    </div>
                    <div>
                        <label for="sellDeadline">Deadline (Date and Time):</label>
                        <input type="datetime-local" id="sellDeadline" name="deadline" required />
                    </div>
                    <div>
                        <input type="submit" value="Create Auction" />
                    </div>
                </form>
                ` : '<p>You have no available items to put up for auction. Please create an item first.</p>' }
            </section>
        `;
        sellViewContainer.insertAdjacentHTML('beforeend', formHTML);
        // Add event listener only if the form was rendered (i.e., items were available).
        const formElement = document.getElementById('sellPageCreateAuctionForm');
        if (formElement) {
            formElement.addEventListener('submit', _handleCreateAuctionSubmit);
        }
    }

    /**
     * Renders the list of the user's open auctions.
     * @private
     * @param {Array<object>} openAuctions - Array of SellerAuctionListItemDTO objects.
     * @param {number} loginTimestampMillis - User's login timestamp for time calculations.
     */
    function _renderOpenAuctions(openAuctions, loginTimestampMillis) {
        let auctionsHTML = '<section class="auction-list"><h2>Your Open Auctions</h2><p>(Ordered by creation date, oldest first)</p>';
        if (openAuctions && openAuctions.length > 0) {
            auctionsHTML += `
                <table>
                    <thead>
                        <tr>
                            <th>Auction ID</th>
                            <th>Items (Preview)</th>
                            <th>Current Max Bid</th>
                            <th>Time Remaining</th>
                        </tr>
                    </thead>
                    <tbody>
            `;
            openAuctions.forEach(dto => {
                const auction = dto.auction; // The core auction data.
                // Ensure auction and deadline data are present.
                if (!auction || typeof auction.deadline !== 'number') {
                    console.warn("SellPage (_renderOpenAuctions): Skipping open auction due to invalid data:", dto);
                    return;
                }
                // Build a list of item names and codes for display.
                const itemsListHTML = dto.items && dto.items.length > 0 ?
                    `<ul>${dto.items.map(item => `<li>${App.Utils.escapeHTML(item.name) || 'Unnamed Item'} (Code: ${App.Utils.escapeHTML(item.itemCode) || 'N/A'})</li>`).join('')}</ul>`
                    : 'No items listed';
                // Determine the display for the highest bid.
                const maxBid = dto.highestBid && typeof dto.highestBid.amount === 'number' ?
                    `€${dto.highestBid.amount.toFixed(2)}` : 'No bids yet';

                auctionsHTML += `
                    <tr data-auction-id="${auction.id || ''}" style="cursor: pointer;">
                        <td>${auction.id || 'N/A'}</td>
                        <td>${itemsListHTML}</td>
                        <td>${maxBid}</td>
                        <td>${App.Utils.getTimeRemaining(loginTimestampMillis, auction.deadline)}</td>
                    </tr>
                `;
            });
            auctionsHTML += `</tbody></table>`;
        } else {
            auctionsHTML += `<p>You have no open auctions.</p>`;
        }
        auctionsHTML += `</section>`;
        sellViewContainer.insertAdjacentHTML('beforeend', auctionsHTML);
    }

    /**
     * Renders the list of the user's closed auctions.
     * @private
     * @param {Array<object>} closedAuctions - Array of SellerAuctionListItemDTO objects.
     */
    function _renderClosedAuctions(closedAuctions) {
        let auctionsHTML = '<section class="auction-list"><h2>Your Closed Auctions</h2><p>(Ordered by creation date, oldest first)</p>';
        if (closedAuctions && closedAuctions.length > 0) {
            auctionsHTML += `
                <table>
                    <thead>
                        <tr>
                            <th>Auction ID</th>
                            <th>Items (Preview)</th>
                            <th>Winning Price</th>
                            <th>Winner</th>
                        </tr>
                    </thead>
                    <tbody>
            `;
            closedAuctions.forEach(dto => {
                const auction = dto.auction; // The core auction data.
                if (!auction) {
                    console.warn("SellPage (_renderClosedAuctions): Skipping closed auction due to missing data:", dto);
                    return;
                }
                // Build a list of item names and codes.
                const itemsListHTML = dto.items && dto.items.length > 0 ?
                    `<ul>${dto.items.map(item => `<li>${App.Utils.escapeHTML(item.name) || 'Unnamed Item'} (Code: ${App.Utils.escapeHTML(item.itemCode) || 'N/A'})</li>`).join('')}</ul>`
                    : 'No items listed';
                // Determine display for winning price.
                const winningPrice = auction.winningPrice != null ? `€${auction.winningPrice.toFixed(2)}` : 'N/A (No winner)';
                // Determine display for winner's username.
                const winnerUsername = dto.winner && dto.winner.username ? App.Utils.escapeHTML(dto.winner.username) : 'No Winner';

                auctionsHTML += `
                    <tr data-auction-id="${auction.id || ''}" style="cursor: pointer;">
                        <td>${auction.id || 'N/A'}</td>
                        <td>${itemsListHTML}</td>
                        <td>${winningPrice}</td>
                        <td>${winnerUsername}</td>
                    </tr>
                `;
            });
            auctionsHTML += `</tbody></table>`;
        } else {
            auctionsHTML += `<p>You have no closed auctions.</p>`;
        }
        auctionsHTML += `</section>`;
        sellViewContainer.insertAdjacentHTML('beforeend', auctionsHTML);
    }

    /**
     * Attaches click event listeners to auction rows in both open and closed auction tables.
     * Clicking a row navigates to the AuctionDetail view for that auction.
     * @private
     */
    function _attachAuctionRowClickListeners() {
        // Select all table rows with a 'data-auction-id' attribute.
        sellViewContainer.querySelectorAll('tr[data-auction-id]').forEach(row => {
            row.addEventListener('click', (e) => {
                const auctionId = e.currentTarget.getAttribute('data-auction-id');
                if (auctionId) {
                    // Navigate to AuctionDetail, passing 'sell' as origin for correct back navigation.
                    App.Orchestrator.navigateToView('auctionDetail', { auctionId: parseInt(auctionId, 10), origin: 'sell' });
                }
            });
        });
    }

    // --- Event Handlers for Form Submissions ---

    /**
     * Handles the submission of the "Create New Item" form.
     * Performs client-side validation and sends data to CreateItemServlet via AJAX.
     * @private
     * @param {Event} event - The form submission event.
     */
    function _handleCreateItemSubmit(event) {
        event.preventDefault(); // Prevent default browser submission.
        const form = event.target; // The submitted form element.

        // Client-side validation.
        if (!form.checkValidity()) {
            App.Orchestrator.showMessage("Please fill all required fields correctly for the item.", true);
            form.reportValidity(); // Trigger browser's built-in validation UI.
            return;
        }
        // Specific check for file input, as `required` might not be fully reliable for empty files.
        const imageFile = form.querySelector('#sellImage').files[0];
        if (!imageFile || imageFile.size === 0) {
            App.Orchestrator.showMessage("Please select an image for the item.", true);
            return;
        }

        const formData = new FormData(form); // Collect form data.
        // Make AJAX POST request to CreateItemServlet.
        // The last `true` indicates `makeCall` should reset the form upon sending.
        makeCall("POST", "CreateItemServlet", formData, function(req) {
            if (req.readyState === XMLHttpRequest.DONE) {
                App.Orchestrator.clearMessages(); // Clear previous messages.
                try {
                    const response = JSON.parse(req.responseText);
                    if (req.status === 201) { // HTTP 201 Created.
                        App.Orchestrator.showMessage(response.successMessage || "Item created successfully!", false);
                        App.SellPage.init(); // Refresh Sell page data to show the new item in available list.
                    } else {
                        App.Orchestrator.showMessage(response.errorMessage || "Error creating item. Status: " + req.status, true);
                    }
                } catch (e) {
                    console.error("SellPage: Error processing CreateItem response:", e, req.responseText);
                    App.Orchestrator.showMessage("Error processing server response for item creation.", true);
                }
            }
        }, true); // Reset form after submission attempt.
    }

    /**
     * Handles the submission of the "Create New Auction" form.
     * Performs client-side validation and sends data to CreateAuctionServlet via AJAX.
     * @private
     * @param {Event} event - The form submission event.
     */
    function _handleCreateAuctionSubmit(event) {
        event.preventDefault(); // Prevent default browser submission.
        const form = event.target; // The submitted form element.

        // Client-side validation.
        if (!form.checkValidity()) {
            App.Orchestrator.showMessage("Please fill all required fields correctly for the auction.", true);
            form.reportValidity();
            return;
        }
        // Validate that at least one item is selected.
        const itemIdsElement = form.querySelector('#sellItemIds');
        if (!itemIdsElement || itemIdsElement.selectedOptions.length === 0) {
            App.Orchestrator.showMessage("Please select at least one item for the auction.", true);
            return;
        }
        // Validate deadline: must be at least 10 minutes in the future.
        const deadlineValue = form.querySelector('#sellDeadline').value;
        if (deadlineValue) {
            const deadlineDate = new Date(deadlineValue);
            const nowPlus3Min = new Date(new Date().getTime() + 3 * 60000); // Current time + 3 minutes.
            if (deadlineDate < nowPlus3Min) {
                App.Orchestrator.showMessage("Deadline must be at least 3 minutes in the future.", true);
                return;
            }
        } else {
             App.Orchestrator.showMessage("Auction deadline is required.", true); // Should be caught by 'required' but good to check.
             return;
        }
        // Validate minimum bid increment: must be a whole number >= 1.
        const minIncrementInput = form.querySelector('#sellMinIncrement');
        const minIncrement = parseFloat(minIncrementInput.value);
        if (isNaN(minIncrement) || minIncrement < 1 || minIncrement !== Math.floor(minIncrement)) {
            App.Orchestrator.showMessage("Minimum bid increment must be a whole number greater than or equal to 1.", true);
            minIncrementInput.reportValidity(); // Show browser validation for the specific field.
            return;
        }

        const formData = new FormData(form); // Collect form data.
        // Make AJAX POST request to CreateAuctionServlet.
        makeCall("POST", "CreateAuctionServlet", formData, function(req) {
            if (req.readyState === XMLHttpRequest.DONE) {
                App.Orchestrator.clearMessages();
                try {
                    const response = JSON.parse(req.responseText);
                    if (req.status === 201) { // HTTP 201 Created.
                        App.Orchestrator.showMessage(response.successMessage || "Auction created successfully!", false);
                        // Set 'lastAction' in user-specific localStorage via orchestrator.
                        App.Orchestrator.setUserDataWithExpiry('lastAction', 'sell_related');
                        App.SellPage.init(); // Refresh Sell page data.
                    } else {
                        App.Orchestrator.showMessage(response.errorMessage || "Error creating auction. Status: " + req.status, true);
                    }
                } catch (e) {
                    console.error("SellPage: Error processing CreateAuction response:", e, req.responseText);
                    App.Orchestrator.showMessage("Error processing server response for auction creation.", true);
                }
            }
        }, true); // Reset form after submission attempt.
    }

    /**
     * Loads all data for the Sell page (available items, open/closed auctions) from the server
     * and renders the entire view.
     * @private
     */
    function _loadAndRenderAllData() {
        // Display loading message.
        sellViewContainer.innerHTML = "<p>Loading your sales data...</p>";
        // Make AJAX GET request to SellPageServlet.
        makeCall("GET", "SellPageServlet", null, function(req) {
            if (req.readyState === XMLHttpRequest.DONE) {
                sellViewContainer.innerHTML = ''; // Clear loading message.
                if (req.status === 200) {
                    // Request successful.
                    try {
                        const data = JSON.parse(req.responseText); // Parse JSON response.
                        // Get login timestamp from response or orchestrator.
                        const loginTimestamp = data.loginTimestamp || App.Orchestrator.getLoginTimestamp();

                        // Display any top-level page messages from server (less common for RIA data fetches).
                        if (data.pageErrorMessage) App.Orchestrator.showMessage(data.pageErrorMessage, true);
                        if (data.pageSuccessMessage) App.Orchestrator.showMessage(data.pageSuccessMessage, false);

                        // Render all sections of the Sell page.
                        _renderCreateItemForm();
                        _renderCreateAuctionForm(data.availableItems || []);
                        _renderOpenAuctions(data.openAuctions || [], loginTimestamp);
                        _renderClosedAuctions(data.closedAuctions || []);
                        // Attach click listeners to auction table rows.
                        _attachAuctionRowClickListeners();

                    } catch (e) {
                        console.error("SellPage: Error parsing data from SellPageServlet:", e, "Response:", req.responseText);
                        App.Orchestrator.showMessage('Error processing your sales data.', true);
                        sellViewContainer.insertAdjacentHTML('beforeend', '<p class="error-message">Could not load your sales data due to a client-side error.</p>');
                    }
                } else if (req.status === 401) { // Unauthorized
                    App.Orchestrator.showMessage('Session expired or unauthorized. Redirecting to login...', true);
                    setTimeout(() => window.location.href = 'login.html', 2000);
                }
                else {
                    // Handle other HTTP error statuses.
                    App.Orchestrator.showMessage('Error loading sales data from server. Status: ' + req.status, true);
                    sellViewContainer.insertAdjacentHTML('beforeend', '<p class="error-message">Could not load your sales data from the server.</p>');
                }
            }
        });
    }

    // --- Public API for App.SellPage ---
    return {
        /**
         * Initializes the Sell page view.
         * Fetches and renders all necessary data for the page.
         */
        init: function() {
            _loadAndRenderAllData();
        },

        /**
         * Hides the Sell page view and clears its content.
         * Called by the orchestrator when switching to another view.
         */
        hide: function() {
            sellViewContainer.innerHTML = ''; // Clear all content.
            // Any other specific cleanup for the SellPage module can be done here.
        }
    };
})(); // End of App.SellPage IIFE.