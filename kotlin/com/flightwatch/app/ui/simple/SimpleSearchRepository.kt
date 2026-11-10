package com.flightwatch.app.ui.simple

import com.flightwatch.app.FlightWatchApp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.flightwatch.app.data.BookingLink
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.data.IgnavApi
import com.flightwatch.app.data.IgnavMapper
import com.flightwatch.app.data.LetsfgApi
import com.flightwatch.app.data.SearchResponse

/**
 * Simple Mode search repository.
 * Searches flights using LetsFG or ignav.com based on user's API selection.
 * Returns FlightCard list for display in Simple Mode flight boxes.
 */
class SimpleSearchRepository(private val app: FlightWatchApp) {
    
    private fun filterOffers(offers: List<com.flightwatch.app.data.Offer>, filters: com.flightwatch.app.data.SearchFilters): List<com.flightwatch.app.data.Offer> {
        var result = offers
        
        if (filters.departureTimeFrom != null || filters.departureTimeTo != null) {
            result = result.filter { offer ->
                val depHour = try {
                    java.time.LocalTime.parse(offer.departureTime?.substringAfter('T')?.substringBefore('.')?.substringBefore('+')?.substringBefore('-')?.substringBefore('Z') ?: "00:00:00").hour
                } catch (e: Exception) { -1 }
                if (depHour < 0) return@filter false
                val afterFrom = filters.departureTimeFrom == null || depHour >= filters.departureTimeFrom
                val beforeTo = if (filters.departureTimeFrom != null && filters.departureTimeTo == null) depHour <= 23 else (filters.departureTimeTo == null || depHour <= filters.departureTimeTo)
                afterFrom && beforeTo
            }
        }
        
        if (filters.arrivalTimeFrom != null || filters.arrivalTimeTo != null) {
            result = result.filter { offer ->
                val arrHour = try {
                    java.time.LocalTime.parse(offer.arrivalTime?.substringAfter('T')?.substringBefore('.')?.substringBefore('+')?.substringBefore('-')?.substringBefore('Z') ?: "00:00:00").hour
                } catch (e: Exception) { -1 }
                if (arrHour < 0) return@filter false
                val afterFrom = filters.arrivalTimeFrom == null || arrHour >= filters.arrivalTimeFrom
                val beforeTo = if (filters.arrivalTimeFrom != null && filters.arrivalTimeTo == null) arrHour <= 23 else (filters.arrivalTimeTo == null || arrHour <= filters.arrivalTimeTo)
                afterFrom && beforeTo
            }
        }
        
        if (filters.maxStops != null) {
            result = result.filter { (it.stops ?: 0) <= filters.maxStops }
        }
        
        return result
    }

    private fun cacheKey(origin: String, destination: String, departDate: String, returnDate: String, adults: Int): String {
        return "simple-${app.settings.searchSource}-$origin-$destination-$departDate-$returnDate-$adults"
    }

    /**
     * Search flights for Simple Mode.
     * Uses the selected search API (LetsFG or ignav.com).
     *
     * @param origin Departure airport IATA code
     * @param destination Arrival airport IATA code
     * @param departDate Departure date (YYYY-MM-DD)
     * @param returnDate Return date (YYYY-MM-DD)
     * @param adults Number of adult passengers
     * @return List of FlightCard for display
     */
    suspend fun search(
        origin: String,
        destination: String,
        departDate: String,
        returnDate: String,
        adults: Int = 1,
        onProgress: (String) -> Unit = {},
        force: Boolean = false,
        filters: com.flightwatch.app.data.SearchFilters = com.flightwatch.app.data.SearchFilters(),
    ): List<FlightCard> {
        val s = app.settings
        val key = cacheKey(origin, destination, departDate, returnDate, adults)
        
        // Check cache first (on IO thread)
        val cached = if (force) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.searchCache.load(key) }
        if (cached != null) {
            val age = System.currentTimeMillis() - app.searchCache.savedAt(key)
            val maxAge = 60 * 60 * 1000L // 1 hour
            if (age < maxAge) {
                android.util.Log.i("FlightWatch", "SimpleSearch cache hit: ${cached.offers?.size ?: 0} offers")
                onProgress("Loading cached results...")
                val offers = filterOffers(cached.offers ?: emptyList(), filters)
                android.util.Log.i("FlightWatch", "SimpleSearch cache: ${offers.size} offers after filters")
                val flights = FlightRules.group(offers, s.selectedAirlines)
                return fetchBookingLinks(flights, s, onProgress, origin, destination, departDate, returnDate)
            }
        }
        
