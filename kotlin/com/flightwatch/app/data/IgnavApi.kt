package com.flightwatch.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * ignav.com Flight Prices & Booking Links API client.
 *
 * - Round-trip search: POST /api/fares/round-trip
 * - Booking links: POST /api/fares/booking-links (via ignav_id)
 * - 1,000 free successful requests/month, no rate limit
 */
class IgnavApi(private val apiKey: String, private val usage: ApiUsageStore? = null) {

    private val gson = Gson()
    private val BASE_URL = "https://ignav.com/api/fares"

    // ---------------------------------------------------------------- request/response models

    data class RoundTripRequest(
        val origin: String,
        val destination: String,
        val departure_date: String,
        val return_date: String,
        val adults: Int = 1,
        val max_stops: Int? = null,
        val cabin_class: String = "economy",
        val market: String? = null,
        val departure_time_range: TimeRange? = null,
    )

    /** Hour filters (airport-local, inclusive). */
    data class TimeRange(
        val earliest_hour: Int? = null,
        val latest_hour: Int? = null,
        val arrival_earliest_hour: Int? = null,
        val arrival_latest_hour: Int? = null,
    )

    data class Segment(
        @SerializedName("marketing_carrier_code") val marketingCarrierCode: String,
        @SerializedName("flight_number") val flightNumber: String,
        @SerializedName("operating_carrier_name") val operatingCarrierName: String?,
        @SerializedName("departure_airport") val departureAirport: String,
        @SerializedName("departure_time_local") val departureTimeLocal: String,
        @SerializedName("departure_timezone") val departureTimezone: String?,
        @SerializedName("arrival_airport") val arrivalAirport: String,
        @SerializedName("arrival_time_local") val arrivalTimeLocal: String,
        @SerializedName("arrival_timezone") val arrivalTimezone: String?,
        @SerializedName("duration_minutes") val durationMinutes: Int,
        @SerializedName("aircraft") val aircraft: String?,
    )

    data class Leg(
        val carrier: String,
        @SerializedName("duration_minutes") val durationMinutes: Int,
        val segments: List<Segment>? = emptyList(),
    )

    data class Price(
        val amount: Double,
        val currency: String,
        val status: String,
    )

    data class Itinerary(
        val price: Price? = null,
        val outbound: Leg? = null,
        val inbound: Leg?,
        @SerializedName("cabin_class") val cabinClass: String,
        @SerializedName("has_mixed_carriers") val hasMixedCarriers: Boolean,
        @SerializedName("ignav_id") val ignavId: String,
    )

    data class RoundTripResponse(
        val origin: String,
        val destination: String,
        @SerializedName("departure_date") val departureDate: String,
        @SerializedName("return_date") val returnDate: String,
        @SerializedName("observed_at") val observedAt: String,
        val itineraries: List<Itinerary>? = emptyList(),
    )

    data class BookingLinksRequest(
        val ignav_id: String,
    )

    data class BookingLink(
        val legs: List<String>,
        val links: List<ProviderLink>,
    )

    data class ProviderLink(
        @SerializedName("provider_name") val providerName: String,
        @SerializedName("provider_type") val providerType: String?,
        @SerializedName("fare_name") val fareName: String?,
        val specificity: String?,
        val price: Price?,
        val url: String,
    )

    data class BookingLinksResponse(
        val itinerary: Itinerary,
        @SerializedName("booking_options") val bookingOptions: List<BookingLink>? = emptyList(),
    )

    // ---------------------------------------------------------------- round-trip search

    /**
     * Search round-trip fares. Returns multiple itineraries, each with an ignav_id.
     * One request returns multiple outbound+return combos for the same dates.
     */
    suspend fun searchRoundTrip(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String,
        adults: Int = 1,
        maxStops: Int? = null,
        market: String? = null,
        departureTimeRange: TimeRange? = null,
    ): RoundTripResponse = withContext(Dispatchers.IO) {
        val request = RoundTripRequest(
            origin = origin.uppercase(),
            destination = destination.uppercase(),
            departure_date = departureDate,
            return_date = returnDate,
            adults = adults,
            max_stops = maxStops,
            market = market,
            departure_time_range = departureTimeRange,
        )
        usage?.bump(ApiUsageStore.IGNAV)
        val json = post("$BASE_URL/round-trip", request) ?: throw IgnavException("Ignav returned nothing")
        gson.fromJson(json, RoundTripResponse::class.java)
    }

    // ---------------------------------------------------------------- booking links

