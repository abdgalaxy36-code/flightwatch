package com.flightwatch.app

import android.app.Application
import com.flightwatch.app.data.HistoryStore
import com.flightwatch.app.data.FavoritesStore
import com.flightwatch.app.data.LetsfgApi
import com.flightwatch.app.data.OctoTripApi
import com.flightwatch.app.data.OctoTripCache
import com.flightwatch.app.data.SearchCache
import com.flightwatch.app.data.ApiUsageStore
import com.flightwatch.app.data.SerpLinkCache
import com.flightwatch.app.data.SerpApiClient
import com.flightwatch.app.data.IgnavApi
import com.flightwatch.app.data.IgnavLinkCache
import com.flightwatch.app.data.PriceHistoryStore
import com.flightwatch.app.data.SettingsStore
import com.flightwatch.app.work.PriceCheckWorker
import com.flightwatch.app.data.TokenStore

class FlightWatchApp : Application() {

    val tokenStore: TokenStore by lazy { TokenStore(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val history: HistoryStore by lazy { HistoryStore(this) }
    val favorites: FavoritesStore by lazy { FavoritesStore(this) }
    val searchCache: SearchCache by lazy { SearchCache(this) }
    val usage: ApiUsageStore by lazy { ApiUsageStore(this) }
    val api: LetsfgApi by lazy { LetsfgApi(tokenStore, usage) }
    val octoTripApi: OctoTripApi by lazy { OctoTripApi(usage) }
    val octoTripCache: OctoTripCache by lazy { OctoTripCache(this) }
    val serpLinkCache: SerpLinkCache by lazy { SerpLinkCache(this) }
    val ignavLinkCache: IgnavLinkCache by lazy { IgnavLinkCache(this) }
    val priceHistory: PriceHistoryStore by lazy { PriceHistoryStore(this) }
    val flightsStore: com.flightwatch.app.data.SelectedFlightsStore by lazy { com.flightwatch.app.data.SelectedFlightsStore(this) }

    /** Only exists when the traveller has added a SerpApi key. */
    private var serpApiInstance: SerpApiClient? = null
    fun serpApi(): SerpApiClient? =
        if (settings.hasSerpApiKey()) serpApiInstance ?: SerpApiClient(settings.serpApiKey, usage).also { serpApiInstance = it } else null

    /** Only exists when the traveller has added an ignav.com key. */
    fun ignavApi(): IgnavApi? =
        if (settings.hasIgnavApiKey()) IgnavApi(settings.ignavApiKey, usage) else null

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.flightwatch.app.diagnostics.AppLogger.init(this)
        com.flightwatch.app.diagnostics.CrashReporter.init(this)
        com.flightwatch.app.work.PriceCheckWorker.schedule(this, settings.checkIntervalHours)
    }

    companion object {
        lateinit var instance: FlightWatchApp
            private set
    }
}
