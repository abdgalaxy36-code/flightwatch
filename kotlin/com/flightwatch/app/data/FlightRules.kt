package com.flightwatch.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The trip rules for page 1 (Jeddah -> Istanbul).
 *
 * Times from the API are already local to the airports (Jeddah and Istanbul are
 * both UTC+3, Arabia Standard Time / Turkey Time, neither has DST). Turkish
 * Airlines TK127 leaving Jeddah at 20:10 comes back as 20:10, so no offset is
 * applied - adding one would push the flight out of the window.
 *
 * Outbound: direct only, departs at/after 4 PM local, arrives by 11 PM local
 *           the same day, or on the red eye before 2 AM the next morning.
 * Return:   direct only, departing after 6 PM local.
 * Prices:   Saudi Riyal only, and always the airline's own site - the search
 *           runs with airline_only so third-party sellers never reach the app.
 *
 * There is deliberately no "allow connecting flights" switch: direct is fixed.
 */
object FlightRules {

    const val CURRENCY = "SAR"

    /** Outbound leaves at/after 4 PM local and lands by 11 PM local. */
    val EARLIEST_OUTBOUND_DEPARTURE: LocalTime = LocalTime.of(16, 0)
    val EARLIEST_ARRIVAL: LocalTime = LocalTime.of(16, 0)
    val LATEST_ARRIVAL: LocalTime = LocalTime.of(23, 0)

    /** A late departure may land in the small hours of the next morning. */
    val LATEST_NEXT_DAY_ARRIVAL: LocalTime = LocalTime.of(2, 0)

    /** The return cutoff, shown as one section on the main page. */
    val RETURN_CUTOFFS = listOf(
        ReturnCutoff("Return after 6 PM", LocalTime.of(18, 0)),
    )

    /** Airline display names, keyed by IATA airline code. */
    private val AIRLINE_BRAND = mapOf(
        "SV" to "Saudia",
        "F3" to "flyadeal",
        "TK" to "Turkish Airlines",
        "EK" to "Emirates",
        "GA" to "Gulf Air",
        "A3" to "Aegean Airlines",
        "MS" to "EgyptAir",
        "XY" to "flynas",
    )

    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseDateTime(raw: String?): LocalDateTime? = try {
        if (raw.isNullOrBlank()) null else {
            // Strip timezone offset (e.g. +03:00, -05:00) and Z, but keep the date part intact
            val cleaned = raw
                .substringBefore('T').let { datePart ->
                    val timePart = raw.substringAfter('T', "")
                    // Remove offset from time part only (after HH:mm:ss)
                    val timeClean = timePart.replace(Regex("[+-]\\d{2}:\\d{2}$"), "").replace("Z", "").trimEnd('.')
                    "$datePart T$timeClean"
                }
                .replace(" T", "T")
            LocalDateTime.parse(cleaned)
        }
    } catch (e: Exception) {
        null
    }

    /** The API already reports local airport times, so this is a plain parse. */
    fun parseDateTimeLocal(raw: String?): LocalDateTime? = parseDateTime(raw)

    fun parseDate(raw: String?): LocalDate? = try {
        if (raw.isNullOrBlank()) null else LocalDate.parse(raw)
    } catch (e: Exception) {
        null
    }

    fun formatTime(raw: String?): String {
        val dt = parseDateTime(raw) ?: return "--:--"
        return dt.format(TIME_FMT)
    }

    fun formatDay(raw: String?): String {
        val d = parseDate(raw?.substringBefore('T') ?: return "") ?: return ""
        val day = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val month = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$day ${d.dayOfMonth} $month"
    }

    private fun minutesOfDay(t: LocalTime): Int = t.hour * 60 + t.minute

    /** A flight is direct when it has no stop and no intermediate segment. */
    fun isDirect(offer: Offer): Boolean {
        val stops = offer.stops ?: return false
        if (stops != 0) return false
        val segs = offer.segments
        if (segs != null && segs.size > 1) return false
        return true
    }