    /**
     * Fetch booking links for an exact itinerary using its ignav_id.
     * Returns airline-direct and OTA links with specificity = "exact_flight" or "search_results".
     * Returns HTTP 404 if no usable links (does not consume request credit).
     */
    suspend fun fetchBookingLinks(ignavId: String): BookingLinksResponse = withContext(Dispatchers.IO) {
        usage?.bump(ApiUsageStore.IGNAV)
        val request = BookingLinksRequest(ignav_id = ignavId)
        val json = post("$BASE_URL/booking-links", request)
        if (json == null) throw IgnavException("Ignav booking links not found (404)")
        gson.fromJson(json, BookingLinksResponse::class.java)
    }

    // ---------------------------------------------------------------- HTTP helper

    private fun post(endpoint: String, body: Any): String? {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("X-Api-Key", apiKey)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")

        try {
            val json = gson.toJson(body)
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code == 404) return null
            if (code !in 200..299) throw IgnavException("Ignav HTTP $code: ${text.take(200)}")
            return text
        } catch (e: IgnavException) {
            throw e
        } catch (e: Exception) {
            throw IgnavException(e.message ?: "Network error")
        } finally {
            conn.disconnect()
        }
    }

    class IgnavException(message: String) : Exception(message)
}

/**
 * Convert Ignav itineraries to the internal [Offer] shape the search pipeline
 * already understands (grouping, funnel, history, cache) - so an ignav search
 * flows through exactly the same code as a LetsFG one.
 */
object IgnavMapper {

    /** One [Offer] per itinerary, carrying the ignav_id for booking links. */
    fun mapToOffers(response: IgnavApi.RoundTripResponse): List<Offer> {
        val offers = mutableListOf<Offer>()
        for (it in response.itineraries ?: emptyList()) {
            val outboundSegments = it.outbound?.segments ?: emptyList()
            if (outboundSegments.isEmpty()) continue

            val firstOut = outboundSegments.first()
            val lastOut = outboundSegments.last()
            val inboundSegments = it.inbound?.segments ?: emptyList()
            val firstIn = inboundSegments.firstOrNull()
            val lastIn = inboundSegments.lastOrNull()

            val outboundFlightNumber = "${firstOut.marketingCarrierCode}${firstOut.flightNumber}"
            val returnFlightNumber = firstIn?.let { "${it.marketingCarrierCode}${it.flightNumber}" } ?: ""

            val inbound = if (inboundSegments.isNotEmpty()) {
                Inbound(
                    origin = firstIn?.departureAirport ?: "",
                    destination = lastIn?.arrivalAirport ?: "",
                    departureTime = firstIn?.departureTimeLocal ?: "",
                    arrivalTime = lastIn?.arrivalTimeLocal ?: "",
                    durationMinutes = it.inbound?.durationMinutes ?: 0,
                    stops = (inboundSegments.size - 1).coerceAtLeast(0),
                    airline = it.inbound?.carrier ?: "",
                    airlineCode = firstIn?.marketingCarrierCode ?: "",
                    flightNumber = returnFlightNumber,
                    segments = inboundSegments.map { s -> segmentOf(s) },
                )
            } else {
                null
            }

            offers += Offer(
                price = it.price?.amount ?: 0.0,
                currency = it.price?.currency ?: "SAR",
                airline = it.outbound?.carrier ?: "",
                airlineCode = firstOut.marketingCarrierCode,
                origin = firstOut.departureAirport,
                destination = lastOut.arrivalAirport,
                departureTime = firstOut.departureTimeLocal,
                arrivalTime = lastOut.arrivalTimeLocal,
                stops = (outboundSegments.size - 1).coerceAtLeast(0),
                flightNumber = outboundFlightNumber,
                segments = outboundSegments.map { s -> segmentOf(s) },
                inbound = inbound,
                conditions = Conditions(
                    sellerKind = "airline",
                    bookingChannel = "airline_direct",
                    bookingProvider = it.outbound?.carrier ?: "",
                ),
                source = "ignav",
                ignavId = it.ignavId,
            )
        }
        return offers
    }

    private fun segmentOf(s: IgnavApi.Segment) = Segment(
        airline = s.operatingCarrierName ?: s.marketingCarrierCode,
        airlineCode = s.marketingCarrierCode,
        flightNumber = "${s.marketingCarrierCode}${s.flightNumber}",
        origin = s.departureAirport,
        destination = s.arrivalAirport,
        departureTime = s.departureTimeLocal,
        arrivalTime = s.arrivalTimeLocal,
        durationMinutes = s.durationMinutes,
        aircraft = s.aircraft,
    )
}
