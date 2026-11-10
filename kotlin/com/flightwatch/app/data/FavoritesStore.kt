package com.flightwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One saved favourite, keyed by airline + flight number within an airport page. */
data class FavoriteFlight(
    val flightNumber: String,
    val airline: String,
    val airlineCode: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val price: Double,
    val currency: String,
    val savedAt: Long = System.currentTimeMillis(),
)

/**
 * Favourites per departure airport.
 *
 * Stored as JSON in SharedPreferences under one key per airport page
 * ("JED-IST"), so each airport tab keeps its own list.
 */
class FavoritesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _flow = MutableStateFlow(loadAll())
    val flow: StateFlow<List<FavoriteFlight>> = _flow.asStateFlow()

    fun forPage(origin: String, destination: String): List<FavoriteFlight> =
        _flow.value.filter { it.origin.equals(origin, true) && it.destination.equals(destination, true) }

    fun isFavorite(origin: String, destination: String, flightNumber: String): Boolean =
        forPage(origin, destination).any { normalize(it.flightNumber) == normalize(flightNumber) }

    fun toggle(item: FavoriteFlight): Boolean {
        val key = pageKey(item.origin, item.destination)
        val list = readPage(key).toMutableList()
        val existing = list.indexOfFirst { normalize(it.flightNumber) == normalize(item.flightNumber) }
        val nowFavorite: Boolean
        if (existing >= 0) {
            list.removeAt(existing)
            nowFavorite = false
        } else {
            list.add(0, item)
            nowFavorite = true
        }
        writePage(key, list)
        _flow.value = loadAll()
        return nowFavorite
    }

    fun remove(origin: String, destination: String, flightNumber: String) {
        val key = pageKey(origin, destination)
        val list = readPage(key).filterNot { normalize(it.flightNumber) == normalize(flightNumber) }
        writePage(key, list)
        _flow.value = loadAll()
    }

    /** Refresh price on an existing favourite when the live fare is known. */
    fun updatePrice(origin: String, destination: String, flightNumber: String, price: Double, currency: String) {
        val key = pageKey(origin, destination)
        val list = readPage(key).toMutableList()
        val idx = list.indexOfFirst { normalize(it.flightNumber) == normalize(flightNumber) }
        if (idx >= 0 && list[idx].price != price) {
            list[idx] = list[idx].copy(price = price, currency = currency, savedAt = System.currentTimeMillis())
            writePage(key, list)
            _flow.value = loadAll()
        }
    }

    private fun pageKey(origin: String, destination: String) =
        "${origin.uppercase()}-${destination.uppercase()}"

    private fun readPage(key: String): List<FavoriteFlight> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteFlight>>() {}.type
            gson.fromJson<List<FavoriteFlight>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writePage(key: String, list: List<FavoriteFlight>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }

    private fun loadAll(): List<FavoriteFlight> {
        val out = mutableListOf<FavoriteFlight>()
        prefs.all.forEach { (k, v) ->
            if (v is String && k.contains('-')) {
                out += try {
                    val type = object : TypeToken<List<FavoriteFlight>>() {}.type
                    gson.fromJson<List<FavoriteFlight>>(v, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        return out.sortedByDescending { it.savedAt }
    }

    private fun normalize(n: String) = n.uppercase().replace(Regex("[^A-Z0-9]"), "")
}
