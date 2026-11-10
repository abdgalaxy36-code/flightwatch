package com.flightwatch.app.data

/**
 * Where each airline's own booking site lives.
 *
 * The search runs with airline_only, so every price shown is the airline's own
 * site price - tapping the price should take the traveller to that same site.
 * These are the official airline domains; the booking paths below are the ones
 * verified to resolve, the rest fall back to the site's root.
 *
 * This is the fallback used when no link to the exact flight could be found.
 * The flight number is passed along because several of these sites read it
 * from the address and open the flight straight away.
 */
object AirlineSites {

    private val BOOKING_URL = mapOf(
        "SV" to "https://www.saudia.com/en/booking/flights",
        "F3" to "https://www.flyadeal.com/en/search-flight",
        "TK" to "https://www.turkishairlines.com/en/flights/booking",
        "EK" to "https://www.emirates.com/english/booking/",
        "GA" to "https://www.gulfair.com/en/booking",
        "A3" to "https://www.aegeanair.com/en/book",
        "MS" to "https://www.egyptair.com/en/booking",
        "XY" to "https://www.flynas.com/en/book",
    )

    fun urlFor(
        airlineCode: String,
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        flightNumber: String? = null,
        returnFlightNumber: String? = null,
    ): String {
        val base = BOOKING_URL[airlineCode] ?: "https://www.flynas.com/en/book"
        return if (airlineCode == "F3") {
            flyadealUrl(base, origin, destination, departureDate, returnDate, flightNumber, returnFlightNumber)
        } else {
            genericUrl(base, origin, destination, departureDate, returnDate, flightNumber)
        }
    }

    /**
     * flyadeal reads the trip from origin1, destination1, departure1 and the
     * second leg from destination2 and departure2, and it takes the chosen
     * flights as a JSON list in the flights parameter.
     *
     * The list holds the flight out first and the flight back second, and
     * each one is written the way the site writes it: the airline code, a
     * space, and the number on its own - F3 807 rather than F3807.
     */
    private fun flyadealUrl(
        base: String,
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        flightNumber: String?,
        returnFlightNumber: String?,
    ): String {
        val url = StringBuilder(base)
        url.append("?origin1=").append(origin)
        url.append("&destination1=").append(destination)
        url.append("&departure1=").append(departureDate)
        if (!returnDate.isNullOrBlank()) {
            url.append("&destination2=").append(origin)
            url.append("&departure2=").append(returnDate)
        }
        url.append("&adt1=1&chd1=0&inf1=0")
        val flights = listOfNotNull(
            flightNumber?.takeIf { it.isNotBlank() },
            returnFlightNumber?.takeIf { it.isNotBlank() },
        )
        if (flights.isNotEmpty()) {
            val json = flights.joinToString(",", prefix = "[", postfix = "]") { number ->
                "{\"flightNumber\":\"${siteNumber(number)}\"}"
            }
            url.append("&flights=").append(enc(json))
        }
        return url.toString()
    }

    /**
     * The way the site writes a flight number: the airline code, a space and
     * the number on its own. F3807 is shown as F3 807.
     */
    private fun siteNumber(flightNumber: String): String {
        val spaced = flightNumber.replace(Regex("([A-Z0-9]{2})(\\d+)"), "$1 $2")
        return spaced
    }

    private fun genericUrl(
        base: String,
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String?,
        flightNumber: String?,
    ): String {
        val params = buildString {
            append("?from=").append(origin)
            append("&to=").append(destination)
            append("&departureDate=").append(departureDate)
            if (!returnDate.isNullOrBlank()) append("&returnDate=").append(returnDate)
            append("&adults=1")
            append("&currency=").append(FlightRules.CURRENCY)
            if (!flightNumber.isNullOrBlank()) append("&flightNumber=").append(flightNumber)
        }
        return base + params
    }

    /**
     * Percent-encoding for a query value.
     *
     * Spaces become %20 and not +, because the site decodes the value with
     * decodeURIComponent, which leaves a plus sign alone.
     */
    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
