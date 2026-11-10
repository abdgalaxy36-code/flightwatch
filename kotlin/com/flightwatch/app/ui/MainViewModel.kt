package com.flightwatch.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flightwatch.app.FlightWatchApp
import com.flightwatch.app.data.BookingLinks
import com.flightwatch.app.data.AirlineSites
import com.flightwatch.app.data.Conditions
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.data.Funnel
import com.flightwatch.app.data.IgnavApi
import com.flightwatch.app.data.IgnavLinkCache
import com.flightwatch.app.data.IgnavMapper
import com.flightwatch.app.data.Inbound
import com.flightwatch.app.data.LetsfgApi
import com.flightwatch.app.data.LetsfgException
import com.flightwatch.app.data.OctoTripApi
import com.flightwatch.app.data.PricePoint
import com.flightwatch.app.data.ReturnCutoff
import com.flightwatch.app.data.ReturnLeg
import com.flightwatch.app.data.SearchResponse
import com.flightwatch.app.data.Segment
import com.flightwatch.app.data.SerpApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One swipable route page. Page 1 is Jeddah -> Istanbul. */
data class FlightPageConfig(
    val id: String,
    val title: String,
    val origin: String,
    val destination: String,
)

/** One section of the main page: flights whose return leaves after the cutoff. */
data class SectionUi(
    val index: Int,
    val cutoff: ReturnCutoff,
    val flights: List<FlightCard>,
)

data class FlightsUiState(
    val loading: Boolean = false,
    val note: String = "",
    val error: String? = null,
    val sections: List<SectionUi> = emptyList(),
    val funnel: Funnel? = null,
    val rawCount: Int = 0,
    val updatedAt: Long = 0L,
    val connected: Boolean = false,
    /** True while showing a saved result rather than a freshly searched one. */
    val fromCache: Boolean = false,
    /** Cheapest first, or earliest departure first. */
    val sortByPrice: Boolean = true,
    /** Only flights whose return leaves after 7 PM rather than after 5 PM. */
    val after7pm: Boolean = false,
)

class MainViewModel(internal val app: FlightWatchApp) : ViewModel() {

    private val api: LetsfgApi = app.api

    private val _state = MutableStateFlow(
        FlightsUiState(
            connected = app.tokenStore.hasCredentials(),
            sortByPrice = app.settings.sortByPrice,
            after7pm = app.settings.returnAfter7pm,
        ),
    )
    val state: StateFlow<FlightsUiState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<PricePoint>>(emptyList())
    val history: StateFlow<List<PricePoint>> = _history.asStateFlow()

    /** Live favourites for the current airport page (and every other page too). */
    val favorites = app.favorites.flow

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Which return leg the traveller picked, keyed by "sectionIndex|flightKey". */
    private val _selectedReturns = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedReturns: StateFlow<Map<String, String>> = _selectedReturns.asStateFlow()

    private var searchJob: Job? = null
    private var linkJob: Job? = null

    /** When the links were last looked for, so they are not looked for twice. */
    private var lastLinkRefresh = 0L

    /**
     * Look for booking links again if the ones on screen have gone stale.
     *
     * The links stop working after about fifteen minutes, so when the app is
     * opened again after a while the links are looked for once more.
     */
    fun refreshLinksIfStale() {
        if (System.currentTimeMillis() - lastLinkRefresh < LINK_REFRESH_MS) return
        refreshLinks()
    }

    /** Look for booking links right now (after the traveller changes link settings). */
    fun refreshLinksNow() {
        lastLinkRefresh = 0L
        refreshLinks()
    }
    private var lastSearchKey: String? = null

    /** The airline-site fares from LetsFG. */
    private var airlineCards: List<FlightCard> = emptyList()

    /** Extra fares from Google Flights, only when a SerpApi key is set. */
    private var extraCards: List<FlightCard> = emptyList()

    /** One swipable page per departure airport; arrival airport gets no page. */
    val pages: List<FlightPageConfig>
        get() = app.settings.departureAirports.map { origin ->
            FlightPageConfig(
                id = "${origin}-${app.settings.destination}",
                title = "$origin → ${app.settings.destination}",
                origin = origin,
                destination = app.settings.destination,
            )
        }

    /** Currently shown departure-airport page. */
    private val _activePageIndex = MutableStateFlow(0)
    val activePageIndex: StateFlow<Int> = _activePageIndex.asStateFlow()

    val currentPage: FlightPageConfig
        get() = pages.elementAtOrElse(_activePageIndex.value) { pages.first() }

    /** Switch the visible departure-airport page and show its saved fares. */
    fun setActivePage(index: Int) {
        _activePageIndex.value = index.coerceIn(0, pages.lastIndex)
        val page = currentPage
        app.settings.origin = page.origin
        app.settings.destination = page.destination
        val key = currentSearchKey()
        if (!app.searchCache.has(key)) {
            // Drop the previous airport's flights so they cannot be mistaken
            // for this page's results while the new search runs.
            airlineCards = emptyList()
            extraCards = emptyList()
            _selectedReturns.value = emptyMap()
            _state.value = FlightsUiState(
                loading = true,
                note = "Searching ${page.origin}…",
                connected = app.tokenStore.hasCredentials(),
                sortByPrice = app.settings.sortByPrice,
                after7pm = app.settings.returnAfter7pm,
            )
            search(force = false)
        } else {
            applyResults(app.searchCache.load(key) ?: return, fromCache = true)
            refreshLinks()
        }
    }

