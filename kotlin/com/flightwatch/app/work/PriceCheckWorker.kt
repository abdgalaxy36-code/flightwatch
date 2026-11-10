package com.flightwatch.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flightwatch.app.FlightWatchApp
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.data.IgnavApi
import com.flightwatch.app.data.IgnavMapper
import com.flightwatch.app.data.LetsfgApi
import com.flightwatch.app.data.SearchResponse
import java.util.concurrent.TimeUnit

/**
 * Periodically re-runs the search and reports price changes.
 *
 * Every price seen is written to the history table. Only a change in the
 * airline's own site price raises a notification.
 */
class PriceCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FlightWatchApp ?: return Result.failure()
        val settings = app.settings
        settings.normalizeDates()
        // Each engine needs its own credential: LetsFG sign-in or an ignav key.
        val usingIgnav = settings.searchSource == "ignav"
        if (usingIgnav) {
            if (!settings.hasIgnavApiKey()) return Result.success()
        } else if (!app.tokenStore.hasCredentials()) {
            return Result.success()
        }

        return try {
            // Do not pay for a search when the saved fares are still current -
            // the traveller may have just refreshed them.
            val key = cacheKey(settings)
            val age = System.currentTimeMillis() - app.searchCache.savedAt(key)
            if (app.searchCache.has(key) && age < settings.checkIntervalHours * 30L * 60L * 1000L) {
                android.util.Log.i("FlightWatch", "price check skipped - saved fares are ${age / 60000} min old")
                return Result.success()
            }

            val response: SearchResponse = if (usingIgnav) {
                val client = app.ignavApi() ?: return Result.success()
                SearchResponse(
                    offers = IgnavMapper.mapToOffers(
                        client.searchRoundTrip(
                            origin = settings.origin,
                            destination = settings.destination,
                            departureDate = settings.outboundDate,
                            returnDate = settings.returnDate,
                            adults = settings.adults,
                            maxStops = 0,
                            market = "SA",
                            departureTimeRange = if (FlightRules.isTimeRuledOrigin(settings.origin)) {
                                IgnavApi.TimeRange(earliest_hour = 16, latest_hour = 23)
                            } else {
                                null
                            },
                        ),
                    ),
                )
            } else {
                app.api.search(
                    LetsfgApi.SearchParams(
                        origin = settings.origin,
                        destination = settings.destination,
                        dateFrom = settings.outboundDate,
                        returnDate = settings.returnDate,
                        adults = settings.adults,
                        children = 0,
                        currency = FlightRules.CURRENCY,
                        cabin = "M",
                        airlineOnly = true,
                    ),
                )
            }
            val offers = response.offers ?: emptyList()
            app.searchCache.save(key, response)
            val cards = FlightRules.group(offers, settings.selectedAirlines)

            val alerts = mutableListOf<String>()

            for (card in cards) {
                val qualifying = card.returns
                if (qualifying.isEmpty()) continue
                // The headline fare is the cheapest return paired with it.
                val cheapest = qualifying.minByOrNull { it.price } ?: continue
                val headlineKey = app.history.ticketKey(
                    origin = settings.origin,
                    destination = settings.destination,
                    outboundKey = card.key,
                    outboundArrival = card.arrivalTime,
                    returnFlightNumber = cheapest.flightNumber,
                    returnDeparture = cheapest.departureTime,
                    returnArrival = cheapest.arrivalTime,
                )
                val previous = app.history.latestFor(headlineKey, officialOnly = true)
                // The lowest price seen for this ticket before this run.
                val lowestBefore = app.history.forFlight(headlineKey).minOfOrNull { it.price }

                // Also record to PriceHistoryStore for Simple Mode charts
                for (ret in qualifying) {
                    val chartKey = "${card.flightNumber}-${ret.flightNumber}"
                    app.priceHistory.record(chartKey, ret.price, ret.currency)
                }
                
                // Update displayed price on flight card
                val now = System.currentTimeMillis()
                val newPrice = cheapest.price
                app.flightsStore.updatePrices(mapOf(card.key to (newPrice to now)))
                android.util.Log.i("FlightWatch", "Price updated: ${card.flightNumber} = $newPrice")
                
                // Record every displayed pairing so each chart stays current.
                for (ret in qualifying) {
                    app.history.record(
                        flightKey = app.history.ticketKey(
                            origin = settings.origin,
                            destination = settings.destination,
                            outboundKey = card.key,
                            outboundArrival = card.arrivalTime,
                            returnFlightNumber = ret.flightNumber,
                            returnDeparture = ret.departureTime,
                            returnArrival = ret.arrivalTime,
                        ),
                        airline = card.airline,
                        flightNumber = card.flightNumber,
                        departureTime = card.departureTime,
                        arrivalTime = card.arrivalTime,
                        returnFlightNumber = ret.flightNumber,
                        returnDepartureTime = ret.departureTime,
                        returnArrivalTime = ret.arrivalTime,
                        origin = settings.origin,
                        destination = settings.destination,
                        price = ret.price,
                        currency = ret.currency,
                        isOfficial = true,
                        seller = card.airline,
                    )
                }

                // Check if this flight is a favourite
                val isFavourite = app.favorites.isFavorite(settings.origin, settings.destination, card.flightNumber)

                if (previous == null) {
                    if (settings.notifyOnNewLow && cheapest.price <= settings.lowPriceThreshold) {
                        alerts += "Low price: ${card.airline} ${card.flightNumber} at " +
                            "${cheapest.price.toLong()} ${cheapest.currency}"
                    }
                    continue
                }
                if (previous.price == cheapest.price) continue

                val isNewLow = lowestBefore != null && cheapest.price < lowestBefore

                if (isNewLow && settings.notifyOnNewLow) {
                    alerts += "New low: ${card.airline} ${card.flightNumber} at " +
                        "${cheapest.price.toLong()} ${cheapest.currency} " +
                        "(was ${previous.price.toLong()})"
                    continue
                }

                val dropped = cheapest.price < previous.price
                if (dropped && !settings.notifyOnPriceDrop) continue
                if (!dropped && !settings.notifyOnPriceRise) continue

                val direction = if (dropped) "dropped" else "rose"
                alerts += "${card.airline} ${card.flightNumber} price $direction to " +
                    "${cheapest.price.toLong()} ${cheapest.currency} " +
                    "(was ${previous.price.toLong()})"

                // Favourite price change notification
                if (isFavourite && settings.notifyFavouriteChanges) {
                    alerts += "⭐ Favourite ${card.airline} ${card.flightNumber} price $direction to " +
                        "${cheapest.price.toLong()} ${cheapest.currency} " +
                        "(was ${previous.price.toLong()})"
                }
            }

            if (alerts.isNotEmpty() && settings.notificationsEnabled) {
                notify(app, alerts)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("FlightWatch", "PriceCheckWorker failed: ${e.message}", e)
            val msg = e.message ?: ""
            val isPermanent = "401" in msg || "402" in msg || "403" in msg || "credit" in msg.lowercase()
            if (isPermanent) Result.failure() else Result.retry()
        }
    }

    private fun cacheKey(settings: com.flightwatch.app.data.SettingsStore) =
        "simple-${settings.searchSource}-${settings.origin}-${settings.destination}-" +
            "${settings.outboundDate}-${settings.returnDate}-${settings.adults}"

    private fun notify(app: FlightWatchApp, alerts: List<String>) {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Price changes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "LetsFG airline fare changes" }
            manager.createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val text = alerts.take(5).joinToString("\n") +
            if (alerts.size > 5) "\n…and ${alerts.size - 5} more" else ""
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Flight price ${if (alerts.size == 1) "change" else "changes"}")
            .setContentText(alerts.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    companion object {
        const val CHANNEL_ID = "price_changes"
        const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "price_check"

        fun schedule(context: Context, everyHours: Int) {
            val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(
                everyHours.coerceAtLeast(1).toLong(),
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
