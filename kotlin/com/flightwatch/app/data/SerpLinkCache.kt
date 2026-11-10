package com.flightwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Keeps the last Google Flights booking links on disk.
 *
 * SerpApi links are stable, so a saved answer is used while fresh and looked
 * up again only when it goes stale.
 */
class SerpLinkCache(context: Context) {

    private val dir = File(context.filesDir, "serp_link_cache").apply { mkdirs() }
    private val gson = Gson()

    fun save(key: String, flights: List<SerpApiClient.Flight>) {
        try {
            fileFor(key).writeText(gson.toJson(flights))
            metaFor(key).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
        }
    }

    fun load(key: String): List<SerpApiClient.Flight> {
        return try {
            val file = fileFor(key)
            if (!file.exists()) return emptyList()
            val type = object : TypeToken<List<SerpApiClient.Flight>>() {}.type
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
        private const val DEFAULT_MAX_AGE_MS = 60L * 60L * 24L * 1000L
    }
}
