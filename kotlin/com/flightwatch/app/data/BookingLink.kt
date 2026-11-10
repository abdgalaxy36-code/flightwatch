package com.flightwatch.app.data

/**
 * A single booking link option for a flight.
 * Each provider (airline, OTA) can have a different price.
 */
data class BookingLink(
    val providerName: String,     // "Saudia", "Expedia", etc.
    val providerType: String?,    // "airline" or "third_party"
    val url: String,
    val price: Double?,
    val currency: String?,
    val isAirline: Boolean,       // true if official airline site
)