        android.util.Log.i("FlightWatch", "SimpleSearch: source=${s.searchSource} origin=$origin dest=$destination dep=$departDate ret=$returnDate")
        var lastError: Exception? = null
        var attempt = 0
        var response: SearchResponse? = null
        while (response == null && attempt < 3) {
            try {
                onProgress(if (s.searchSource == "ignav") "Searching ignav API (attempt ${attempt + 1})..." else "Searching LetsFG API (attempt ${attempt + 1})...")
                response = when (s.searchSource) {
                    "ignav" -> searchIgnavResponse(origin, destination, departDate, returnDate, adults, filters)
                    else -> searchLetsfgResponse(origin, destination, departDate, returnDate, adults, filters)
                }
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                val isPermanent = "429" in msg || "401" in msg || "402" in msg || "403" in msg || "credit" in msg.lowercase() || "limit" in msg.lowercase()
                if (isPermanent) {
                    android.util.Log.w("FlightWatch", "SimpleSearch permanent error (no retry): ${e.message}")
                    attempt = 3 // stop retrying
                } else {
                    attempt++
                    android.util.Log.w("FlightWatch", "SimpleSearch attempt $attempt failed: ${e.message}")
                    if (attempt < 3) kotlinx.coroutines.delay(1500L * attempt)
                }
            }
        }
        val finalResponse = response
        if (finalResponse == null) {
            val e = lastError ?: Exception("Search failed")
            run {
            android.util.Log.e("FlightWatch", "SimpleSearch API failed: ${e.message}")
            // For rate limit (429) or any error, use cached results
            val cachedFallback = app.searchCache.load(key)
            if (cachedFallback != null) {
                android.util.Log.i("FlightWatch", "SimpleSearch using cached results (API failed)")
                val offers = filterOffers(cachedFallback.offers ?: emptyList(), filters)
                val flights = FlightRules.group(offers, s.selectedAirlines)
                return fetchBookingLinks(flights, s, onProgress, origin, destination, departDate, returnDate)
            }
            throw e
            }
        }
        
        // Cache the response (on IO thread)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.searchCache.save(key, finalResponse) }
        
        val offers = finalResponse.offers ?: emptyList()
        android.util.Log.i("FlightWatch", "SimpleSearch result: ${offers.size} offers")
        onProgress("Grouping ${offers.size} offers...")
        val flights = FlightRules.group(offers, s.selectedAirlines)
        android.util.Log.i("FlightWatch", "SimpleSearch: ${flights.size} flights after grouping")
        
