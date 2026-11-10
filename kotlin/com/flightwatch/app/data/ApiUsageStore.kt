package com.flightwatch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Counts the requests this device has made to each API.
 *
 * The numbers are real counts taken from the app's own traffic, not a guess
 * at what the provider's account total is. SerpApi counts against a monthly
 * quota, so the counters start again when the month turns over.
 */
class ApiUsageStore(context: Context) {

    private val prefs = context.getSharedPreferences("api_usage", Context.MODE_PRIVATE)

    private val _counts = MutableStateFlow(readCounts())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    /** Record one request against an API. */
    fun bump(api: String) {
        val key = api.lowercase()
        val month = currentMonth()
        if (prefs.getString(KEY_MONTH, null) != month) {
            prefs.edit().clear().putString(KEY_MONTH, month).apply()
        }
        val next = (_counts.value[key] ?: 0) + 1
        prefs.edit().putInt(key, next).putString(KEY_MONTH, month).apply()
        _counts.value = _counts.value + (key to next)
    }

    /** How many requests this device made to an API this month. */
    fun count(api: String): Int = counts.value[api.lowercase()] ?: 0

    fun reset() {
        prefs.edit().clear().putString(KEY_MONTH, currentMonth()).apply()
        _counts.value = emptyMap()
    }

    private fun readCounts(): Map<String, Int> {
        if (prefs.getString(KEY_MONTH, null) != currentMonth()) return emptyMap()
        return prefs.all
            .filterKeys { it != KEY_MONTH }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }
            .toMap()
    }

    private fun currentMonth(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}"
    }

    companion object {
        const val LETSFG = "letsfg"
        const val IGNAV = "ignav"
        const val SERPAPI = "serpapi"
        const val OCTOTRIP = "octotrip"
        const val KIWI = "kiwi"
        const val FLOWEXTRACT = "flowextract"

        /** LetsFG charges a fixed number of credits for one search. */
        const val LETSFG_CREDITS_PER_SEARCH = 32

        private const val KEY_MONTH = "month"
    }
}
