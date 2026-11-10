package com.flightwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Keeps the last OctoTrip answer on disk.
 *
 * The booking links it returns stop working after about fifteen minutes, so
 * the saved copy is only used while it is fresh. The sweep itself is free, so
 * when the saved copy has gone stale the app simply looks again.
 */
class OctoTripCache(context: Context) {

    private val dir = File(context.filesDir, "octotrip_cache").apply { mkdirs() }
    private val gson = Gson()

    fun save(key: String, flights: List<OctoTripApi.Flight>) {
        try {
            fileFor(key).writeText(gson.toJson(flights))
            metaFor(key).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
        }
    }

    fun load(key: String): List<OctoTripApi.Flight> {
        return try {
            val file = fileFor(key)
            if (!file.exists()) return emptyList()
            val type = object : TypeToken<List<OctoTripApi.Flight>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savedAt(key: String): Long {
        return try {
            val file = metaFor(key)
            if (file.exists()) file.readText().trim().toLong() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun isFresh(key: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean {
        val at = savedAt(key)
        return at > 0L && System.currentTimeMillis() - at < maxAgeMs
    }

    fun clear(key: String) {
        try {
            fileFor(key).delete()
            metaFor(key).delete()
        } catch (e: Exception) {
        }
    }

    private fun fileFor(key: String) = File(dir, safe(key) + ".json")
    private fun metaFor(key: String) = File(dir, safe(key) + ".at")

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 10L * 60L * 1000L
    }
}
