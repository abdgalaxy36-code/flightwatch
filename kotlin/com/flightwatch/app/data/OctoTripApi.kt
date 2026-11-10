package com.flightwatch.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OctoTrip Flights - a second flight source that hands out a booking link for
 * every offer it returns.
 *
 * It is a public MCP server over Streamable HTTP, so no key and no account are
 * needed. Two things about it are worth knowing:
 *
 *  - it rejects clients whose User-Agent looks like a script ("ua_blocked"), so
 *    the requests go out wearing a normal browser agent;
 *  - one search returns only the six cheapest options for that exact query, so
 *    the app sweeps several query shapes and merges the answers.
 *
 * The links are per flight: the redirect carries the flight numbers and dates
 * of the offer it belongs to, not just the route.
 */
class OctoTripApi(private val usage: ApiUsageStore? = null) {

    private val gson = Gson()

    /** The session id the server hands out during the handshake. */
    private var sessionId: String? = null


    data class Segment(
        @SerializedName("flight_number") val flightNumber: String? = null,
        @SerializedName("carrier") val carrier: String? = null,
        @SerializedName("departure") val departure: String? = null,
        @SerializedName("arrival") val arrival: String? = null,
        @SerializedName("departure_time") val departureTime: String? = null,
        @SerializedName("arrival_time") val arrivalTime: String? = null,
        @SerializedName("aircraft") val aircraft: String? = null,
    )

    data class Leg(
        @SerializedName("departure") val departure: String? = null,
        @SerializedName("arrival") val arrival: String? = null,
        @SerializedName("departure_time") val departureTime: String? = null,
        @SerializedName("arrival_time") val arrivalTime: String? = null,
        @SerializedName("departure_date") val departureDate: String? = null,
        @SerializedName("arrival_date") val arrivalDate: String? = null,
        @SerializedName("duration_minutes") val durationMinutes: Int? = null,
        @SerializedName("stops") val stops: Int? = null,
        @SerializedName("legs") val legs: List<Segment>? = null,
    )

    data class Flight(
        @SerializedName("airline") val airline: String? = null,
        @SerializedName("airline_code") val airlineCode: String? = null,
        @SerializedName("flight_numbers") val flightNumbers: List<String>? = null,
        @SerializedName("is_direct") val isDirect: Boolean? = null,
        @SerializedName("stops") val stops: Int? = null,
        @SerializedName("total_duration_minutes") val totalDurationMinutes: Int? = null,
        @SerializedName("outbound") val outbound: Leg? = null,
        @SerializedName("return") val inbound: Leg? = null,
        @SerializedName("price") val price: Double? = null,
        @SerializedName("currency") val currency: String? = null,
        @SerializedName("baggage") val baggage: String? = null,
        @SerializedName("gate") val gate: String? = null,
        @SerializedName("booking_url") val bookingUrl: String? = null,
        @SerializedName("link_type") val linkType: String? = null,
        @SerializedName("affiliate_disclosure") val affiliateDisclosure: String? = null,
    )

    data class Payload(
        @SerializedName("results") val results: List<Flight>? = null,
        @SerializedName("total") val total: Int? = null,
        @SerializedName("total_available") val totalAvailable: Int? = null,
        @SerializedName("validity") val validity: String? = null,
    )