    val settings get() = app.settings
    val selectedFlights = MutableStateFlow<List<com.flightwatch.app.data.FlightCard>>(emptyList())
    val tokenStore get() = app.tokenStore
    val usage get() = app.usage

    init {
        loadHistory()
        viewModelScope.launch {
            val check = com.flightwatch.app.data.ConnectionCheck.run()
            android.util.Log.i(
                TAG,
                "connection: dnsOk=${check.dnsOk} ${check.dnsNote} | httpsOk=${check.httpsOk} ${check.httpsNote}",
            )
        }
        // Show the saved fares if we have them; otherwise pay for one search.
        if (app.searchCache.has(currentSearchKey())) {
            loadCached()
            refreshLinks()
        } else {
            search(force = true)
        }
        // Match the active departure-airport page to the searched origin.
        _activePageIndex.value = pages.indexOfFirst { it.origin == app.settings.origin }.coerceAtLeast(0)

        // Re-fetch booking links whenever the user changes the enabled links sources.
        viewModelScope.launch {
            app.settings.linksSourcesFlow.collect { _ ->
                refreshLinks()
            }
        }
    }

    fun context(): Context = app

    // --------------------------------------------------------------- search

    /** A key that changes whenever the search inputs change. */
    fun currentSearchKey(): String {
        val s = app.settings
        return "${s.searchSource}-${s.origin}-${s.destination}-${s.outboundDate}-${s.returnDate}-" +
            "${s.tripMode}-${s.flexibleDays}-" +
            "${s.selectedAirlines.sorted()}-${s.allowLateArrival}-${s.adults}"
    }

    /** Whether the currently selected engine has what it needs to search. */
    private fun isConnected(): Boolean =
        if (app.settings.searchSource == "ignav") app.settings.hasIgnavApiKey()
        else app.tokenStore.hasCredentials()

    /**
     * Show the saved result if there is one. This never touches the network -
     * an airline_only search costs real credits, and re-running the same one
     * costs double, so the app only searches when there is a reason to.
     */
    fun loadCached() {
        app.settings.normalizeDates()
        val key = currentSearchKey()
        val cached = app.searchCache.load(key) ?: return
        applyResults(cached, fromCache = true)
    }

