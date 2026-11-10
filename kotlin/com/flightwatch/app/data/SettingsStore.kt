package com.flightwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.*
import java.time.LocalDate

/** All user settings live here. Every setting is editable from the Settings page. */
class SettingsStore(context: Context) {

    private val appContext = context
    private val tokenStore = TokenStore(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("flight_watch_settings", Context.MODE_PRIVATE)

    var origin: String
        get() = prefs.getString(KEY_ORIGIN, "JED") ?: "JED"
        set(v) = prefs.edit().putString(KEY_ORIGIN, v).apply()

    var destination: String
        get() = prefs.getString(KEY_DESTINATION, "IST") ?: "IST"
        set(v) = prefs.edit().putString(KEY_DESTINATION, v).apply()

    var outboundDate: String
        get() = prefs.getString(KEY_OUT_DATE, defaultOutbound()) ?: defaultOutbound()
        set(v) = prefs.edit().putString(KEY_OUT_DATE, v).apply()

    var returnDate: String
        get() = prefs.getString(KEY_RET_DATE, defaultReturn()) ?: defaultReturn()
        set(v) = prefs.edit().putString(KEY_RET_DATE, v).apply()

    /**
     * Keep the date pair sane: the return is always the departure plus the
     * configured gap (default 10 days), so a return can never precede the
     * departure (`return_before_departure`) and the gap rule holds for every
     * screen - settings picker, history jump, favourite, background worker.
     * ISO dates compare correctly as strings.
     */
    fun normalizeDates() {
        if (outboundDate.isBlank()) return
        val departure = try {
            LocalDate.parse(outboundDate)
        } catch (e: Exception) {
            return
        }
        val expected = departure.plusDays(tripGapDays.toLong()).toString()
        if (returnDate != expected) returnDate = expected
    }

    /** Days between the departure date and the return date. Always applied. */
    var tripGapDays: Int
        get() = prefs.getInt(KEY_TRIP_GAP, 10).coerceIn(1, 90)
        set(v) = prefs.edit().putInt(KEY_TRIP_GAP, v.coerceIn(1, 90)).apply()

    /**
     * Which engine prices the search: "letsfg" (default, airline-only LetsFG
     * search) or "ignav" (ignav.com round-trip fares with exact-flight links).
     * The value is part of the search-cache key, so each engine keeps its own
     * saved results.
     */
    private val _searchSource = MutableStateFlow(prefs.getString(KEY_SEARCH_SOURCE, "letsfg") ?: "letsfg")
    val searchSourceFlow: StateFlow<String> = _searchSource.asStateFlow()
    var searchSource: String
        get() = _searchSource.value
        set(v) {
            val normalized = if (v == "ignav") "ignav" else "letsfg"
            prefs.edit().putString(KEY_SEARCH_SOURCE, normalized).apply()
            _searchSource.value = normalized
        }

    /**
     * Trip date mode: "exact" (one outbound + one return date),
     * "month" (whole outbound month + whole return month), or
     * "flexible" (depart any day from start for N days, return = start + N).
     */
    var tripMode: String
        get() = prefs.getString(KEY_TRIP_MODE, "exact") ?: "exact"
        set(v) = prefs.edit().putString(KEY_TRIP_MODE, v).apply()

    /** Length of the flexible departure window, in days (inclusive of start). */
    var flexibleDays: Int
        get() = prefs.getInt(KEY_FLEX_DAYS, 10).coerceIn(1, 60)
        set(v) = prefs.edit().putInt(KEY_FLEX_DAYS, v.coerceIn(1, 60)).apply()

    /** Airline names (lowercase) the user wants to see. Empty = all. */
    var selectedAirlines: Set<String>
        get() = prefs.getStringSet(KEY_AIRLINES, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_AIRLINES, v).apply()

    /** Relaxation: allow the outbound to land after midnight. */
    var allowLateArrival: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_NEXT_DAY, false)
        set(v) = prefs.edit().putBoolean(KEY_ALLOW_NEXT_DAY, v).apply()

