package com.flightwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Remembers the booking link resolved for each ignav_id.
 *
 * A link lookup costs one of the 1,000 free monthly ignav requests (a 404
 * does not), so a resolved answer - including "this itinerary has no link" -
 * is reused for a day instead of being fetched again on every refresh.
 */
class IgnavLinkCache(context: Context) {

    data class Entry(
        val url: String? = null,
        val seller: String? = null,
        val price: Double? = null,
        val at: Long = 0L,
    ) {
        val found: Boolean get() = !url.isNullOrBlank()

        companion object {
            /** No usable link for this itinerary (HTTP 404). */
            fun none() = Entry(at = System.currentTimeMillis())

            /**
             * One link that covers the trip: a combined out+return option beats
             * split legs, an airline-direct link beats an OTA, and an
             * exact-flight link beats one that only opens search results.
             */
            fun from(response: IgnavApi.BookingLinksResponse): Entry {
                val usable = (response.bookingOptions ?: emptyList()).filter { option ->
                    option.links.any { it.url.isNotBlank() }
                }
                val option = usable.firstOrNull { it.legs.size >= 2 }
                    ?: usable.firstOrNull { it.legs.contains("outbound") }
                    ?: usable.firstOrNull()
                    ?: return none()
                val link = option.links.filter { it.url.isNotBlank() }.minWithOrNull(
                    compareByDescending<IgnavApi.ProviderLink> { it.specificity == "exact_flight" }
                        .thenByDescending { it.providerType == "airline" },
                ) ?: return none()
                val url = if (link.url.startsWith("http")) link.url else "https://${link.url}"
                return Entry(
                    url = url,
                    seller = link.providerName,
                    price = link.price?.amount,
                    at = System.currentTimeMillis(),
                )
            }
        }
    }

    private val file = File(context.filesDir, "ignav_link_cache.json")
    private val gson = Gson()
    private var entries: MutableMap<String, Entry>? = null

    /** The remembered link for this itinerary, while it is still fresh. */
    @Synchronized
    fun get(id: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Entry? {
        val entry = load()[id] ?: return null
        if (System.currentTimeMillis() - entry.at >= maxAgeMs) return null
        return entry
    }

    @Synchronized
    fun put(id: String, entry: Entry) {
        val map = load()
        map[id] = if (entry.at > 0L) entry else entry.copy(at = System.currentTimeMillis())
        entries = map
        try {
            file.writeText(gson.toJson(map))
        } catch (e: Exception) {
        }
    }

    private fun load(): MutableMap<String, Entry> {
        entries?.let { return it }
        val map: MutableMap<String, Entry> = try {
            if (file.exists()) {
                val type = object : TypeToken<MutableMap<String, Entry>>() {}.type
                gson.fromJson(file.readText(), type) ?: mutableMapOf()
            } else {
                mutableMapOf()
            }
        } catch (e: Exception) {
            mutableMapOf()
        }
        entries = map
        return map
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 60L * 60L * 24L * 1000L
    }
}