    fun isDirect(inbound: Inbound?): Boolean {
        if (inbound == null) return false
        if ((inbound.stops ?: 1) != 0) return false
        val segs = inbound.segments
        if (segs != null && segs.size > 1) return false
        return true
    }

    /**
     * The arrival window: land by 11 PM on the departure day, or on the red eye
     * before 2 AM the next morning.
     */
    private fun arrivalInWindow(dep: LocalDateTime, arr: LocalDateTime): Boolean {
        if (dep.toLocalDate() == arr.toLocalDate()) {
            if (minutesOfDay(arr.toLocalTime()) < minutesOfDay(EARLIEST_ARRIVAL)) return false
            if (minutesOfDay(arr.toLocalTime()) > minutesOfDay(LATEST_ARRIVAL)) return false
            return true
        }
        if (arr.toLocalDate() == dep.toLocalDate().plusDays(1)) {
            return minutesOfDay(arr.toLocalTime()) <= minutesOfDay(LATEST_NEXT_DAY_ARRIVAL)
        }
        return false
    }

    /**
     * Outbound rule.
     *
     * [allowLateArrival] is the relaxation the traveller can turn on in
     * settings: the departure rule still holds, but the arrival may land at
     * any time, including after midnight and on the next day.
     *
     * [origin] only JED keeps the evening departure / arrival-window rules;
     * every other airport page shows all direct SAR flights at any hour.
     */
    fun matchesOutbound(
        offer: Offer,
        allowLateArrival: Boolean = false,
        origin: String = "JED",
    ): Boolean {
        if (!isDirect(offer)) return false
        if (offer.currency != CURRENCY) return false
        if (!isTimeRuledOrigin(origin)) return true
        val dep = parseDateTimeLocal(offer.departureTime) ?: return false
        val arr = parseDateTimeLocal(offer.arrivalTime) ?: return false
        if (minutesOfDay(dep.toLocalTime()) < minutesOfDay(EARLIEST_OUTBOUND_DEPARTURE)) return false
        if (allowLateArrival) return true
        return arrivalInWindow(dep, arr)
    }

    fun matchesOutbound(
        card: FlightCard,
        allowLateArrival: Boolean = false,
        origin: String = "JED",
    ): Boolean {
        if (card.stops != 0) return false
        if (!isTimeRuledOrigin(origin)) return true
        val dep = parseDateTimeLocal(card.departureTime) ?: return false
        val arr = parseDateTimeLocal(card.arrivalTime) ?: return false
        if (minutesOfDay(dep.toLocalTime()) < minutesOfDay(EARLIEST_OUTBOUND_DEPARTURE)) return false
        if (allowLateArrival) return true
        return arrivalInWindow(dep, arr)
    }

    /** JED keeps the evening rules; every other origin is direct-at-any-hour. */
    fun isTimeRuledOrigin(origin: String?): Boolean =
        origin.isNullOrBlank() || origin.equals("JED", ignoreCase = true)

    /** Does this return leg depart late enough for the given cutoff? */
    fun qualifies(returnLeg: ReturnLeg, cutoff: ReturnCutoff): Boolean {
        val dep = parseDateTimeLocal(returnLeg.departureTime) ?: return false
        return minutesOfDay(dep.toLocalTime()) >= minutesOfDay(cutoff.time)
    }

    /** The best display name for an airline, from its code, else the raw name. */
    fun displayAirline(offer: Offer): String =
        AIRLINE_BRAND[offer.airlineCode] ?: normalizeName(offer.airline)

    /** flyadeal is Saudia's low-cost brand - they filter together. */
    fun airlineGroup(offer: Offer): String =
        if (offer.airlineCode == "F3") "Saudia" else displayAirline(offer)

    /** "SAUDIA" and "Saudia" are the same seller - fold the casing together. */
    fun normalizeName(name: String?): String {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return "Unknown"
        if (n == n.uppercase() && n.length > 2) {
            return n.lowercase().split(" ").joinToString(" ") { w ->
                w.replaceFirstChar { it.uppercase() }
            }
        }
        return n
    }