    /** Master switch for all price notifications. */
    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY, true))
    val notificationsEnabledFlow: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()
    var notificationsEnabled: Boolean
        get() = _notificationsEnabled.value
        set(v) {
            prefs.edit().putBoolean(KEY_NOTIFY, v).apply()
            _notificationsEnabled.value = v
        }

    /** Notify only when the airline's own site price moves. */
    private val _notifyOnlyOfficial = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_OFFICIAL, true))
    val notifyOnlyOfficialFlow: StateFlow<Boolean> = _notifyOnlyOfficial.asStateFlow()
    var notifyOnlyOfficial: Boolean
        get() = _notifyOnlyOfficial.value
        set(v) {
            prefs.edit().putBoolean(KEY_NOTIFY_OFFICIAL, v).apply()
            _notifyOnlyOfficial.value = v
        }

    /** Tell me when a fare gets cheaper than the last time we saw it. */
    private val _notifyOnPriceDrop = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_DROP, true))
    val notifyOnPriceDropFlow: StateFlow<Boolean> = _notifyOnPriceDrop.asStateFlow()
    var notifyOnPriceDrop: Boolean
        get() = _notifyOnPriceDrop.value
        set(v) {
            prefs.edit().putBoolean(KEY_NOTIFY_DROP, v).apply()
            _notifyOnPriceDrop.value = v
        }

    /** Tell me when a fare gets more expensive. */
    private val _notifyOnPriceRise = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_RISE, false))
    val notifyOnPriceRiseFlow: StateFlow<Boolean> = _notifyOnPriceRise.asStateFlow()
    var notifyOnPriceRise: Boolean
        get() = _notifyOnPriceRise.value
        set(v) {
            prefs.edit().putBoolean(KEY_NOTIFY_RISE, v).apply()
            _notifyOnPriceRise.value = v
        }

    /** Tell me when a fare drops below every price seen for that flight before. */
    private val _notifyOnNewLow = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_NEW_LOW, true))
    val notifyOnNewLowFlow: StateFlow<Boolean> = _notifyOnNewLow.asStateFlow()
    var notifyOnNewLow: Boolean
        get() = _notifyOnNewLow.value
        set(v) {
            prefs.edit().putBoolean(KEY_NOTIFY_NEW_LOW, v).apply()
            _notifyOnNewLow.value = v
        }

    /** Notify when a favourite flight's price changes. */
    private val _notifyFavouriteChanges = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_FAVOURITES, true))
    val notifyFavouriteChangesFlow: StateFlow<Boolean> = _notifyFavouriteChanges.asStateFlow()
    var notifyFavouriteChanges: Boolean
        get() = _notifyFavouriteChanges.value
        set(v) {
            prefs.edit().putBoolean(KEY_NOTIFY_FAVOURITES, v).apply()
            _notifyFavouriteChanges.value = v
        }

    /** A fare at or below this many riyals counts as a low price. */
    private val _lowPriceThreshold = MutableStateFlow(
        Double.fromBits(prefs.getLong(KEY_LOW_THRESHOLD, 1500.0.toRawBits()))
    )
    val lowPriceThresholdFlow: StateFlow<Double> = _lowPriceThreshold.asStateFlow()
    var lowPriceThreshold: Double
        get() = _lowPriceThreshold.value
        set(v) {
            prefs.edit().putLong(KEY_LOW_THRESHOLD, v.toRawBits()).apply()
            _lowPriceThreshold.value = v
        }

    /**
     * Background price-check interval, in hours. Exposed as a flow so the
     * Settings screen recomposes when the user picks a new interval.
     */
    private val _checkIntervalHours = MutableStateFlow(prefs.getInt(KEY_INTERVAL, 24))
    val checkIntervalHoursFlow: StateFlow<Int> = _checkIntervalHours.asStateFlow()

    var checkIntervalHours: Int
        get() = prefs.getInt(KEY_INTERVAL, 24)
        set(value) {
            val v = value.coerceIn(12, 72)
            prefs.edit().putInt(KEY_INTERVAL, v).apply()
            _checkIntervalHours.value = v
        }

    fun applyCheckInterval(hours: Int) {
        checkIntervalHours = hours
        com.flightwatch.app.work.PriceCheckWorker.schedule(context, hours)
    }

    var adults: Int
        get() = prefs.getInt(KEY_ADULTS, 1)
        set(v) = prefs.edit().putInt(KEY_ADULTS, v.coerceIn(1, 9)).apply()

    /**
     * Application context for scheduling work.
     */
    val context: Context
        get() = appContext

    /**
     * Departure airports the traveller wants swipeable pages for. Only the
     * departure airport gets a page; the arrival airport is shared and never
     * gets its own page.
     *
     * Backed by a flow so the main screen's airport tabs appear as soon as a
     * code is added in Settings.
     */
    private val _departureAirports = MutableStateFlow(readDepartureAirports())
    val departureAirportsFlow: StateFlow<List<String>> = _departureAirports.asStateFlow()

    var departureAirports: List<String>
        get() = _departureAirports.value
        set(v) {
            val normalized = v.map { it.uppercase() }.filter { it.isNotBlank() }.distinct()
            if (normalized.isEmpty()) return
            prefs.edit().putString(KEY_DEPARTURE_AIRPORTS, normalized.joinToString(",")).apply()
            _departureAirports.value = normalized
        }

    private fun readDepartureAirports(): List<String> =
        prefs
            .getString(KEY_DEPARTURE_AIRPORTS, "JED")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { it.uppercase() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("JED")

    fun addDepartureAirport(code: String) {
        val normalized = code.uppercase().trim()
        if (normalized.isBlank() || normalized.length !in 2..3) return
        val current = departureAirports.toMutableList()
        if (!current.contains(normalized)) {
            departureAirports = current + normalized
        }
    }

    fun removeDepartureAirport(code: String) {
        val normalized = code.uppercase()
        if (departureAirports.size > 1) {
            departureAirports = departureAirports.filter { it != normalized }
        }
    }

    var sortByPrice: Boolean
        get() = prefs.getBoolean(KEY_SORT_PRICE, true)
        set(v) = prefs.edit().putBoolean(KEY_SORT_PRICE, v).apply()

    /** Show only flights whose return leaves after 7 PM (instead of after 5 PM). */
    var returnAfter7pm: Boolean
        get() = prefs.getBoolean(KEY_RETURN_7PM, false)
        set(v) = prefs.edit().putBoolean(KEY_RETURN_7PM, v).apply()

    /**
     * Optional ignav.com API key.
     *
     * ignav.com provides round-trip searches with exact-flight booking links via ignav_id.
     * Free tier: 1,000 successful requests/month, no rate limit.
     */
    private val _ignavApiKey = MutableStateFlow(prefs.getString(KEY_IGNAV_KEY, "") ?: "")
    val ignavApiKeyFlow: StateFlow<String> = _ignavApiKey.asStateFlow()

    var ignavApiKey: String
        get() = _ignavApiKey.value
        set(v) {
            val trimmed = v.trim()
            _ignavApiKey.value = trimmed
            prefs.edit().putString(KEY_IGNAV_KEY, trimmed).apply()
        }

    fun hasIgnavApiKey(): Boolean = _ignavApiKey.value.isNotBlank()

    fun hasLetsfgCredentials(): Boolean = tokenStore.hasCredentials()

    /**
     * Optional SerpApi key.
     *
     * With a key the app also asks Google Flights, which covers many more
     * airlines and can resolve a booking link for one exact flight. Without a
     * key nothing changes.
     */
    private val _serpApiKey = MutableStateFlow(prefs.getString(KEY_SERP_KEY, "") ?: "")
    val serpApiKeyFlow: StateFlow<String> = _serpApiKey.asStateFlow()

    var serpApiKey: String
        get() = _serpApiKey.value
        set(v) {
            val trimmed = v.trim()
            _serpApiKey.value = trimmed
            prefs.edit().putString(KEY_SERP_KEY, trimmed).apply()
        }

    fun hasSerpApiKey(): Boolean = _serpApiKey.value.isNotBlank()

    /**
     * Which sources the app uses to resolve per-flight booking links.
     * Options: "serpapi" (Google Flights booking links), "octotrip" (MCP backup),
     * "ignav" (ignav.com round-trip with exact-flight links), "kiwi" (Kiwi Tequila deep links), "flowextract" (Apify).
     * Multiple can be enabled at once - all are queried.
     */
    private val _linksSources = MutableStateFlow(
        prefs.getStringSet(KEY_LINKS_SOURCES, setOf("ignav")) ?: setOf("ignav")
    )
    val linksSourcesFlow: StateFlow<Set<String>> = _linksSources.asStateFlow()

    var linksSources: Set<String>
        get() = _linksSources.value
        set(v) {
            _linksSources.value = v
            prefs.edit().putStringSet(KEY_LINKS_SOURCES, v).apply()
        }

    /**
     * Theme mode: "system", "light" or "dark".
     */
    /**
     * Theme mode: "system", "light" or "dark". Exposed as a flow so the theme
     * recomposes when the user switches it.
     */
    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME, "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode).apply()
    }

    fun allAirlinesSeen(): Set<String> = prefs.getStringSet(KEY_KNOWN_AIRLINES, emptySet()) ?: emptySet()

    fun rememberAirlines(names: Set<String>) {
        val merged = allAirlinesSeen() + names.map { it.lowercase() }
        prefs.edit().putStringSet(KEY_KNOWN_AIRLINES, merged).apply()
    }

    fun reset() = prefs.edit().clear().apply()

    private fun defaultOutbound(): String = LocalDate.now().plusDays(30).toString()
    private fun defaultReturn(): String = LocalDate.now().plusDays(37).toString()

    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)
        set(v) = prefs.edit().putString(KEY_GITHUB_TOKEN, v).apply()

    var githubGistId: String?
        get() = prefs.getString(KEY_GITHUB_GIST_ID, null)
        set(v) = prefs.edit().putString(KEY_GITHUB_GIST_ID, v).apply()

    companion object {
        private const val KEY_ORIGIN = "origin"
        private const val KEY_DESTINATION = "destination"
        private const val KEY_OUT_DATE = "outbound_date"
        private const val KEY_RET_DATE = "return_date"
        private const val KEY_TRIP_MODE = "trip_mode"
        private const val KEY_FLEX_DAYS = "flex_days"
        private const val KEY_AIRLINES = "airlines"
        private const val KEY_ALLOW_NEXT_DAY = "allow_next_day"
        private const val KEY_NOTIFY = "notifications"
        private const val KEY_NOTIFY_OFFICIAL = "notify_official_only"
        private const val KEY_NOTIFY_DROP = "notify_drop"
        private const val KEY_NOTIFY_RISE = "notify_rise"
        private const val KEY_NOTIFY_NEW_LOW = "notify_new_low"
        private const val KEY_NOTIFY_FAVOURITES = "notify_favourites"
        private const val KEY_LOW_THRESHOLD = "low_threshold"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_ADULTS = "adults"
        private const val KEY_SORT_PRICE = "sort_by_price"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GITHUB_GIST_ID = "github_gist_id"
        private const val KEY_RETURN_7PM = "return_after_7pm"
        private const val KEY_KNOWN_AIRLINES = "known_airlines"
        private const val KEY_SERP_KEY = "serp_api_key"
        private const val KEY_IGNAV_KEY = "ignav_api_key"
        private const val KEY_LINKS_SOURCES = "links_sources"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DEPARTURE_AIRPORTS = "departure_airports"
        private const val KEY_TRIP_GAP = "trip_gap_days"
        private const val KEY_SEARCH_SOURCE = "search_source"
    }
}
