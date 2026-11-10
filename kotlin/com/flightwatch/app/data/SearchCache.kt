package com.flightwatch.app.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Keeps the last search result on disk.
 *
 * This matters more than it looks: an airline_only search is not a cheap
 * request - it makes the server reprice every flight it found, and the account
 * is charged accordingly (32 credits on a JED-IST search). Re-running the same
 * search costs double each time. So the app shows the saved result and only
 * searches again when the traveller asks for it.
 */
class SearchCache(context: Context) {

    private val dir = File(context.filesDir, "search_cache").apply { mkdirs() }
    private val gson = Gson()

    fun save(key: String, response: SearchResponse) {
        try {
            val file = fileFor(key)
            file.writeText(gson.toJson(response))
            metaFor(key).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            // A cache miss is not fatal - the app can always search again.
        }
    }

    fun load(key: String): SearchResponse? {
        return try {
            val file = fileFor(key)
            if (!file.exists()) return null
            gson.fromJson(file.readText(), SearchResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** When this result was fetched, in millis. */
    fun savedAt(key: String): Long {
        return try {
            val file = metaFor(key)
            if (file.exists()) file.readText().trim().toLong() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun has(key: String): Boolean = fileFor(key).exists()

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
}