    /**
     * The query shapes worth asking for.
     *
     * Both Istanbul airports are tried because the airline split between them
     * changes which offers make the six-cheapest cut. The second cabin is a
     * discovery trick: pricing the same flights at a different fare class puts
     * a different handful of flights in the answer - that is how the Saudia
     * evening flight was found.
     *
     * Only round trips are asked for, because a flight without a return cannot
     * be paired with one and the app shows the two together.
     */
    /**
     * The lookups to make.
     *
     * The server answers with the six cheapest offers it finds and gives a
     * different set each time, so the same question has to be asked more than
     * once to see everything. The premium and business lists are where the
     * direct evening flights show up, and the economy list brings the budget
     * airlines in, so the premium ones are asked first and then again at the
     * end.
     */
    private fun shapes(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        adults: Int,
    ): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        val airports = linkedSetOf(destination) + if (destination == "IST") setOf("SAW") else emptySet()
        fun shape(dest: String, cabin: String): Map<String, Any> {
            val shape = linkedMapOf(
                "origin" to origin,
                "destination" to dest,
                "departure_date" to departureDate,
                "adults" to adults,
                "currency" to FlightRules.CURRENCY,
                "trip_class" to cabin,
                "locale" to "en",
            )
            if (!returnDate.isNullOrBlank()) shape["return_date"] = returnDate
            return shape
        }
        for (dest in airports) {
            for (cabin in listOf("W", "C")) out += shape(dest, cabin)
        }
        for (dest in airports) {
            for (cabin in listOf("Y")) out += shape(dest, cabin)
        }
        for (dest in airports) {
            for (cabin in listOf("W", "C")) out += shape(dest, cabin)
        }
        return out
    }

    /**
     * Run the sweep and return every offer that was found, cheapest first.
     *
     * Offers that show the same flight twice are kept once, and the cheapest
     * price for each flight wins.
     */
    suspend fun sweep(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        adults: Int = 1,
        onProgress: (String) -> Unit = {},
        onFound: suspend (List<Flight>) -> Unit = {},
    ): List<Flight> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Flight>()
        val shapes = shapes(origin, destination, departureDate, returnDate, adults)
        initialize()
        delay(SETTLE_MS)
        shapes.forEachIndexed { index, args ->
            onProgress("Looking up booking links (${index + 1}/${shapes.size})…")
            val payload = try {
                callWithRetry(args)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "lookup ${args["destination"]} ${args["trip_class"]} failed", e)
                null
            }
            for (f in payload?.results.orEmpty()) {
                val key = identity(f)
                val existing = found[key]
                if (existing == null || (f.price ?: Double.MAX_VALUE) < (existing.price ?: Double.MAX_VALUE)) {
                    found[key] = f
                }
            }
            android.util.Log.i(TAG, "lookup ${index + 1}/${shapes.size} -> ${payload?.results?.size ?: 0} offers")
            onFound(found.values.sortedBy { it.price ?: Double.MAX_VALUE })
            if (index < shapes.size - 1) delay(REQUEST_GAP_MS)
        }
        found.values.sortedBy { it.price ?: Double.MAX_VALUE }
    }

    /**
     * One lookup, with a second try when the network gives up.
     *
     * The server allows one request a second with a small burst. Asking for
     * more than that does not get a polite refusal - the name stops resolving
     * for a while - so the wait between calls matters more than it looks.
     */
    private suspend fun callWithRetry(args: Map<String, Any>): Payload? {
        return try {
            call(args)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "lookup failed, waiting before trying again: ${e.message}")
            delay(RETRY_WAIT_MS)
            try {
                call(args)
            } catch (e2: Exception) {
                android.util.Log.w(TAG, "lookup failed again: ${e2.message}")
                null
            }
        }
    }

    /**
     * The MCP handshake.
     *
     * The server hands out a session id here and expects it on every later
     * call, and without this first step the search calls never come back.
     */
    private fun initialize() {
        val resolved = try {
            java.net.InetAddress.getAllByName("mcp.octotrip.app").first().hostAddress
        } catch (e: Exception) {
            "failed: ${e.message}"
        }
        android.util.Log.i(TAG, "dns mcp.octotrip.app -> $resolved")
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 0)
            addProperty("method", "initialize")
            add(
                "params",
                JsonObject().apply {
                    addProperty("protocolVersion", "2025-03-26")
                    add("capabilities", JsonObject())
                    add(
                        "clientInfo",
                        JsonObject().apply {
                            addProperty("name", "FlightWatch")
                            addProperty("version", "1.0")
                        },
                    )
                },
            )
        }
        try {
            val response = post(body.toString())
            val server = response?.getAsJsonObject("result")?.get("serverInfo")
            android.util.Log.i(TAG, "mcp handshake done: $server")
            notifyInitialized()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "mcp handshake failed", e)
        }
    }

    /** The server wants to be told that the session is ready. */
    private fun notifyInitialized() {
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "notifications/initialized")
        }
        try {
            post(body.toString())
        } catch (e: Exception) {
        }
    }

    /** A stable key for one physical itinerary. */
    fun identity(f: Flight): String {
        val out = f.outbound?.legs?.joinToString("-") { it.flightNumber.orEmpty() }
            ?: f.flightNumbers?.firstOrNull().orEmpty()
        val back = f.inbound?.legs?.joinToString("-") { it.flightNumber.orEmpty() }.orEmpty()
        return "$out|$back|${f.outbound?.departureTime.orEmpty()}"
    }

    /** The flight numbers on one leg, cleaned up for comparison. */
    /**
     * Find out which site a booking link ends up at.
     *
     * The redirect is not followed - only its Location header is read - so the
     * seller's own bot protection never comes into it.
     */
    suspend fun resolveSeller(url: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            val code = conn.responseCode
            if (code !in 300..399) return@withContext null
            val location = conn.getHeaderField("Location") ?: return@withContext null
            val host = location.substringAfter("://", "").substringBefore('/').lowercase()
            BookingLinks.sellerNameForHost(host)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun call(args: Map<String, Any>): Payload? {
        usage?.bump(ApiUsageStore.OCTOTRIP)
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "tools/call")
            add(
                "params",
                JsonObject().apply {
                    addProperty("name", "search")
                    add("arguments", gson.toJsonTree(args))
                },
            )
        }
        val envelope = post(body.toString()) ?: return null
        val error = envelope.get("error")
        if (error != null && !error.isJsonNull) {
            val message = error.asJsonObject.get("message")?.asString ?: "unknown error"
            throw OctoTripException(message)
        }
        val result = envelope.getAsJsonObject("result") ?: return null
        val content = result.getAsJsonArray("content") ?: return null
        if (content.size() == 0) return null
        val text = content.get(0).asJsonObject.get("text")?.asString ?: return null
        return try {
            val payload = gson.fromJson(text, Payload::class.java)
            if (payload?.results.isNullOrEmpty()) {
                android.util.Log.i(TAG, "empty answer: ${text.take(250)}")
            }
            payload
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not read the answer: ${text.take(250)}")
            null
        }
    }

    private fun post(body: String): JsonObject? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 75_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            sessionId?.let { conn.setRequestProperty("Mcp-Session-Id", it) }
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw OctoTripException("HTTP $code: ${text.take(200)}")
            }
            conn.getHeaderField("Mcp-Session-Id")?.let { sessionId = it }
            return parseEnvelope(text)
        } catch (e: OctoTripException) {
            throw e
        } catch (e: Exception) {
            throw OctoTripException(e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseEnvelope(body: String): JsonObject? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) {
            return try {
                gson.fromJson(trimmed, JsonObject::class.java)
            } catch (e: Exception) {
                null
            }
        }
        val data = trimmed.lineSequence()
            .filter { it.trimStart().startsWith("data:") }
            .map { it.trimStart().removePrefix("data:").trim() }
            .lastOrNull { it.startsWith("{") }
            ?: return null
        return try {
            gson.fromJson(data, JsonObject::class.java)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val ENDPOINT = "https://mcp.octotrip.app/flights/mcp"
        private const val HOST = "mcp.octotrip.app"
        /**
         * The wait between calls.
         *
         * The phone's own resolver stops answering after a burst of look-ups,
         * and the host here has a short time to live, so every call needs a
         * fresh name lookup. Asking for them one at a time, with this pause
         * between, is what keeps the resolver willing to answer.
         */
        private const val REQUEST_GAP_MS = 13_000L
        private const val RETRY_WAIT_MS = 20_000L
        private const val SETTLE_MS = 4_000L
        private const val TAG = "FlightWatch"

        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

        /** The flight numbers on one leg, cleaned up for comparison. */
        fun numbersOf(leg: Leg?): List<String> {
            val fromLegs = leg?.legs?.mapNotNull { it.flightNumber }.orEmpty()
            if (fromLegs.isNotEmpty()) return fromLegs.map { BookingLinks.normalize(it) }
            return emptyList()
        }
    }
}

class OctoTripException(message: String) : Exception(message)

