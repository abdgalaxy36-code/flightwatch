package com.flightwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Per-flight price history for Simple Mode.
 * Records every observed price so each flight has its own history and chart.
 * Uses SharedPreferences + JSON for persistence (no Room dependency).
 */
data class FlightPricePoint(
    val flightKey: String,      // "SV557-SV560" (outbound-return)
    val timestamp: Long,
    val price: Double,
    val currency: String,
)

class PriceHistoryStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("flight_price_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun record(flightKey: String, price: Double, currency: String) {
        // Skip if price unchanged from last record
        val existing = history(flightKey)
        if (existing.isNotEmpty() && existing.last().price == price) return
        val history = history(flightKey).toMutableList()
        history.add(FlightPricePoint(flightKey, System.currentTimeMillis(), price, currency))
        // Trim to last 100 entries
        val trimmed = if (history.size > 100) history.takeLast(100) else history
        prefs.edit().putString(flightKey, gson.toJson(trimmed)).apply()
    }

    fun history(flightKey: String): List<FlightPricePoint> {
        val json = prefs.getString(flightKey, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FlightPricePoint>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun lowPrice(flightKey: String): Double? = history(flightKey).minOfOrNull { it.price }

    fun highPrice(flightKey: String): Double? = history(flightKey).maxOfOrNull { it.price }

    fun latest(flightKey: String): FlightPricePoint? = history(flightKey).maxByOrNull { it.timestamp }
}
