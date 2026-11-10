package com.flightwatch.app.data

import java.net.URLEncoder

/**
 * Puts a booking link on the right flight.
 *
 * LetsFG prices every fare on the airline's own site but never hands out a
 * link, so the app looks the same flight up in OctoTrip and borrows its link.
 * Matching is done on the flight numbers and the dates, because two airlines
 * can share a route and the same departure slot.
 */
object BookingLinks {

    /** "SV 261", "sv261" and "SV-261" are all the same flight. */
    fun normalize(number: String?): String =
        number?.uppercase()?.replace(Regex("[^A-Z0-9]"), "").orEmpty()

    /**
     * A Google Flights search link for this exact trip.
     *
     * Free, needs no API key, and always opens something useful - used as the
     * fallback whenever a per-seller booking URL could not be resolved.
     */
    fun googleFlightsUrl(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        flightNumber: String?,
        returnFlightNumber: String? = null,
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
        if (!returnFlightNumber.isNullOrBlank()) {
            url.append("&f=1:").append(URLEncoder.encode(returnFlightNumber.replace(" ", ""), "UTF-8"))
        }
        return url.toString()
    }

    /**
     * Find the offer that sells this exact pair of flights.
     *
     * [outboundNumber] and [returnNumber] are the flight numbers as the airline
     * writes them. [outboundDate] and [returnDate] are yyyy-MM-dd. The cheapest
     * matching offer wins.
     */
    fun find(
        outboundNumber: String,
        outboundDate: String,
        returnNumber: String? = null,
        returnDate: String? = null,
        flights: List<OctoTripApi.Flight>,
    ): OctoTripApi.Flight? {
        val wantedOut = normalize(outboundNumber)
        val wantedBack = normalize(returnNumber)
        if (wantedOut.isEmpty()) return null

        return flights
            .filter { f -> matchesOutbound(f, wantedOut, outboundDate) }
            .filter { f -> wantedBack.isEmpty() || matchesReturn(f, wantedBack, returnDate) }
            .minByOrNull { it.price ?: Double.MAX_VALUE }
    }

    private fun matchesOutbound(f: OctoTripApi.Flight, wanted: String, date: String?): Boolean {
        val leg = f.outbound ?: return false
        val numbers = OctoTripApi.numbersOf(leg)
        if (numbers.isNotEmpty() && !numbers.contains(wanted)) return false
        if (numbers.isEmpty()) {
            val top = f.flightNumbers?.map { normalize(it) }.orEmpty()
            if (top.isNotEmpty() && !top.contains(wanted)) return false
        }
        if (!date.isNullOrBlank()) {
            val legDate = leg.departureDate
            if (!legDate.isNullOrBlank() && legDate != date) return false
        }
        return true
    }

    private fun matchesReturn(f: OctoTripApi.Flight, wanted: String, date: String?): Boolean {
        val leg = f.inbound ?: return false
        val numbers = OctoTripApi.numbersOf(leg)
        if (numbers.isNotEmpty() && !numbers.contains(wanted)) return false
        if (numbers.isEmpty()) {
            val top = f.flightNumbers?.map { normalize(it) }.orEmpty()
            if (top.size > 1 && !top.contains(wanted)) return false
        }
        if (!date.isNullOrBlank()) {
            val legDate = leg.departureDate
            if (!legDate.isNullOrBlank() && legDate != date) return false
        }
        return true
    }

    /**
     * The place a link points at, so the card can say where the traveller is
     * going before they tap.
     */
    /** Where a link goes, friendly name for the host. */
    fun sellerOf(url: String?): String? = sellerNameForHost(hostOf(url))

    /** The host a link goes to, if it is not one of our own redirect addresses. */
    fun hostOf(url: String?): String? {
        val raw = url?.substringAfter("://", "")?.substringBefore('/')?.lowercase() ?: return null
        if (raw.isBlank()) return null
        if (raw == "mcp.octotrip.app") return null
        return raw
    }

    /** A friendly name for the site behind a link. */
    fun sellerNameForHost(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val known = mapOf(
            "gotogate.com" to "GoToGate",
            "us.gotogate.com" to "GoToGate",
            "www.gotogate.com" to "GoToGate",
            "wingie.com" to "Wingie",
            "www.wingie.com" to "Wingie",
            "skyscanner.net" to "Skyscanner",
            "www.skyscanner.net" to "Skyscanner",
            "kayak.com" to "Kayak",
            "www.kayak.com" to "Kayak",
            "google.com" to "Google Flights",
            "www.google.com" to "Google Flights",
            "saudia.com" to "Saudia",
            "www.saudia.com" to "Saudia",
            "flyadeal.com" to "flyadeal",
            "www.flyadeal.com" to "flyadeal",
            "turkishairlines.com" to "Turkish Airlines",
            "www.turkishairlines.com" to "Turkish Airlines",
            "delta.com" to "Delta",
            "aeromexico.com" to "Aeromexico",
        )
        return known[host]
            ?: host.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() }
    }
}
