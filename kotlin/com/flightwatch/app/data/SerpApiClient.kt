package com.flightwatch.app.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * SerpApi's Google Flights engine - an optional third source.
 *
 * It is only used once the traveller adds their own key in Settings. It earns
 * its place because it covers many more airlines than the airline-only search
 * and because a booking token resolves to the real booking page of one exact
 * flight.
 *
 * SerpApi counts searches against a monthly quota, but a repeated identical
 * query is served from their cache for free, and the cached answer stays valid
 * for an hour.
 */
class SerpApiClient(private val apiKey: String, private val usage: ApiUsageStore? = null) {

    private val gson = Gson()

    data class Airport(
        @SerializedName("id") val id: String? = null,
        @SerializedName("time") val time: String? = null,
    )

    data class Segment(
        @SerializedName("departure_airport") val departureAirport: Airport? = null,
        @SerializedName("arrival_airport") val arrivalAirport: Airport? = null,
        @SerializedName("airline") val airline: String? = null,
        @SerializedName("flight_number") val flightNumber: String? = null,
        @SerializedName("duration") val duration: Int? = null,
        @SerializedName("airplane") val airplane: String? = null,
    )

    data class Itinerary(
        @SerializedName("flights") val flights: List<Segment>? = null,
        @SerializedName("price") val price: Double? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("booking_token") val bookingToken: String? = null,
        @SerializedName("total_duration") val totalDuration: Int? = null,
        @SerializedName("layovers") val layovers: List<JsonObject>? = null,
    )

    data class PriceInsights(
        @SerializedName("lowest_price") val lowestPrice: Double? = null,
        @SerializedName("price_level") val priceLevel: String? = null,
        @SerializedName("typical_price_range") val typicalPriceRange: List<Double>? = null,
        @SerializedName("price_history") val priceHistory: List<List<Double>>? = null,
    )

    data class Result(
        @SerializedName("best_flights") val bestFlights: List<Itinerary>? = null,
        @SerializedName("other_flights") val otherFlights: List<Itinerary>? = null,
        @SerializedName("price_insights") val priceInsights: PriceInsights? = null,
        @SerializedName("booking_options") val bookingOptions: List<BookingOption>? = null,
    )

    data class BookingOption(
        val together: Together? = null,
    )

    data class Together(
        @SerializedName("book_with") val bookWith: String? = null,
        val airline: Boolean? = null,
        val price: Double? = null,
        val currency: String? = null,
        @SerializedName("booking_request") val bookingRequest: BookingRequest? = null,
    )

    data class BookingRequest(
        val url: String? = null,
        @SerializedName("post_data") val postData: String? = null,
    )

    data class Flight(
        val airline: String,
        val flightNumber: String,
        val departureTime: String,
        val arrivalTime: String,
        val departureAirport: String,
        val arrivalAirport: String,
        val returnFlightNumber: String,
        val returnDepartureTime: String,
        val returnArrivalTime: String,
        val stops: Int,
        val price: Double,
        val currency: String,
        val bookingToken: String,
        var bookingUrl: String? = null,
        val bookingOptions: List<BookingOption>? = null,
    )