        return fetchBookingLinks(flights, s, onProgress, origin, destination, departDate, returnDate)
    }

    suspend fun fetchBookingLinksFor(flights: List<FlightCard>): List<FlightCard> {
        return fetchBookingLinks(flights, app.settings)
    }

    private suspend fun fetchBookingLinks(flights: List<FlightCard>, s: com.flightwatch.app.data.SettingsStore, onProgress: (String) -> Unit = {}, searchOrigin: String = s.origin, searchDest: String = s.destination, searchDepDate: String = s.outboundDate, searchRetDate: String = s.returnDate): List<FlightCard> {
        var resultFlights = flights
        val linksSource = s.linksSources.firstOrNull() ?: "serpapi"
        android.util.Log.i("FlightWatch", "fetchBookingLinks: source=$linksSource flights=${flights.size}")
        onProgress("Fetching booking links for ${flights.size} flights...")
        
        try {
            when (linksSource) {
                "combined" -> {
                    android.util.Log.i("FlightWatch", "fetchBookingLinks: using combined (ignav + serpapi)")
                    val ignavClient = app.ignavApi()
                    val serpClient = app.serpApi()

                    // Dedupe: fetch ignav links once per unique ignavId
                    val uniqueIds = flights.mapNotNull { it.ignavId ?: it.offer.ignavId }.distinct()
                    android.util.Log.i("FlightWatch", "Combined: ${flights.size} cards, ${uniqueIds.size} unique ignavIds")

                    val linksById = kotlinx.coroutines.coroutineScope {
                        if (ignavClient == null || uniqueIds.isEmpty()) {
                            emptyMap()
                        } else {
                            uniqueIds.map { id ->
                                async {
                                    var attempt = 0
                                    var fetched: List<BookingLink>? = null
                                    while (attempt < 3 && fetched == null) {
                                        try {
                                            fetched = ignavClient.fetchBookingLinks(id).bookingOptions?.flatMap { option ->
                                                (option.links ?: emptyList()).map { link ->
                                                    BookingLink(
                                                        providerName = link.providerName,
                                                        providerType = link.providerType,
                                                        url = link.url,
                                                        price = link.price?.amount,
                                                        currency = link.price?.currency,
                                                        isAirline = link.providerType == "airline",
                                                    )
                                                }
                                            } ?: emptyList()
                                        } catch (e: Exception) {
                                            attempt++
                                            android.util.Log.w("FlightWatch", "ignav $id attempt $attempt failed: ${e.message}")
                                            if (attempt < 3) kotlinx.coroutines.delay(1000L * attempt)
                                        }
                                    }
                                    id to (fetched ?: emptyList())
                                }
                            }.awaitAll().toMap()
                        }
                    }

                    resultFlights = flights.map { flight ->
                        val allLinks = mutableListOf<BookingLink>()
                        var airlineUrl: String? = null

                        // 1. ignav links (from deduped cache)
                        val ignavId = flight.ignavId ?: flight.offer.ignavId
                        if (ignavId != null) {
                            linksById[ignavId]?.forEach { allLinks.add(it) }
                            airlineUrl = allLinks.firstOrNull { it.isAirline }?.url
                        }

                        // 2. Google Flights link (local, no network)
                        if (serpClient != null) {
                            val gfUrl = serpClient.flightUrl(
                                origin = s.origin,
                                destination = s.destination,
                                departureDate = s.outboundDate,
                                returnDate = s.returnDate,
                                flightNumber = flight.flightNumber,
                            )
                            if (gfUrl != null) {
                                allLinks.add(BookingLink("Google Flights", "third_party", gfUrl, null, flight.offer.currency, isAirline = false))
                            }
                        }

                        if (allLinks.isNotEmpty()) {
                            val ret = flight.returns.firstOrNull()
                            if (ret != null) {
                                flight.copy(
                                    returns = listOf(ret.copy(bookingUrl = airlineUrl, bookingLinks = allLinks)),
                                    bookingUrl = airlineUrl,
                                    bookingLinks = allLinks,
                                )
                            } else {
                                flight.copy(bookingUrl = airlineUrl, bookingLinks = allLinks)
                            }
                        } else flight
                    }
                }
                "ignav" -> {
                    android.util.Log.i("FlightWatch", "fetchBookingLinks: using ignav")
                    val client = app.ignavApi()
                    android.util.Log.i("FlightWatch", "fetchBookingLinks: ignavApi=${client != null}")
                    if (client != null) {
                        // Dedupe: fetch once per unique ignavId
                        val uniqueIds = flights.mapNotNull { it.ignavId ?: it.offer.ignavId }.distinct()
                        android.util.Log.i("FlightWatch", "ignav: ${flights.size} cards, ${uniqueIds.size} unique ids")
                        val linksById = kotlinx.coroutines.coroutineScope {
                            uniqueIds.map { id ->
                                async {
                                    try {
                                        id to (client.fetchBookingLinks(id).bookingOptions?.flatMap { option ->
                                            (option.links ?: emptyList()).map { link ->
                                                BookingLink(
                                                    providerName = link.providerName,
                                                    providerType = link.providerType,
                                                    url = link.url,
                                                    price = link.price?.amount,
                                                    currency = link.price?.currency,
                                                    isAirline = link.providerType == "airline",
                                                )
                                            }
                                        } ?: emptyList())
                                    } catch (e: Exception) {
                                        android.util.Log.w("FlightWatch", "ignav $id failed: ${e.message}")
                                        id to emptyList<BookingLink>()
                                    }
                                }
                            }.awaitAll().toMap()
                        }
                        resultFlights = flights.map { flight ->
                            val ignavId = flight.ignavId ?: flight.offer.ignavId ?: return@map flight
                            try {
                                val allLinks = linksById[ignavId] ?: emptyList()
                                if (allLinks.isNotEmpty()) {
                                    val airlineLink = allLinks.firstOrNull { it.isAirline } ?: allLinks.firstOrNull()
                                    val ret = flight.returns.firstOrNull()
                                    if (ret != null) {
                                        flight.copy(
                                            returns = listOf(ret.copy(bookingUrl = airlineLink?.url, bookingLinks = allLinks)),
                                            bookingUrl = airlineLink?.url,
                                            bookingLinks = allLinks,
                                        )
                                    } else {
                                        flight.copy(bookingUrl = airlineLink?.url, bookingLinks = allLinks)
                                    }
                                } else flight
                            } catch (e: Exception) {
                                flight
                            }
                        }
                    }
                }
                "serpapi" -> {
                    android.util.Log.i("FlightWatch", "fetchBookingLinks: using serpapi")
                    val client = app.serpApi()
                    if (client != null) {
                        try {
                            // SerpApi sweep returns flights with departure_token (round-trip)
                            val sweepResults = client.sweep(
                                origin = searchOrigin,
                                destination = searchDest,
                                departureDate = searchDepDate,
                                returnDate = searchRetDate,
                                adults = s.adults,
                            )
                            android.util.Log.i("FlightWatch", "SerpApi sweep: ${sweepResults.size} results")
                            resultFlights = flights.map { flight ->
                                val match = sweepResults.firstOrNull { sweep ->
                                    sweep.flightNumber.replace(" ", "").equals(flight.flightNumber.replace(" ", ""), ignoreCase = true)
                                }
                                if (match != null) {
                                    try {
                                        // Try booking_token first (one-way), then google_flights_url
                                        val url = if (match.bookingToken.isNotBlank()) {
                                            client.bookingUrl(match.bookingToken)
                                        } else {
                                            // Round-trip: use Google Flights search URL
                                            client.flightUrl(
                                                origin = s.origin,
                                                destination = s.destination,
                                                departureDate = s.outboundDate,
                                                returnDate = s.returnDate,
                                                flightNumber = flight.flightNumber,
                                            )
                                        }
                                        if (url != null) {
                                            val link = BookingLink("SerpApi - Google Flights", "third_party", url, flight.offer.price, flight.offer.currency, isAirline = false)
                                            val ret = flight.returns.firstOrNull()
                                            if (ret != null) {
                                                flight.copy(
                                                    returns = listOf(ret.copy(bookingUrl = url, bookingLinks = listOf(link))),
                                                    bookingUrl = url,
                                                    bookingLinks = listOf(link),
                                                )
                                            } else {
                                                flight.copy(bookingUrl = url, bookingLinks = listOf(link))
                                            }
                                        } else flight
                                    } catch (e: Exception) {
                                        flight
                                    }
                                } else flight
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("FlightWatch", "SerpApi sweep failed: ${e.message}")
                        }
                    }
                }
                "octotrip" -> {
                    android.util.Log.i("FlightWatch", "fetchBookingLinks: using octotrip")
                    resultFlights = flights.map { flight ->
                        val ret = flight.returns.firstOrNull()
                        val url = ret?.bookingUrl ?: flight.bookingUrl
                        if (url != null) {
                            val link = BookingLink(flight.airline, "airline", url, flight.offer.price, flight.offer.currency, isAirline = true)
                            if (ret != null) {
                                flight.copy(
                                    returns = listOf(ret.copy(bookingUrl = url, bookingLinks = listOf(link))),
                                    bookingUrl = url,
                                    bookingLinks = listOf(link),
                                )
                            } else {
                                flight.copy(bookingUrl = url, bookingLinks = listOf(link))
                            }
                        } else flight
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FlightWatch", "Booking links fetch failed: ${e.message}")
        }
        
        android.util.Log.i("FlightWatch", "fetchBookingLinks: done, ${resultFlights.count { !it.bookingLinks.isNullOrEmpty() }} flights with links")
        return resultFlights
    }

    /**
     * Search using LetsFG API (airline-only search).
     * Returns tickets matching the user's rules.
     */
    /**
     * Refresh: search BOTH APIs (LetsFG + ignav), merge, deduplicate.
     * Used by refresh button and pull-to-refresh.
     */
    suspend fun refreshAll(
        origin: String,
        destination: String,
        departDate: String,
        returnDate: String,
        adults: Int = 1,
        oldFlights: List<FlightCard> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): List<FlightCard> {
        val s = app.settings
        val allOffers = mutableListOf<com.flightwatch.app.data.Offer>()

        // Build old price map for comparison
        val oldPrices = oldFlights.associate { it.key to (it.currentPrice ?: it.returns.firstOrNull()?.price ?: it.offer.price ?: 0.0) }

        // 1. LetsFG
        onProgress("Searching LetsFG API...")
        try {
            val resp = searchLetsfgResponse(origin, destination, departDate, returnDate, adults)
            val offers = resp.offers ?: emptyList()
            allOffers.addAll(offers)
            onProgress("LetsFG: ${offers.size} offers")
        } catch (e: Exception) {
            onProgress("LetsFG failed: ${e.message}")
            android.util.Log.w("FlightWatch", "refreshAll letsfg failed: ${e.message}")
        }

        // 2. ignav (if key exists)
        if (app.ignavApi() != null) {
            onProgress("Searching ignav API...")
            try {
                val resp = searchIgnavResponse(origin, destination, departDate, returnDate, adults)
                val offers = resp.offers ?: emptyList()
                allOffers.addAll(offers)
                onProgress("ignav: ${offers.size} offers")
            } catch (e: Exception) {
                onProgress("ignav failed: ${e.message}")
                android.util.Log.w("FlightWatch", "refreshAll ignav failed: ${e.message}")
            }
        }

        // 3. Deduplicate: keep best price per outbound+return pairing
        onProgress("Deduplicating ${allOffers.size} offers...")
        val deduped = allOffers
            .groupBy { "${it.flightKey}|${it.inbound?.flightNumber ?: "-"}|${it.inbound?.departureTime ?: "-"}" }
            .map { (_, group) -> group.minByOrNull { it.price ?: Double.MAX_VALUE }!! }

        // 4. Group into cards
        onProgress("Grouping into flights...")
        val flights = FlightRules.group(deduped, s.selectedAirlines)

        // 5. Compare prices and report changes
        val priceChanges = mutableListOf<String>()
        for (flight in flights) {
            val newPrice = flight.returns.firstOrNull()?.price ?: flight.offer.price ?: continue
            val oldPrice = oldPrices[flight.key]
            if (oldPrice != null && oldPrice > 0 && newPrice > 0 && oldPrice != newPrice) {
                val diff = newPrice - oldPrice
                val arrow = if (diff < 0) "↓" else "↑"
                priceChanges.add("${flight.flightNumber}: ${oldPrice.toInt()}→${newPrice.toInt()} SAR $arrow")
            }
        }
        if (priceChanges.isNotEmpty()) {
            onProgress("Price changes: ${priceChanges.joinToString(", ")}")
        }

        // 6. Fetch booking links ONLY for user's selected flights (not all search results)
        val oldKeys = oldFlights.map { it.key }.toSet()
        val toLink = if (oldKeys.isNotEmpty()) flights.filter { it.key in oldKeys } else flights
        onProgress("Fetching booking links for ${toLink.size} selected flights...")
        val linked = fetchBookingLinks(toLink, s, onProgress, origin, destination, departDate, returnDate)
        val linkedByKey = linked.associateBy { it.key }

        // 7. Save to cache
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.searchCache.save(cacheKey(origin, destination, departDate, returnDate, adults),
                SearchResponse(offers = deduped))
        }

        // 8. Return flights with links only for selected, others without links
        val withLinks = flights.map { linkedByKey[it.key] ?: it }

        onProgress(if (priceChanges.isNotEmpty()) "Done: ${oldKeys.size} selected, ${priceChanges.size} price updates" else "Done: ${oldKeys.size} selected flights")
        android.util.Log.i("FlightWatch", "refreshAll: ${allOffers.size} raw -> ${deduped.size} deduped -> ${oldKeys.size} selected -> ${linked.size} linked")
        return withLinks
    }

    private suspend fun searchLetsfgResponse(
        origin: String,
        destination: String,
        departDate: String,
        returnDate: String,
        adults: Int,
        filters: com.flightwatch.app.data.SearchFilters = com.flightwatch.app.data.SearchFilters(),
    ): SearchResponse {
        val response = app.api.search(
            LetsfgApi.SearchParams(
                origin = origin,
                destination = destination,
                dateFrom = departDate,
                returnDate = returnDate,
                adults = adults,
                children = 0,
                currency = FlightRules.CURRENCY,
                cabin = "M",
                airlineOnly = true,
                departureTimeFrom = filters.departureTimeFrom?.let { String.format("%02d:00", it) },
                departureTimeTo = if (filters.departureTimeFrom != null && filters.departureTimeTo == null) "23:59" else filters.departureTimeTo?.let { String.format("%02d:00", it + 1) },
            ),
        )
        android.util.Log.i("FlightWatch", "SimpleSearch LetsFG: offers=${response.offers?.size ?: 0}")
        val filtered = filterOffers(response.offers ?: emptyList(), filters)
        android.util.Log.i("FlightWatch", "SimpleSearch LetsFG: ${filtered.size} offers after filters")
        return response.copy(offers = filtered)
    }

    /**
     * Search using ignav.com API (round-trip fares).
     * Returns tickets with exact-flight booking links via ignav_id.
     */
    private suspend fun searchIgnavResponse(
        origin: String,
        destination: String,
        departDate: String,
        returnDate: String,
        adults: Int,
        filters: com.flightwatch.app.data.SearchFilters = com.flightwatch.app.data.SearchFilters(),
    ): SearchResponse {
        val client = app.ignavApi()
            ?: throw IllegalStateException("Add your ignav.com API key in Settings to search with ignav.com.")
        val response = client.searchRoundTrip(
            origin = origin,
            destination = destination,
            departureDate = departDate,
            returnDate = returnDate,
            adults = adults,
            market = "SA", // SAR currency
            maxStops = filters.maxStops,
            departureTimeRange = if (filters.departureTimeFrom != null || filters.arrivalTimeFrom != null) {
                IgnavApi.TimeRange(
                    earliest_hour = filters.departureTimeFrom,
                    latest_hour = if (filters.departureTimeFrom != null && filters.departureTimeTo == null) 23 else filters.departureTimeTo,
                    arrival_earliest_hour = filters.arrivalTimeFrom,
                    arrival_latest_hour = if (filters.arrivalTimeFrom != null && filters.arrivalTimeTo == null) 23 else filters.arrivalTimeTo,
                )
            } else null,
        )
        val offers = filterOffers(IgnavMapper.mapToOffers(response), filters)
        android.util.Log.i("FlightWatch", "SimpleSearch ignav: ${offers.size} offers after filters")
        return SearchResponse(offers = offers, totalResults = offers.size)
    }
}