    /**
     * Fetch fresh fares. Only ever called on an explicit request, and skipped
     * when the saved result is still fresh enough to be the answer.
     */
    fun search(force: Boolean = false) {
        app.settings.normalizeDates()
        val key = currentSearchKey()
        if (!force) {
            val age = System.currentTimeMillis() - app.searchCache.savedAt(key)
            if (app.searchCache.has(key) && age < FRESH_FOR_MS) {
                // Nothing new to fetch yet - the saved result is still current.
                _state.value = _state.value.copy(
                    error = "Last checked ${describeAge(age)} ago. Prices are cached - " +
                        "checking again costs search credits.",
                )
                return
            }
        }
        lastSearchKey = key
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, note = "Starting search…")
            try {
                val s = app.settings
                val response = if (s.searchSource == "ignav") searchIgnav(s) else searchLetsfg(s)
                app.searchCache.save(key, response)
                applyResults(response, fromCache = false)
                refreshLinks()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e(TAG, "search failed", e)
                val message = if (e is LetsfgException && e.status == 429) {
                    "Search credits are used up for now. " +
                        "Your saved fares are still shown below."
                } else {
                    e.message ?: "Search failed"
                }
                // Keep whatever is already on screen - the traveller still has it.
                _state.value = _state.value.copy(
                    loading = false,
                    error = message,
                    connected = isConnected(),
                )
            }
        }
    }

    /** One ignav.com round-trip request, mapped into the standard offers shape. */
    private suspend fun searchIgnav(s: com.flightwatch.app.data.SettingsStore): SearchResponse {
        val client = app.ignavApi()
            ?: throw IgnavApi.IgnavException(
                "Add your ignav.com API key in Settings to search with ignav.com.",
            )
        _state.value = _state.value.copy(note = "Searching ignav.com…")
        val ignav = client.searchRoundTrip(
            origin = s.origin,
            destination = s.destination,
            departureDate = s.outboundDate,
            // normalizeDates already forced return = departure + the gap setting.
            returnDate = s.returnDate,
            adults = s.adults,
            // Direct-only, matching the app's fixed no-connecting-flights rule.
            maxStops = 0,
            // market decides the currency; SA -> SAR, the only one the app shows.
            market = "SA",
            // JED's evening rule: depart at/after 16:00 local, applied server-side.
            departureTimeRange = if (FlightRules.isTimeRuledOrigin(s.origin)) {
                IgnavApi.TimeRange(earliest_hour = 16, latest_hour = 23)
            } else {
                null
            },
        )
        val offers = IgnavMapper.mapToOffers(ignav)
        android.util.Log.i(TAG, "ignav search: itineraries=${(ignav.itineraries ?: emptyList()).size} offers=${offers.size}")
        return SearchResponse(offers = offers, totalResults = offers.size)
    }

    /** The LetsFG airline-only search, with the month/flexible date windows. */
    private suspend fun searchLetsfg(s: com.flightwatch.app.data.SettingsStore): SearchResponse =
        api.search(
            LetsfgApi.SearchParams(
                origin = s.origin,
                destination = s.destination,
                dateFrom = s.outboundDate,
                returnDate = s.returnDate,
                adults = s.adults,
                children = 0,
                currency = FlightRules.CURRENCY,
                cabin = "M",
                airlineOnly = true,
                dateTo = windowDateTo(s),
                returnFrom = windowReturnFrom(s),
                returnTo = windowReturnTo(s),
            ),
            onProgress = { note ->
                _state.value = _state.value.copy(note = note)
            },
        )

    /**
     * Switch the search engine (LetsFG <-> ignav.com) and show what the newly
     * selected one returns. Each engine has its own cache key, so switching
     * back shows its saved fares without paying for another search.
     */
    fun switchSearchSource(source: String) {
        val s = app.settings
        if (s.searchSource == source) return
        s.searchSource = source
        if (source == "ignav" && !s.hasIgnavApiKey()) {
            _state.value = _state.value.copy(
                loading = false,
                error = "Add your ignav.com API key in Settings to search with ignav.com.",
            )
            return
        }
        if (app.searchCache.has(currentSearchKey())) {
            loadCached()
            refreshLinks()
        } else {
            search(force = true)
        }
    }

    /** Turn a search response into the two sections and push it to the UI. */
    private fun applyResults(response: com.flightwatch.app.data.SearchResponse, fromCache: Boolean) {
        val s = app.settings
        val offers = response.offers ?: emptyList()
        airlineCards = FlightRules.group(offers, s.selectedAirlines)
            .filter { FlightRules.matchesOutbound(it, s.allowLateArrival, s.origin) }

        val sections = buildSections(allCards())
        val funnel = FlightRules.diagnose(offers, s.selectedAirlines, s.origin)
        saveHistory(allCards())

        android.util.Log.i(
            TAG,
            "results applied (${if (fromCache) "cached" else "fresh"}): offers=${offers.size} " +
                "cards=${airlineCards.size} section5pm=${sections.getOrNull(0)?.flights?.size ?: 0} " +
                "section7pm=${sections.getOrNull(1)?.flights?.size ?: 0} " +
                "lateArrival=${s.allowLateArrival} funnel=${funnel.total}/${funnel.direct}/" +
                "${funnel.afterDepartureTime}/${funnel.afterSameDayArrival}/" +
                "${funnel.afterArrivalWindow}/${funnel.withOfficialPrice}",
        )
        android.util.Log.i(
            TAG,
            "cards: " + airlineCards.joinToString(" | ") { card ->
                "${card.airlineCode}${card.flightNumber} ${card.departureTime}-${card.arrivalTime} " +
                    card.returns.joinToString(",") { "${it.flightNumber} ${it.departureTime} ${it.price}" }
            },
        )

        _state.value = FlightsUiState(
            loading = false,
            note = "",
            error = null,
            sections = sections,
            funnel = funnel,
            rawCount = offers.size,
            updatedAt = app.searchCache.savedAt(currentSearchKey()),
            connected = isConnected(),
            fromCache = fromCache,
            sortByPrice = s.sortByPrice,
            after7pm = s.returnAfter7pm,
        )
    }

    /** Sort the list cheapest first, or by departure time. */
    fun setSortByPrice(value: Boolean) {
        app.settings.sortByPrice = value
        _state.value = _state.value.copy(sortByPrice = value)
    }

    /** Switch between the after-5-PM and after-7-PM return cutoffs. */
    fun setAfter7pm(value: Boolean) {
        app.settings.returnAfter7pm = value
        _state.value = _state.value.copy(after7pm = value)
    }

    /**
     * Relax or tighten the arrival rule without paying for another search.
     *
     * The saved response holds every offer the search returned, so the new
     * rule is simply applied to it again.
     */
    fun setAllowLateArrival(value: Boolean) {
        val cached = app.searchCache.load(currentSearchKey())
        app.settings.allowLateArrival = value
        if (cached != null) {
            applyResults(cached, fromCache = true)
            refreshLinks()
        } else {
            search(force = true)
        }
    }

    /** Every card on screen: the airline-site fares plus anything extra. */
    private fun allCards(): List<FlightCard> = airlineCards + extraCards

    /** End of the outbound window for month/flexible modes, else null. */
    private fun windowDateTo(s: com.flightwatch.app.data.SettingsStore): String? =
        when (s.tripMode) {
            "month" -> java.time.YearMonth.from(java.time.LocalDate.parse(s.outboundDate))
                .atEndOfMonth().toString()
            "flexible" -> java.time.LocalDate.parse(s.outboundDate)
                .plusDays((s.flexibleDays - 1).toLong()).toString()
            else -> null
        }

    /** Start of the return window for month mode, else null. */
    private fun windowReturnFrom(s: com.flightwatch.app.data.SettingsStore): String? =
        if (s.tripMode == "month" && s.returnDate.isNotBlank()) {
            java.time.YearMonth.from(java.time.LocalDate.parse(s.returnDate)).atDay(1).toString()
        } else {
            null
        }

    /** End of the return window for month mode, else null. */
    private fun windowReturnTo(s: com.flightwatch.app.data.SettingsStore): String? =
        if (s.tripMode == "month" && s.returnDate.isNotBlank()) {
            java.time.YearMonth.from(java.time.LocalDate.parse(s.returnDate)).atEndOfMonth().toString()
        } else {
            null
        }

    private fun rebuildSections() {
        _state.value = _state.value.copy(sections = buildSections(allCards()))
    }

    // --------------------------------------------------------- booking links

    /** The key the saved links belong to. */
    private fun serpLinkKey(): String {
        val s = app.settings
        return "${s.origin}-${s.destination}-${s.outboundDate}-${s.returnDate}-${s.adults}"
    }

    /**
     * Put a booking link on every flight we can find one for, using the link
     * sources the traveller chose in Settings (multiple enabled at once).
     *
     * LetsFG prices the fares but never says where to buy them, so the same
     * flight is looked up in all enabled sources. Each source is queried once
     * per refresh. Links are cached for 24 hours.
     */
    private fun refreshLinks() {
        app.settings.normalizeDates()
        linkJob?.cancel()
        lastLinkRefresh = System.currentTimeMillis()
        linkJob = viewModelScope.launch {
            val s = app.settings
            val key = serpLinkKey()
            val enabledSources = s.linksSources

            if (enabledSources.contains("serpapi")) {
                val client = app.serpApi()
                if (client != null) {
                    val saved = app.serpLinkCache.load(key)
                    if (saved.isNotEmpty()) attachSerpLinks(saved, client, key)
                    if (!app.serpLinkCache.isFresh(key) || saved.isEmpty()) {
                        try {
                            val flights = client.sweep(
                                origin = s.origin,
                                destination = s.destination,
                                departureDate = s.outboundDate,
                                returnDate = s.returnDate,
                                adults = s.adults,
                            )
                            if (flights.isNotEmpty()) {
                                app.serpLinkCache.save(key, flights)
                                attachSerpLinks(flights, client, key)
                            } else {
                                android.util.Log.i(TAG, "SerpApi booking link lookup found nothing new")
                            }
                            _state.value = _state.value.copy(note = "")
                            android.util.Log.i(TAG, "SerpApi booking link lookup finished: ${flights.size} offers")
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.w(TAG, "SerpApi booking link lookup failed", e)
                        }
                    }
                }
            }

            if (enabledSources.contains("octotrip")) {
                try {
                    val flights = app.octoTripApi.sweep(
                        origin = s.origin,
                        destination = s.destination,
                        departureDate = s.outboundDate,
                        returnDate = s.returnDate,
                        adults = s.adults,
                        onProgress = { note ->
                            _state.value = _state.value.copy(note = note)
                        },
                        onFound = { partial ->
                            if (partial.isNotEmpty()) attachOctoTripLinks(partial)
                        },
                    )
                    if (flights.isNotEmpty()) attachOctoTripLinks(flights) else {
                        android.util.Log.i(TAG, "OctoTrip booking link lookup found nothing new")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w(TAG, "OctoTrip booking link lookup failed", e)
                }
            }

            if (enabledSources.contains("ignav")) {
                val client = app.ignavApi()
                if (client != null) {
                    try {
                        attachIgnavLinks(client)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        android.util.Log.w(TAG, "Ignav booking link lookup failed", e)
                    }
                }
            }

            // kiwi / flowextract - not yet implemented without keys;
            if (enabledSources.contains("kiwi")) {
                android.util.Log.i(TAG, "Kiwi links source not yet implemented")
            }
            if (enabledSources.contains("flowextract")) {
                android.util.Log.i(TAG, "FlowExtract links source not yet implemented")
            }
        }
    }

    /**
     * Exact booking links from ignav.com, via the ignav_id each ignav search
     * puts on its offers. One lookup per displayed itinerary; answers (including
     * "no links" - a free 404) are remembered for a day in [IgnavLinkCache].
     */
    private suspend fun attachIgnavLinks(client: IgnavApi) {
        val cutoff = FlightRules.RETURN_CUTOFFS.first()
        // Ids actually needed: every displayed return, plus the head offer as
        // the card-level fallback openBooking uses.
        val wanted = linkedSetOf<String>()
        for (card in airlineCards) {
            val shown = card.returns.filter { FlightRules.qualifies(it, cutoff) }
            if (shown.isEmpty()) continue
            for (leg in shown) {
                if (leg.bookingUrl == null) leg.ignavId?.let { wanted += it }
            }
            if (card.bookingUrl == null) card.offer.ignavId?.let { wanted += it }
        }
        if (wanted.isEmpty()) return

        var fetched = 0
        for (id in wanted) {
            if (app.ignavLinkCache.get(id) != null) continue
            val entry = try {
                IgnavLinkCache.Entry.from(client.fetchBookingLinks(id))
            } catch (e: IgnavApi.IgnavException) {
                if ("404" in e.message.orEmpty()) {
                    // No usable link for this itinerary - free, and remembered.
                    IgnavLinkCache.Entry.none()
                } else {
                    // Transient failure: don't poison the cache, retry next time.
                    android.util.Log.w(TAG, "ignav booking links for $id failed", e)
                    continue
                }
            }
            app.ignavLinkCache.put(id, entry)
            fetched++
        }

        var attached = 0
        airlineCards = airlineCards.map { card ->
            val shown = card.returns.filter { FlightRules.qualifies(it, cutoff) }
            if (shown.isEmpty()) return@map card
            var changed = false
            val newReturns = card.returns.map { leg ->
                if (leg.bookingUrl != null || leg.ignavId == null) return@map leg
                val entry = app.ignavLinkCache.get(leg.ignavId) ?: return@map leg
                val url = entry.url ?: return@map leg
                changed = true
                attached++
                leg.copy(bookingUrl = url, bookingSeller = entry.seller, bookingPrice = entry.price)
            }
            var cardUrl = card.bookingUrl
            var cardSeller = card.bookingSeller
            var cardPrice = card.bookingPrice
            if (cardUrl == null) {
                val entry = card.offer.ignavId?.let { app.ignavLinkCache.get(it) }
                val url = entry?.url
                if (url != null) {
                    cardUrl = url
                    cardSeller = entry.seller
                    cardPrice = entry.price
                    changed = true
                }
            }
            if (changed) {
                card.copy(
                    returns = newReturns,
                    bookingUrl = cardUrl,
                    bookingSeller = cardSeller,
                    bookingPrice = cardPrice,
                )
            } else {
                card
            }
        }
        android.util.Log.i(
            TAG,
            "ignav booking links: fetched=$fetched attached=$attached of ${airlineCards.size} flights",
        )
        if (attached > 0) rebuildSections()
    }
    // Links are ONLY from the chosen API (SerpApi/OctoTrip). No fallbacks, no guessing.
    // If the API didn't find a booking URL for a flight, it stays null.

    /** Fill any missing booking URL with the official airline site link first, then Google Flights. */
    private fun applyFallbackLinks() {
        val s = app.settings
        // normalizeDates keeps the return at departure + the gap setting.
        val returnDate = s.returnDate
        fun airlineUrl(card: FlightCard, leg: ReturnLeg?): String =
            AirlineSites.urlFor(
                airlineCode = card.airlineCode,
                origin = s.origin,
                destination = s.destination,
                departureDate = s.outboundDate,
                returnDate = returnDate.ifBlank { null },
                flightNumber = card.flightNumber,
                returnFlightNumber = leg?.flightNumber,
            )
        fun googleUrl(card: FlightCard, leg: ReturnLeg?): String =
            BookingLinks.googleFlightsUrl(
                origin = s.origin,
                destination = s.destination,
                departureDate = s.outboundDate,
                returnDate = returnDate.ifBlank { null },
                flightNumber = card.flightNumber,
                returnFlightNumber = leg?.flightNumber,
            )

        var changed = false
        airlineCards = airlineCards.map { card ->
            val newReturns = card.returns.map { leg ->
                if (leg.bookingUrl != null) return@map leg
                changed = true
                // Prefer official airline site URL
                leg.copy(
                    bookingUrl = airlineUrl(card, leg),
                    bookingSeller = card.airline,
                )
            }
            val best = newReturns.minByOrNull { it.price }
            val cardUrl = best?.bookingUrl ?: card.bookingUrl ?: airlineUrl(card, null)
            if (cardUrl != card.bookingUrl) changed = true
            card.copy(
                returns = newReturns,
                bookingUrl = cardUrl,
                bookingSeller = best?.bookingSeller ?: card.bookingSeller ?: card.airline,
                bookingPrice = best?.bookingPrice ?: card.bookingPrice,
            )
        }
        extraCards = extraCards.map { card ->
            val newReturns = card.returns.map { leg ->
                if (leg.bookingUrl != null) return@map leg
                changed = true
                leg.copy(
                    bookingUrl = airlineUrl(card, leg),
                    bookingSeller = card.airline,
                )
            }
            val best = newReturns.minByOrNull { it.price }
            val cardUrl = best?.bookingUrl ?: card.bookingUrl ?: airlineUrl(card, null)
            if (cardUrl != card.bookingUrl) changed = true
            card.copy(
                returns = newReturns,
                bookingUrl = cardUrl,
                bookingSeller = best?.bookingSeller ?: card.bookingSeller ?: card.airline,
                bookingPrice = best?.bookingPrice ?: card.bookingPrice,
            )
        }
        if (changed) {
            rebuildSections()
            android.util.Log.i(TAG, "fallback official airline links applied")
        }
    }

    /** Put SerpApi booking links on the cards.
     *
     * Only resolves a booking URL (1 SerpApi request) for a card that doesn't
     * already have one, and only if a matching SerpApi flight has a token.
     * Resolved URLs are saved back to the cache so they're never resolved twice.
     * If no match or no token, the flight keeps NO booking URL.
     */
    private suspend fun attachSerpLinks(flights: List<SerpApiClient.Flight>, client: SerpApiClient, key: String) {
        if (flights.isEmpty()) return
        val s = app.settings
        var linked = 0
        val updatedFlights = flights.toMutableList()

        airlineCards = airlineCards.map { card ->
            // Find any SerpApi flight matching this OUTBOUND (ignoring return leg).
            // A SerpApi Flight = one complete round-trip with its own bookingToken.
            val outboundMatch = updatedFlights.firstOrNull { f ->
                BookingLinks.normalize(f.flightNumber) == BookingLinks.normalize(card.flightNumber) &&
                f.departureTime.substringBefore(' ') == s.outboundDate &&
                (s.returnDate.isBlank() || f.returnDepartureTime.substringBefore(' ') == s.returnDate)
            }

            // Resolve token once per outbound match, reuse for all return legs.
            var resolvedUrl: String? = null
            var resolvedSeller: String? = null
            var resolvedPrice: Double? = null
            if (outboundMatch != null && outboundMatch.bookingToken.isNotEmpty()) {
                if (outboundMatch.bookingUrl == null) {
                    val url = client.bookingUrl(outboundMatch.bookingToken)
                    if (url != null) {
                        linked++
                        val idx = updatedFlights.indexOfFirst { it.flightNumber == outboundMatch.flightNumber }
                        if (idx >= 0) updatedFlights[idx] = outboundMatch.copy(bookingUrl = url)
                        resolvedUrl = url
                        resolvedSeller = BookingLinks.sellerOf(url)
                        resolvedPrice = outboundMatch.price
                    }
                } else if (outboundMatch.bookingUrl != null) {
                    resolvedUrl = outboundMatch.bookingUrl
                    resolvedSeller = BookingLinks.sellerOf(outboundMatch.bookingUrl)
                    resolvedPrice = outboundMatch.price
                }
            }

            val newReturns = card.returns.map { leg ->
                if (leg.bookingUrl != null) return@map leg  // Already has a link
                if (resolvedUrl != null) {
                    leg.copy(
                        bookingUrl = resolvedUrl,
                        bookingSeller = resolvedSeller,
                        bookingPrice = resolvedPrice,
                    )
                } else {
                    leg
                }
            }
            val best = newReturns.minByOrNull { it.price }
            card.copy(
                returns = newReturns,
                bookingUrl = best?.bookingUrl ?: resolvedUrl,
                bookingSeller = best?.bookingSeller ?: resolvedSeller,
                bookingPrice = best?.bookingPrice ?: resolvedPrice,
            )
        }
        app.serpLinkCache.save(key, updatedFlights)
        val withLinks = airlineCards.count { card -> card.returns.any { it.bookingUrl != null } }
        android.util.Log.i(
            TAG,
            "booking links attached: $linked returns on $withLinks of ${airlineCards.size} flights",
        )
        rebuildSections()
    }

    /** Put OctoTrip booking links on the cards. */
    private suspend fun attachOctoTripLinks(flights: List<OctoTripApi.Flight>) {
        if (flights.isEmpty()) return
        val s = app.settings
        var linked = 0
        airlineCards = airlineCards.map { card ->
            // Match on OUTBOUND only; OctoTrip Flight = one round-trip with one bookingUrl.
            val outboundMatch = flights.firstOrNull { f ->
                val outNumbers = fLegNumbers(f.outbound) + (f.flightNumbers.orEmpty())
                val dateMatch = s.outboundDate.isBlank() || f.outbound?.departureDate?.substringBefore(' ') == s.outboundDate
                outNumbers.any { BookingLinks.normalize(it) == BookingLinks.normalize(card.flightNumber) } && dateMatch
            }

            var resolvedUrl: String? = null
            var resolvedSeller: String? = null
            var resolvedPrice: Double? = null
            if (outboundMatch?.bookingUrl != null) {
                linked++
                val seller = BookingLinks.sellerOf(outboundMatch.bookingUrl)
                val isAirlineSite = seller?.contains(card.airline, true) == true
                resolvedUrl = if (isAirlineSite) outboundMatch.bookingUrl else null
                resolvedSeller = if (isAirlineSite) seller else card.airline
                resolvedPrice = outboundMatch.price
            }

            val newReturns = card.returns.map { leg ->
                if (leg.bookingUrl != null) return@map leg
                if (resolvedUrl != null) {
                    leg.copy(
                        bookingUrl = resolvedUrl,
                        bookingSeller = resolvedSeller,
                        bookingPrice = resolvedPrice,
                    )
                } else {
                    leg
                }
            }
            val best = newReturns.minByOrNull { it.price }
            card.copy(
                returns = newReturns,
                bookingUrl = best?.bookingUrl ?: resolvedUrl,
                bookingSeller = best?.bookingSeller ?: resolvedSeller,
                bookingPrice = best?.bookingPrice ?: resolvedPrice,
            )
        }
        val withLinks = airlineCards.count { card -> card.returns.any { it.bookingUrl != null } }
        android.util.Log.i(
            TAG,
            "booking links attached: $linked returns on $withLinks of ${airlineCards.size} flights",
        )
        rebuildSections()
    }

    private fun fLegNumbers(leg: OctoTripApi.Leg?): List<String> {
        return leg?.legs?.mapNotNull { it.flightNumber } ?: emptyList()
    }
    private fun toCard(f: SerpApiClient.Flight): FlightCard {
        val dep = normalizeStamp(f.departureTime)
        val arr = normalizeStamp(f.arrivalTime)
        val retDep = normalizeStamp(f.returnDepartureTime)
        val retArr = normalizeStamp(f.returnArrivalTime)
        val code = f.flightNumber.filter { it.isLetter() }.uppercase()
        val name = f.airline.ifBlank { code }
        val outSegment = Segment(
            airline = name,
            airlineCode = code,
            flightNumber = f.flightNumber,
            origin = f.departureAirport,
            destination = f.arrivalAirport,
            departureTime = dep,
            arrivalTime = arr,
        )
        val backSegment = Segment(
            airline = name,
            airlineCode = code,
            flightNumber = f.returnFlightNumber,
            origin = f.arrivalAirport,
            destination = f.departureAirport,
            departureTime = retDep,
            arrivalTime = retArr,
        )
        val conditions = Conditions(
            sellerKind = "ota",
            bookingChannel = "ota",
            bookingProvider = "Google Flights",
        )
        val inbound = Inbound(
            origin = f.arrivalAirport,
            destination = f.departureAirport,
            departureTime = retDep,
            arrivalTime = retArr,
            stops = 0,
            airline = name,
            airlineCode = code,
            flightNumber = f.returnFlightNumber,
            segments = listOf(backSegment),
        )
        val offer = com.flightwatch.app.data.Offer(
            price = f.price,
            currency = f.currency,
            airline = name,
            airlineCode = code,
            origin = f.departureAirport,
            destination = f.arrivalAirport,
            departureTime = dep,
            arrivalTime = arr,
            stops = f.stops,
            flightNumber = f.flightNumber,
            segments = listOf(outSegment),
            inbound = inbound,
            conditions = conditions,
            source = "google_flights",
        )
        val leg = ReturnLeg(
            key = "$code|${f.returnFlightNumber}|$retDep",
            flightNumber = f.returnFlightNumber,
            airline = name,
            departureTime = retDep,
            arrivalTime = retArr,
            stops = 0,
            durationMinutes = 0,
            price = f.price,
            currency = f.currency,
        )
        return FlightCard(
            key = "GF|$code|${f.flightNumber}|$dep",
            airline = name,
            airlineCode = code,
            flightNumber = f.flightNumber,
            departureTime = dep,
            arrivalTime = arr,
            stops = f.stops,
            durationMinutes = 0,
            segments = listOf(outSegment),
            conditions = conditions,
            ancillaries = null,
            offer = offer,
            returns = listOf(leg),
            sourceLabel = "Google Flights",
        )
    }

    /** "2026-12-05 17:05" becomes "2026-12-05T17:05:00". */
    private fun normalizeStamp(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.replace(' ', 'T').let {
            if (it.length == 16) "$it:00" else it
        }
    }

    private fun describeAge(millis: Long): String {
        val minutes = millis / 60_000
        return when {
            minutes < 1 -> "moments"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60}h"
        }
    }

    /**
     * Split the flights into return-time sections.
     *
     * Every card is kept - a flight whose return misses the cutoff still
     * shows (its returns simply all appear under the section), so Saudia
     * and others never vanish from the page.
     */
    private fun buildSections(cards: List<FlightCard>): List<SectionUi> =
        FlightRules.RETURN_CUTOFFS.mapIndexed { index, cutoff ->
            SectionUi(
                index = index,
                cutoff = cutoff,
                flights = cards.sortedWith(
                    compareByDescending<FlightCard> { cheapestReturn(it, cutoff) != null }
                        .thenBy { cheapestReturn(it, cutoff)?.price ?: it.offer.price ?: Double.MAX_VALUE },
                ),
            )
        }

    /** The cheapest return leg for this flight that satisfies the cutoff. */
    fun cheapestReturn(card: FlightCard, cutoff: ReturnCutoff): ReturnLeg? =
        card.returns.filter { FlightRules.qualifies(it, cutoff) }.minByOrNull { it.price }

    /** The return leg currently shown on the card. */
    fun selectedReturn(sectionIndex: Int, card: FlightCard, cutoff: ReturnCutoff): ReturnLeg? {
        val key = selectionKey(sectionIndex, card.key)
        val picked = _selectedReturns.value[key]
        return card.returns.firstOrNull { it.key == picked }
            ?: cheapestReturn(card, cutoff)
    }

    fun selectReturn(sectionIndex: Int, card: FlightCard, returnLeg: ReturnLeg) {
        _selectedReturns.value = _selectedReturns.value +
            (selectionKey(sectionIndex, card.key) to returnLeg.key)
    }

    private fun selectionKey(sectionIndex: Int, flightKey: String) = "$sectionIndex|$flightKey"

    /** Record every observed price so the History page and notifications have data. */
    private fun saveHistory(cards: List<FlightCard>) {
        val s = app.settings
        val cutoff = FlightRules.RETURN_CUTOFFS.first()
        for (r in cards) {
            // One history series per complete ticket (outbound + a return that
            // qualifies for the page cutoff) - the price shown on the card.
            val qualifying = r.returns.filter { FlightRules.qualifies(it, cutoff) }
            for (ret in qualifying) {
                app.history.record(
                    flightKey = app.history.ticketKey(
                        origin = s.origin,
                        destination = s.destination,
                        outboundKey = r.key,
                        outboundArrival = r.arrivalTime,
                        returnFlightNumber = ret.flightNumber,
                        returnDeparture = ret.departureTime,
                        returnArrival = ret.arrivalTime,
                    ),
                    airline = r.airline,
                    flightNumber = r.flightNumber,
                    departureTime = r.departureTime,
                    arrivalTime = r.arrivalTime,
                    returnFlightNumber = ret.flightNumber,
                    returnDepartureTime = ret.departureTime,
                    returnArrivalTime = ret.arrivalTime,
                    origin = s.origin,
                    destination = s.destination,
                    price = ret.price,
                    currency = ret.currency,
                    isOfficial = r.offer.isOfficial,
                    seller = r.airline,
                )
            }
        }
        app.settings.rememberAirlines(cards.map { it.airline }.toSet())
        // Keep favourite prices in step with what the search just found -
        // no extra API calls, just a local price refresh.
        for (r in cards) {
            if (app.favorites.isFavorite(s.origin, s.destination, r.flightNumber)) {
                app.favorites.updatePrice(
                    origin = s.origin,
                    destination = s.destination,
                    flightNumber = r.flightNumber,
                    price = r.offer.price ?: 0.0,
                    currency = r.offer.currency ?: FlightRules.CURRENCY,
                )
            }
        }
        loadHistory()
    }

    fun loadHistory() {
        _history.value = app.history.all(400)
    }

    fun isFavorite(flightNumber: String): Boolean =
        app.favorites.isFavorite(settings.origin, settings.destination, flightNumber)

    /** Star / un-star a flight on the current airport page. */
    fun toggleFavorite(card: FlightCard, price: Double, currency: String) {
        app.favorites.toggle(
            com.flightwatch.app.data.FavoriteFlight(
                flightNumber = card.flightNumber,
                airline = card.airline,
                airlineCode = card.airlineCode,
                origin = settings.origin,
                destination = settings.destination,
                departureTime = card.departureTime,
                price = price,
                currency = currency,
            ),
        )
    }

    fun removeFavorite(origin: String, destination: String, flightNumber: String) {
        app.favorites.remove(origin, destination, flightNumber)
    }

    fun refreshConnectionState() {
        _state.value = _state.value.copy(connected = app.tokenStore.hasCredentials())
    }

    /** Check that the phone can reach the hosts the app needs. */
    fun testConnection(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = com.flightwatch.app.data.ConnectionCheck.run()
            val text = if (result.ok) {
                "All good. ${result.dnsNote}."
            } else {
                "Something is off. Names: ${result.dnsNote}. Connection: ${result.httpsNote}."
            }
            android.util.Log.i(TAG, "connection test: $text")
            onResult(text)
        }
    }

    /** Send a test notification so the traveller can verify alerts arrive. */
    fun sendTestNotification(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val manager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        "price_changes",
                        "Price changes",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "LetsFG airline fare changes" }
                    manager.createNotificationChannel(channel)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    onResult("Notification permission not granted.")
                    return@launch
                }
                val notification = androidx.core.app.NotificationCompat.Builder(app, "price_changes")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("FlightWatch test notification")
                    .setContentText("This is a test - price alerts are working.")
                    .setAutoCancel(true)
                    .build()
                androidx.core.app.NotificationManagerCompat.from(app).notify(1001, notification)
                android.util.Log.i(TAG, "test notification sent")
                onResult("Test notification sent.")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "test notification failed", e)
                onResult("Test failed: ${e.message ?: e}")
            }
        }
    }

    // ----------------------------------------------------------------- auth

    fun setAuthState(s: AuthState) {
        _authState.value = s
    }

    fun saveManualToken(token: String) {
        app.tokenStore.manualToken = token.trim().ifBlank { null }
        refreshConnectionState()
    }

    fun disconnect() {
        app.tokenStore.clear()
        refreshConnectionState()
    }

    companion object {
        private const val TAG = "FlightWatch"

        /** How long a saved result counts as current. */
        private const val FRESH_FOR_MS = 60L * 60L * 1000L

        /** Booking links are cached for 24 hours to avoid burning SerpApi quota. */
        private const val LINK_REFRESH_MS = 24L * 60L * 60L * 1000L
    }

    class Factory(private val app: FlightWatchApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
    }
}

sealed class AuthState {
    object Idle : AuthState()
    data class Working(val message: String) : AuthState()
    data class Ready(val authorizeUrl: String, val pending: Any) : AuthState()
    data class Connected(val message: String) : AuthState()
    data class Failed(val message: String) : AuthState()
}
