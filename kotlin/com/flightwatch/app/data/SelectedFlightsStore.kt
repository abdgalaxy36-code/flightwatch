package com.flightwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists selected flights to disk so they survive app restarts.
 */
class SelectedFlightsStore(context: Context) {
    companion object {
        private val lock = Any()
    }
    private val prefs: SharedPreferences = context.getSharedPreferences("selected_flights", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(flights: List<FlightCard>) = synchronized(lock) {
        try {
            prefs.edit().putString("flights", gson.toJson(flights)).apply()
        } catch (e: Exception) {
        }
    }

    fun updatePrices(updates: Map<String, Pair<Double, Long>>) {
        synchronized(lock) {
            val flights = load().map { flight ->
                val update = updates[flight.key]
                if (update != null) {
                    flight.copy(currentPrice = update.first, priceCheckedAt = update.second)
                } else {
                    flight
                }
            }
            save(flights)
        }
    }

    fun load(): List<FlightCard> = synchronized(lock) {
        return try {
            val json = prefs.getString("flights", null) ?: return emptyList()
            val type = object : TypeToken<List<FlightCard>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() = synchronized(lock) {
        prefs.edit().remove("flights").apply()
    }
}