    /**
     * Search the route for the dates, keeping only the flights that leave in
     * the evening window the traveller asked for.
     */
    suspend fun search(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        adults: Int = 1,
        earliestDepartureHour: Int = 16,
        latestDepartureHour: Int = 23,
    ): Result = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "engine" to "google_flights",
            "api_key" to apiKey,
            "type" to if (returnDate.isNullOrBlank()) "2" else "1",
            "departure_id" to origin,
            "arrival_id" to destination,
            "outbound_date" to departureDate,
            "adults" to adults.toString(),
            "currency" to FlightRules.CURRENCY,
            "gl" to "sa",
            "hl" to "en",
            "stops" to "1",
            "sort_by" to "2",
            "show_hidden" to "true",
            "outbound_times" to "$earliestDepartureHour,$latestDepartureHour",
            // Local Saudi market params
            "timezone" to "Asia/Riyadh",
            "country" to "SA",
            "location" to "Saudi Arabia",
        )
        if (!returnDate.isNullOrBlank()) params["return_date"] = returnDate
        val json = get(params) ?: throw SerpApiException("SerpApi returned nothing")
        gson.fromJson(json, Result::class.java)
    }

    /**
     * A Google Flights link that opens this one flight.
     *
     * Used when there is no booking token to resolve - the flight number in
     * the link is what pins the result.
     */
    fun flightUrl(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        flightNumber: String?,
    ): String {
        val query = buildString {
            append("Flights from ").append(origin).append(" to ").append(destination)
            append(" on ").append(departureDate)
            if (!returnDate.isNullOrBlank()) append(" through ").append(returnDate)
        }
        val url = StringBuilder("https://www.google.com/travel/flights?q=")
        url.append(URLEncoder.encode(query, "UTF-8"))
        url.append("&curr=").append(FlightRules.CURRENCY)
        if (!flightNumber.isNullOrBlank()) {
            url.append("&f=0:").append(URLEncoder.encode(flightNumber.replace(" ", ""), "UTF-8"))
        }
        return url.toString()
    }

    /**
     * Fetch booking_options (3rd party providers + prices) using selected_flights_json.
     * Call with the EXACT flight numbers from the app's FlightCard.
     */
    suspend fun bookingOptions(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        outFlightNumber: String,
        returnFlightNumber: String?,
    ): List<BookingOption> = withContext(Dispatchers.IO) {
        val outNum = outFlightNumber.replace(" ", "").trim()
        val retNum = returnFlightNumber?.replace(" ", "")?.trim().orEmpty()
        val selectedJson = buildString {
            append("{\"outbound\":[{\"flight_number\":\"$outNum\",\"departure_id\":\"$origin\",\"arrival_id\":\"$destination\",\"date\":\"$departureDate\"}]")
            if (retNum.isNotBlank() && !returnDate.isNullOrBlank()) {
                append(",\"return\":[{\"flight_number\":\"$retNum\",\"departure_id\":\"$destination\",\"arrival_id\":\"$origin\",\"date\":\"$returnDate\"}]")
            }
            append("}")
        }
        android.util.Log.i("FlightWatch", "SerpApi bookingOptions: outNum=$outNum retNum=$retNum")
        val params = linkedMapOf(
            "engine" to "google_flights",
            "api_key" to apiKey,
            "selected_flights_json" to selectedJson,
            "curr" to FlightRules.CURRENCY,
        )
        val json = get(params)
        val result = json?.let { gson.fromJson(it, Result::class.java) }
        val options = result?.bookingOptions ?: emptyList()
        android.util.Log.i("FlightWatch", "SerpApi bookingOptions: got ${options.size}")
        options
    }

    /** Flatten the itineraries into one row per flight, keeping the return with it. */
    fun flatten(result: Result, origin: String, destination: String): List<Flight> {
        val all = (result.bestFlights.orEmpty() + result.otherFlights.orEmpty())
        val out = mutableListOf<Flight>()
        android.util.Log.i("FlightWatch", "flatten: ${all.size} itineraries")
        for (it in all) {
            val segs = it.flights.orEmpty()
            if (segs.isEmpty()) continue
            val outboundEnd = segs.indexOfFirst { s -> s.arrivalAirport?.id == destination }
            val outSegs = if (outboundEnd >= 0) segs.subList(0, outboundEnd + 1) else listOf(segs.first())
            val backSegs = if (outboundEnd >= 0 && segs.size > outboundEnd + 1) {
                segs.subList(outboundEnd + 1, segs.size)
            } else {
                emptyList()
            }
            android.util.Log.i("FlightWatch", "flatten: segs=${segs.size} outboundEnd=$outboundEnd outSegs=${outSegs.size} backSegs=${backSegs.size}")
            val first = outSegs.first()
            val lastOut = outSegs.last()
            val firstBack = backSegs.firstOrNull()
            val lastBack = backSegs.lastOrNull()
            out += Flight(
                airline = first.airline.orEmpty(),
                flightNumber = first.flightNumber.orEmpty(),
                departureTime = first.departureAirport?.time.orEmpty(),
                arrivalTime = lastOut.arrivalAirport?.time.orEmpty(),
                departureAirport = first.departureAirport?.id.orEmpty(),
                arrivalAirport = lastOut.arrivalAirport?.id.orEmpty(),
                returnFlightNumber = firstBack?.flightNumber.orEmpty(),
                returnDepartureTime = firstBack?.departureAirport?.time.orEmpty(),
                returnArrivalTime = lastBack?.arrivalAirport?.time.orEmpty(),
                stops = (outSegs.size - 1).coerceAtLeast(0),
                price = it.price ?: 0.0,
                currency = FlightRules.CURRENCY,
                bookingToken = it.bookingToken.orEmpty(),
                bookingOptions = result.bookingOptions,
            )
        }
        return out
    }

    /**
     * Look up booking tokens for the trip.
     *
     * One round-trip search when a return date is given (1 SerpApi request,
     * and the itinerary carries both flight numbers so matching works).
     * One one-way search otherwise.
     *
     * This function ONLY searches - it does NOT resolve booking URLs, so it
     * costs at most 1 SerpApi request. Call [bookingUrl] only for flights the
     * user actually sees, to save quota.
     */
    suspend fun sweep(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        adults: Int = 1,
    ): List<Flight> = withContext(Dispatchers.IO) {
        val result = if (!returnDate.isNullOrBlank()) {
            search(origin, destination, departureDate, returnDate, adults)
        } else {
            search(origin, destination, departureDate, null, adults)
        }
        flatten(result, origin, destination)
    }

    /**
     * Resolve one booking token to a seller link.
     *
     * Costs 1 SerpApi request per call. Only call this for flights the user
     * actually sees - SerpApi free tier is 250 requests/month.
     *
     * Results are cached per booking token so the same token is never
     * resolved twice.
     */
    private val resolvedUrls = java.util.Collections.synchronizedMap(mutableMapOf<String, String?>())

    suspend fun bookingUrl(bookingToken: String): String? = withContext(Dispatchers.IO) {
        if (bookingToken.isEmpty()) return@withContext null
        resolvedUrls[bookingToken] ?: run {
            val url = findBookingUrl(bookingToken)
            resolvedUrls[bookingToken] = url
            url
        }
    }

    private suspend fun findBookingUrl(bookingToken: String): String? = withContext(Dispatchers.IO) {
        val json = get(
            linkedMapOf(
                "engine" to "google_flights",
                "api_key" to apiKey,
                "booking_token" to bookingToken,
            ),
        )
        json?.let { findFirstUrl(it) }
    }

    private fun get(params: Map<String, String>): JsonElement? {
        usage?.bump(ApiUsageStore.SERPAPI)
        val query = params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        var conn: HttpURLConnection? = null
        try {
            conn = URL("$BASE_URL?$query").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw SerpApiException("SerpApi HTTP $code: ${text.take(200)}")
            }
            val element = try {
                gson.fromJson(text, JsonElement::class.java)
            } catch (e: Exception) {
                throw SerpApiException("SerpApi sent something that is not JSON: ${text.take(120)}")
            }
            val error = element.asJsonObject?.get("error")
            if (error != null && !error.isJsonNull) {
                throw SerpApiException("SerpApi: ${error.asString}")
            }
            return element
        } catch (e: SerpApiException) {
            throw e
        } catch (e: Exception) {
            throw SerpApiException(e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    /** Plan info from SerpApi's free Account API (never metered). */
    data class AccountInfo(
        val searchesPerMonth: Int? = null,
        val planSearchesLeft: Int? = null,
        val totalSearchesLeft: Int? = null,
        val thisMonthUsage: Int? = null,
        val planName: String? = null,
    )

    /**
     * Read the account quota. SerpApi's Account API is free of charge and is
     * not counted toward the monthly search quota, so a refresh is safe.
     */
    suspend fun account(): AccountInfo = withContext(Dispatchers.IO) {
        val json = getAccount() ?: throw SerpApiException("SerpApi account returned nothing")
        val obj = json.asJsonObject
        fun int(name: String): Int? =
            obj.get(name)?.takeIf { !it.isJsonNull }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt
        AccountInfo(
            searchesPerMonth = int("searches_per_month"),
            planSearchesLeft = int("plan_searches_left"),
            totalSearchesLeft = int("total_searches_left"),
            thisMonthUsage = int("this_month_usage"),
            planName = obj.get("plan_name")?.takeIf { !it.isJsonNull }?.asString,
        )
    }

    /** Account API does not bump the search counter. */
    private fun getAccount(): JsonElement? {
        val query = "api_key=${enc(apiKey)}"
        var conn: HttpURLConnection? = null
        try {
            conn = URL("https://serpapi.com/account?$query").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw SerpApiException("SerpApi account HTTP $code")
            }
            return try {
                gson.fromJson(text, JsonElement::class.java)
            } catch (e: Exception) {
                throw SerpApiException("SerpApi account is not JSON")
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun findFirstUrl(element: JsonElement): String? {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                for ((key, value) in obj.entrySet()) {
                    if (key == "url" && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        val s = value.asString
                        if (s.startsWith("http")) return s
                    }
                }
                for ((_, value) in obj.entrySet()) {
                    findFirstUrl(value)?.let { return it }
                }
            }
            element.isJsonArray -> {
                for (item in element.asJsonArray) {
                    findFirstUrl(item)?.let { return it }
                }
            }
        }
        return null
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val BASE_URL = "https://serpapi.com/search"
    }
}

class SerpApiException(message: String) : Exception(message)