    /**
     * Collapse the raw offers into one card per physical flight.
     *
     * Every offer is an airline-site offer (the search runs airline_only), and
     * each one pairs the outbound with a specific return leg at a round-trip
     * package price. Returns are collected per flight and priced at the best
     * package price seen for that return.
     */
    fun group(offers: List<Offer>, allowedAirlines: Set<String>): List<FlightCard> {
        val byFlight = LinkedHashMap<String, MutableList<Offer>>()
        for (o in offers) {
            if (allowedAirlines.isNotEmpty() && !allowedAirlines.contains(airlineGroup(o).lowercase())) continue
            byFlight.getOrPut(o.flightKey) { mutableListOf() }.add(o)
        }

        val cards = mutableListOf<FlightCard>()
        for ((key, group) in byFlight) {
            val head = group.minByOrNull { it.price ?: Double.MAX_VALUE } ?: continue

            val returns = group.mapNotNull { o ->
                val ib = o.inbound ?: return@mapNotNull null
                if (!isDirect(ib)) return@mapNotNull null
                ReturnLeg(
                    key = "${ib.airlineCode ?: ib.airline}|${ib.flightNumber ?: "-"}|${ib.departureTime}",
                    flightNumber = ib.flightNumber ?: "-",
                    airline = normalizeName(ib.airline),
                    departureTime = ib.departureTime ?: "",
                    arrivalTime = ib.arrivalTime ?: "",
                    stops = ib.stops ?: 0,
                    durationMinutes = ib.durationMinutes ?: 0,
                    price = o.price ?: 0.0,
                    currency = o.currency ?: CURRENCY,
                    ignavId = o.ignavId,
                )
            }.groupBy { it.key }
                .map { (_, v) -> v.minByOrNull { it.price }!! }
                .sortedBy { it.departureTime }

            // One card per outbound+return pair (all combinations)
            if (returns.isEmpty()) {
                cards += FlightCard(
                    key = key,
                    airline = displayAirline(head),
                    airlineCode = head.airlineCode ?: "",
                    flightNumber = head.flightNumber ?: "-",
                    departureTime = head.departureTime ?: "",
                    arrivalTime = head.arrivalTime ?: "",
                    stops = head.stops ?: 0,
                    durationMinutes = head.durationMinutes ?: 0,
                    segments = head.segments ?: emptyList(),
                    conditions = head.conditions,
                    ancillaries = head.ancillaries,
                    offer = head,
                    returns = emptyList(),
                    ignavId = head.ignavId,
                    origin = head.origin,
                    destination = head.destination,
                )
            } else {
                for (ret in returns) {
                    cards += FlightCard(
                        key = "$key|${ret.key}",
                        airline = displayAirline(head),
                        airlineCode = head.airlineCode ?: "",
                        flightNumber = head.flightNumber ?: "-",
                        departureTime = head.departureTime ?: "",
                        arrivalTime = head.arrivalTime ?: "",
                        stops = head.stops ?: 0,
                        durationMinutes = head.durationMinutes ?: 0,
                        segments = head.segments ?: emptyList(),
                        conditions = head.conditions,
                        ancillaries = head.ancillaries,
                        offer = head,
                        returns = listOf(ret),
                        ignavId = ret.ignavId ?: head.ignavId,
                        origin = head.origin,
                        destination = head.destination,
                    )
                }
            }
        }
        return cards
    }

