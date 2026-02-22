
window.App = window.App || {};
App.BuyPage = (function() {
    "use strict";

    const buyViewContainer = document.getElementById('buy-view');

    // _renderSearchForm remains the same
    function _renderSearchForm(currentKeyword = '') {
        const searchFormHTML = `
            <section class="search-form">
                <h2>Search Open Auctions</h2>
                <form id="buyPageSearchForm">
                    <div>
                        <label for="buyKeywordInput">Keyword:</label>
                        <input type="text" id="buyKeywordInput" name="keyword" value="${App.Utils.escapeHTML(currentKeyword)}" />
                    </div>
                    <div>
                        <input type="submit" value="Search" />
                    </div>
                </form>
            </section>
        `;
        buyViewContainer.insertAdjacentHTML('beforeend', searchFormHTML);

        const formElement = document.getElementById('buyPageSearchForm');
        if (formElement) {
            formElement.addEventListener('submit', (e) => {
                e.preventDefault();
                const keywordVal = document.getElementById('buyKeywordInput').value.trim();
                _loadAndRenderData({ keyword: keywordVal, initialAuctionIds: null });
            });
        }
    }


    /**
     * Renders the list of open auctions (search results or visited auctions).
     * MODIFIED: Now displays all items for each auction.
     * @private
     * @param {Array<object>} searchResults - An array of AuctionWithItemsDTO objects.
     * @param {number} loginTimestampMillis - The user's login timestamp.
     * @param {object} displayContext - Object containing title, subtitle, and noResults message.
     */
    function _renderSearchResults(searchResults, loginTimestampMillis, displayContext) {
        let resultsHTML = '<section class="auction-list">';
        resultsHTML += `<h2>${App.Utils.escapeHTML(displayContext.title)}</h2>`;
        if (displayContext.subtitle) {
            resultsHTML += `<p>${App.Utils.escapeHTML(displayContext.subtitle)}</p>`;
        }

        if (searchResults && searchResults.length > 0) {
            resultsHTML += `
                <table>
                    <thead><tr><th>Auction ID</th><th>Items</th><th>Time Remaining</th></tr></thead>
                    <tbody>
            `;
            searchResults.forEach(dto => { // dto is now AuctionWithItemsDTO
                const auction = dto.auction;
                if (!auction || typeof auction.deadline !== 'number') return;

                // MODIFIED: Build HTML for all items in the auction
                let itemsHtmlList = 'No items listed.';
                if (dto.items && dto.items.length > 0) {
                    itemsHtmlList = '<ul>';
                    dto.items.forEach(item => {
                        itemsHtmlList += `<li>${App.Utils.escapeHTML(item.name)} (Code: ${App.Utils.escapeHTML(item.itemCode)})</li>`;
                        // Optionally, you could add item image thumbnails here if desired, though it might make rows very tall.
                        // Example for image:
                        // if (item.base64Image) {
                        //     itemsHtmlList += ` <img src="data:image/jpeg;base64,${item.base64Image}" alt="${App.Utils.escapeHTML(item.name)}" style="max-height:30px; vertical-align:middle; margin-left:5px;">`;
                        // }
                    });
                    itemsHtmlList += '</ul>';
                }

                resultsHTML += `
                    <tr data-auction-id="${auction.id || ''}" data-action="offer" style="cursor: pointer;">
                        <td>${auction.id || 'N/A'}</td>
                        <td>${itemsHtmlList}</td> 
                        <td>${App.Utils.getTimeRemaining(loginTimestampMillis, auction.deadline)}</td>
                    </tr>
                `;
            });
            resultsHTML += `</tbody></table>`;
        } else {
            resultsHTML += `<p>${App.Utils.escapeHTML(displayContext.noResults)}</p>`;
        }
        resultsHTML += `</section>`;
        buyViewContainer.insertAdjacentHTML('beforeend', resultsHTML);
    }

    /**
     * Renders the list of auctions won by the current user.
     * MODIFIED: Now displays all items for each won auction.
     * @private
     * @param {Array<object>} wonAuctions - An array of AuctionWithItemsDTO objects for won auctions.
     */
    function _renderWonAuctions(wonAuctions) {
        let wonAuctionsHTML = `<section class="won-auctions-list"><h2>Your Won Auctions</h2>`;
        if (wonAuctions && wonAuctions.length > 0) {
            wonAuctionsHTML += `
                <table>
                    <thead><tr><th>Auction ID</th><th>Items</th><th>Winning Price</th></tr></thead>
                    <tbody>`;
            wonAuctions.forEach(dto => { // dto is now AuctionWithItemsDTO
                const auction = dto.auction;
                if (!auction) return;

                // MODIFIED: Build HTML for all items in the won auction
                let itemsHtmlList = 'No items listed.';
                if (dto.items && dto.items.length > 0) {
                    itemsHtmlList = '<ul>';
                    dto.items.forEach(item => {
                        itemsHtmlList += `<li>${App.Utils.escapeHTML(item.name)} (Code: ${App.Utils.escapeHTML(item.itemCode)})</li>`;
                    });
                    itemsHtmlList += '</ul>';
                }

                wonAuctionsHTML += `
                    <tr data-auction-id="${auction.id || ''}" data-action="detail" style="cursor: pointer;">
                        <td>${auction.id || 'N/A'}</td>
                        <td>${itemsHtmlList}</td> 
                        <td>€${auction.winningPrice != null ? auction.winningPrice.toFixed(2) : 'N/A'}</td>
                    </tr>
                `;
            });
            wonAuctionsHTML += `</tbody></table>`;
        } else {
            wonAuctionsHTML += `<p>You have not won any auctions yet.</p>`;
        }
        wonAuctionsHTML += `</section>`;
        buyViewContainer.insertAdjacentHTML('beforeend', wonAuctionsHTML);
    }

    // _attachAuctionRowClickListeners remains the same
    function _attachAuctionRowClickListeners() {
        buyViewContainer.querySelectorAll('tr[data-auction-id]').forEach(row => {
            row.addEventListener('click', (e) => {
                const auctionId = e.currentTarget.getAttribute('data-auction-id');
                const action = e.currentTarget.getAttribute('data-action');
                if (auctionId) {
                    const numericAuctionId = parseInt(auctionId, 10);
                    if (action === 'offer') {
                        App.Orchestrator.navigateToView('offer', { auctionId: numericAuctionId });
                    } else if (action === 'detail') {
                        App.Orchestrator.navigateToView('auctionDetail', { auctionId: numericAuctionId, origin: 'buy' });
                    }
                }
            });
        });
    }

    // _loadAndRenderData remains largely the same in its calling structure,
    // but it will now receive DTOs containing List<Item> from the servlet.
    function _loadAndRenderData(params = {}) {
        let servletURL = 'BuyPageServlet';
        let queryParams = new URLSearchParams();
        let displayContext = { title: "", subtitle: "", noResults: "" };

        const keywordFromForm = params.keyword;
        const initialIdsFromOrchestrator = params.initialAuctionIds;
        let activeKeywordForSearch = null;
        let idsToLoadForServlet = null;

        if (keywordFromForm !== undefined && keywordFromForm !== null && keywordFromForm.trim() !== "") {
            activeKeywordForSearch = keywordFromForm.trim();
            queryParams.append('keyword', activeKeywordForSearch);
            App.Orchestrator.setUserDataWithExpiry('lastSearchKeyword', activeKeywordForSearch);
            displayContext.title = `Search Results for "${App.Utils.escapeHTML(activeKeywordForSearch)}"`;
            displayContext.subtitle = "(Ordered by time remaining to deadline, most urgent first)";
            displayContext.noResults = "No open auctions found matching your criteria.";
        } else if (initialIdsFromOrchestrator && Array.isArray(initialIdsFromOrchestrator) && initialIdsFromOrchestrator.length > 0) {
            idsToLoadForServlet = initialIdsFromOrchestrator;
            queryParams.append('ids', idsToLoadForServlet.join(','));
            App.Orchestrator.removeUserData('lastSearchKeyword');
            displayContext.title = "Previously Visited Open Auctions";
            displayContext.noResults = "None of your recently visited auctions are currently open for bidding.";
        } else {
            const visitedAuctionsFromStorage = App.Orchestrator.getUserDataWithExpiry('visitedAuctions');
            if (visitedAuctionsFromStorage && Array.isArray(visitedAuctionsFromStorage) && visitedAuctionsFromStorage.length > 0) {
                idsToLoadForServlet = visitedAuctionsFromStorage;
                queryParams.append('ids', idsToLoadForServlet.join(','));
                displayContext.title = "Recently Viewed Open Auctions";
                displayContext.noResults = "None of your recently viewed auctions are currently open for bidding.";
            } else {
                displayContext.title = "Open Auctions";
                displayContext.noResults = "No open auctions to display. Try searching with a keyword.";
            }
            App.Orchestrator.removeUserData('lastSearchKeyword');
        }

        if (queryParams.toString()) {
            servletURL += '?' + queryParams.toString();
        }

        buyViewContainer.innerHTML = '<p>Loading auctions...</p>';
        const keywordForForm = activeKeywordForSearch !== null ? activeKeywordForSearch : (App.Orchestrator.getUserDataWithExpiry('lastSearchKeyword') || '');

        makeCall("GET", servletURL, null, function(req) {
            if (req.readyState === XMLHttpRequest.DONE) {
                buyViewContainer.innerHTML = '';
                _renderSearchForm(keywordForForm);

                if (req.status === 200) {
                    try {
                        const data = JSON.parse(req.responseText); // data.searchResults and data.wonAuctions now contain AuctionWithItemsDTO
                        const currentLoginTimestamp = data.loginTimestamp || App.Orchestrator.getLoginTimestamp();
                        if (data.errorMessage) App.Orchestrator.showMessage(data.errorMessage, true);
                        _renderSearchResults(data.searchResults || [], currentLoginTimestamp, displayContext);
                        _renderWonAuctions(data.wonAuctions || []);
                        _attachAuctionRowClickListeners();
                    } catch (e) {
                        console.error("BuyPage: Error parsing data:", e, "Response text:", req.responseText);
                        App.Orchestrator.showMessage('Error processing auction data from server.', true);
                        buyViewContainer.insertAdjacentHTML('beforeend', '<p class="error-message">Could not load auction data (client processing error).</p>');
                    }
                } else if (req.status === 401) {
                    App.Orchestrator.showMessage('Session expired or unauthorized. Redirecting to login...', true);
                    setTimeout(() => window.location.href = 'login.html', 2000);
                } else {
                    App.Orchestrator.showMessage('BuyPage: Error loading data from server. Status: ' + req.status, true);
                    buyViewContainer.insertAdjacentHTML('beforeend', '<p class="error-message">Could not load auction data from server.</p>');
                }
            }
        });
    }

    return {
        init: function(params = {}) {
            _loadAndRenderData(params);
        },
        hide: function() {
            buyViewContainer.innerHTML = '';
        }
    };
})();