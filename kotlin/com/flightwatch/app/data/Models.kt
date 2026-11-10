package com.flightwatch.app.data

import com.google.gson.annotations.SerializedName

/** One physical flight leg (a segment of an offer). */
data class Segment(
    @SerializedName("airline") val airline: String? = null,
    @SerializedName("airline_code") val airlineCode: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("departure_time") val departureTime: String? = null,
    @SerializedName("arrival_time") val arrivalTime: String? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("aircraft") val aircraft: String? = null,
    @SerializedName("cabin") val cabin: String? = null,
)

/** The return leg of a round-trip offer. */
data class Inbound(
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("departure_time") val departureTime: String? = null,
    @SerializedName("arrival_time") val arrivalTime: String? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("stops") val stops: Int? = null,
    @SerializedName("airline") val airline: String? = null,
    @SerializedName("airline_code") val airlineCode: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("segments") val segments: List<Segment>? = null,
)

/** The fare rules attached to an offer. */
data class Conditions(
    @SerializedName("seller_kind") val sellerKind: String? = null,
    @SerializedName("booking_channel") val bookingChannel: String? = null,
    @SerializedName("booking_provider") val bookingProvider: String? = null,
    @SerializedName("self_transfer") val selfTransfer: String? = null,
    @SerializedName("fare_type") val fareType: String? = null,
    @SerializedName("refund_before_departure") val refundBeforeDeparture: String? = null,
    @SerializedName("change_before_departure") val changeBeforeDeparture: String? = null,
    @SerializedName("carry_on") val carryOn: String? = null,
    @SerializedName("checked_bag") val checkedBag: String? = null,
    @SerializedName("seat") val seat: String? = null,
    @SerializedName("cabin_verified") val cabinVerified: String? = null,
)

data class Ancillaries(
    @SerializedName("checked_bag") val checkedBag: BagInfo? = null,
    @SerializedName("cabin_bag") val cabinBag: BagInfo? = null,
    @SerializedName("seat_selection") val seatSelection: BagInfo? = null,
)

data class BagInfo(
    @SerializedName("included") val included: Boolean? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("description") val description: String? = null,
)

/**
 * A single sellable offer: one price from one seller for one flight.
 * [sellerKind] is the field that matters: "airline" = the airline's own site,
 * "ota" = a third-party seller. Do NOT use [source] for this - source names the
 * metadata aggregator (skyscanner_meta etc.), not the seller.
 */
data class Offer(
    @SerializedName("id") val id: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("airline") val airline: String? = null,
    @SerializedName("airline_code") val airlineCode: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("origin_name") val originName: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("destination_name") val destinationName: String? = null,
    @SerializedName("departure_time") val departureTime: String? = null,
    @SerializedName("arrival_time") val arrivalTime: String? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("stops") val stops: Int? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("segments") val segments: List<Segment>? = null,
    @SerializedName("inbound") val inbound: Inbound? = null,
    @SerializedName("conditions") val conditions: Conditions? = null,
    @SerializedName("ancillaries") val ancillaries: Ancillaries? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("is_combo") val isCombo: Boolean? = null,
    @SerializedName("long_layover_hours") val longLayoverHours: Double? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("suspect_reason") val suspectReason: String? = null,
    /** ignav.com itinerary id - the handle for exact-flight booking links. */
    @SerializedName("ignav_id") val ignavId: String? = null,
) {
    val isOfficial: Boolean get() = conditions?.sellerKind == "airline"
    val sellerName: String get() = conditions?.bookingProvider ?: source ?: "unknown"
    val isSplitTicket: Boolean get() = conditions?.selfTransfer == "unprotected"

    /** Stable identity of the physical flight, independent of who sells it. */
    val flightKey: String
        get() = "${airlineCode ?: airline}|${flightNumber ?: "-"}|${departureTime ?: "-"}"
}

/** Top-level search response from POST /api/search and GET /api/results/{id}. */
data class SearchResponse(
    @SerializedName("search_id") val searchId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("offers") val offers: List<Offer>? = null,
    @SerializedName("total_results") val totalResults: Int? = null,
    @SerializedName("cheapest_price") val cheapestPrice: Double? = null,
    @SerializedName("google_flights_price") val googleFlightsPrice: Double? = null,
    @SerializedName("savings_vs_google_flights") val savingsVsGoogleFlights: Double? = null,
    @SerializedName("elapsed_seconds") val elapsedSeconds: Double? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("split_ticket_pending") val splitTicketPending: Boolean? = null,
    @SerializedName("gf_enrich_pending") val gfEnrichPending: Boolean? = null,
    @SerializedName("parsed") val parsed: ParsedQuery? = null,
)

data class ParsedQuery(
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("return_date") val returnDate: String? = null,
    @SerializedName("adults") val adults: Int? = null,
)