    /**
     * Why the list is short (or empty). Used to explain an empty result instead
     * of showing a blank screen.
     */
    fun diagnose(
        offers: List<Offer>,
        allowedAirlines: Set<String>,
        origin: String = "JED",
    ): Funnel {
        val timeRuled = isTimeRuledOrigin(origin)
        var total = 0
        var direct = 0
        var afterDeparture = 0
        var afterSameDay = 0
        var afterArrivalWindow = 0
        var official = 0
        for (o in offers) {
            if (allowedAirlines.isNotEmpty() && !allowedAirlines.contains(airlineGroup(o).lowercase())) continue
            total++
            if (!isDirect(o)) continue
            direct++
            if (!timeRuled) {
                afterDeparture++
                afterSameDay++
                afterArrivalWindow++
                if (o.isOfficial) official++
                continue
            }
            val dep = parseDateTimeLocal(o.departureTime) ?: continue
            val arr = parseDateTimeLocal(o.arrivalTime) ?: continue
            if (minutesOfDay(dep.toLocalTime()) < minutesOfDay(EARLIEST_OUTBOUND_DEPARTURE)) continue
            afterDeparture++
            if (arr.toLocalDate() != dep.toLocalDate() && arr.toLocalDate() != dep.toLocalDate().plusDays(1)) continue
            afterSameDay++
            if (!arrivalInWindow(dep, arr)) continue
            afterArrivalWindow++
            if (o.isOfficial) official++
        }
        return Funnel(total, direct, afterDeparture, afterSameDay, afterArrivalWindow, official)
    }
}

/** Search filters for flight search. */
data class SearchFilters(
    val maxStops: Int? = null, // null=any, 0=direct, 1=1 stop
    val departureTimeFrom: Int? = null, // hour 0-23
    val departureTimeTo: Int? = null,
    val arrivalTimeFrom: Int? = null,
    val arrivalTimeTo: Int? = null,
)

/** A cutoff for the return departure time. */
data class ReturnCutoff(
    val label: String,
    val time: LocalTime,
)

/** One physical outbound flight with its airline-site price and return options. */
data class FlightCard(
    val key: String,
    val airline: String,
    val airlineCode: String,
    val flightNumber: String,
    val departureTime: String,
    val arrivalTime: String,
    val stops: Int,
    val durationMinutes: Int,
    val segments: List<Segment>,
    val conditions: Conditions?,
    val ancillaries: Ancillaries?,
    val offer: Offer,
    val returns: List<ReturnLeg>,
    /** A link that opens this exact flight, when one could be found. */
    val bookingUrl: String? = null,
    /** Where that link goes, so the card can say so. */
    val bookingSeller: String? = null,
    /** What that seller charges for the same trip. */
    val bookingPrice: Double? = null,
    /** Set when the price did not come from the airline's own site. */
    val sourceLabel: String? = null,
    /** ignav.com itinerary id for this exact flight, when the fare came from ignav. */
    val ignavId: String? = null,
    /** All booking link options (airline + OTAs) with prices. */
    val bookingLinks: List<BookingLink> = emptyList(),
    /** Latest checked price (updated by PriceCheckWorker). Falls back to offer.price. */
    val currentPrice: Double? = null,
    /** When the price was last checked. */
    val priceCheckedAt: Long? = null,
    /** Route from the search (not settings). */
    val origin: String? = null,
    val destination: String? = null,
)

/** A return leg and the round-trip package price for pairing with it. */
data class ReturnLeg(
    val key: String,
    val flightNumber: String,
    val airline: String,
    val departureTime: String,
    val arrivalTime: String,
    val stops: Int,
    val durationMinutes: Int,
    val price: Double,
    val currency: String,
    /** A link that opens this exact flight and return. */
    val bookingUrl: String? = null,
    /** Where that link goes. */
    val bookingSeller: String? = null,
    /** What that seller charges for the same trip. */
    val bookingPrice: Double? = null,
    /** ignav.com itinerary id pairing this outbound with this return. */
    val ignavId: String? = null,
    /** All booking link options for this return leg. */
    val bookingLinks: List<BookingLink> = emptyList(),
)

/** Step-by-step counts so the UI can explain an empty or short list. */
data class Funnel(
    val total: Int,
    val direct: Int,
    val afterDepartureTime: Int,
    val afterSameDayArrival: Int,
    val afterArrivalWindow: Int,
    val withOfficialPrice: Int,
)
