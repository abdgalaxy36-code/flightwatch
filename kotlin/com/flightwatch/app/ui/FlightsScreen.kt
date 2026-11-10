package com.flightwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightwatch.app.R
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FavoritesStore
import com.flightwatch.app.data.FavoriteFlight
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.data.PricePoint
import com.flightwatch.app.data.ReturnCutoff
import com.flightwatch.app.data.ReturnLeg
import com.flightwatch.app.data.AirlineSites
import com.flightwatch.app.data.BookingLinks
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

// Brand accent (works on both light and dark backgrounds).
internal val AccentOrange = Color(0xFFE8A33D)  // Theme.Gold - opencode orange
internal val TimeGreen = Color(0xFF22C55E)      // better green for times/dates

/** One complete round-trip ticket: outbound + one specific return leg. */
data class RoundTripTicket(
    val outbound: FlightCard,
    val returnLeg: ReturnLeg,
    val totalPrice: Double,
    val currency: String,
)

@Composable
fun FlightsTab(
    vm: MainViewModel,
    state: FlightsUiState,
    page: FlightPageConfig,
    modifier: Modifier = Modifier,
) {
    if (state.loading && state.sections.isEmpty()) {
        LoadingState(state.note, modifier)
        return
    }
    if (state.error != null && state.sections.isEmpty()) {
        ErrorState(state.error, vm, modifier)
        return
    }

    var historyOpen by remember { mutableStateOf(false) }
    var favoritesOpen by remember { mutableStateOf(false) }
    val history by vm.history.collectAsStateWithLifecycle(emptyList())
    val favorites by vm.favorites.collectAsStateWithLifecycle(emptyList())
    val pageFavorites = favorites.filter {
        it.origin.equals(page.origin, true) && it.destination.equals(page.destination, true)
    }

    val cutoff = state.sections.firstOrNull()?.cutoff
        ?: ReturnCutoff("Return after 6 PM", LocalTime.of(18, 0))

    // Build one item per (outbound flight, qualifying return leg) pair.
    // Each item is a complete round-trip ticket with its own total price.
    val tickets = state.sections.flatMap { it.flights }
        .distinctBy { it.key }
        .flatMap { card ->
            card.returns.filter { FlightRules.qualifies(it, cutoff) }
                .map { ret ->
                    RoundTripTicket(
                        outbound = card,
                        returnLeg = ret,
                        totalPrice = ret.price,
                        currency = ret.currency,
                    )
                }
        }
        .sortedBy { it.totalPrice }

    // History filtered for current airport page
    val pageHistory = history.filter {
        it.origin.equals(page.origin, true) && it.destination.equals(page.destination, true)
    }
    // One series per complete ticket - never mix different flights in a chart.
    val pageHistoryGroups = remember(pageHistory) { groupHistory(pageHistory) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MetaRow(
            flightCount = tickets.size,
            offersChecked = state.rawCount,
            updatedAt = state.updatedAt,
            loading = state.loading,
            onRefresh = { vm.search(force = true) },
        )

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.search(force = true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.loading) {
                    item(key = "loading") {
                        Column {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth(),
                                color = AccentOrange,
                                trackColor = MaterialTheme.colorScheme.outline,
                            )
                            if (state.note.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    state.note,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (state.error != null) {
                    item(key = "error") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                null,
                                Modifier.size(14.dp),
                                tint = Color(0xFFC62828),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                state.error,
                                fontSize = 12.sp,
                                color = Color(0xFFC62828),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (tickets.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "No flights match your rules.",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (FlightRules.isTimeRuledOrigin(page.origin)) {
                                    "Direct only, departing after 4 PM, landing by 11 PM, " +
                                        "with a return after 6 PM."
                                } else {
                                    "Direct flights only, any departure time, " +
                                        "with a return after 6 PM."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(items = tickets, key = { "${it.outbound.key}|${it.returnLeg.key}" }) { ticket ->
                    RoundTripCard(vm, ticket, page)
                }

                // ── Favourites (Ruh section style) ─────────────────────
                item(key = "fav-header") {
                    RuhSectionHeader(
                        title = "FAVOURITES",
                        count = pageFavorites.size,
                        open = favoritesOpen,
                        accent = AccentOrange,
                        onToggle = { favoritesOpen = !favoritesOpen },
                    )
                }
                if (favoritesOpen) {
                    if (pageFavorites.isEmpty()) {
                        item(key = "fav-empty") {
                            Text(
                                "Tap ☆ on a flight to keep it here.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    } else {
                        items(pageFavorites, key = { "fav-${it.flightNumber}-${it.savedAt}" }) { fav ->
                            FavoriteRow(
                                fav = fav,
                                onRemove = {
                                    vm.removeFavorite(fav.origin, fav.destination, fav.flightNumber)
                                },
                                onClick = {
                                    vm.settings.origin = fav.origin
                                    vm.settings.destination = fav.destination
                                    if (fav.departureTime.isNotBlank()) {
                                        vm.settings.outboundDate =
                                            fav.departureTime.substringBefore('T').take(10)
                                    }
                                    vm.search(force = true)
                                },
                            )
                        }
                    }
                }

                // ── Price history (Ruh section style) ──────────────────
                item(key = "hist-header") {
                    RuhSectionHeader(
                        title = "PRICE HISTORY",
                        count = pageHistoryGroups.size,
                        open = historyOpen,
                        accent = MaterialTheme.colorScheme.primary,
                        onToggle = { historyOpen = !historyOpen },
                    )
                }
                if (historyOpen) {
                    if (pageHistoryGroups.isEmpty()) {
                        item(key = "hist-empty") {
                            Text(
                                "No prices recorded for this route yet.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    } else {
                        items(pageHistoryGroups, key = { "h-${it.first().flightKey}" }) { group ->
                            HistoryChartCard(group, onClick = {
                                val point = group.first()
                                vm.settings.origin = point.origin
                                vm.settings.destination = point.destination
                                vm.settings.outboundDate = point.departureTime.substringBefore('T')
                                vm.settings.returnAfter7pm = false
                                vm.search(force = true)
                            })
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- meta row

@Composable
private fun MetaRow(
    flightCount: Int,
    offersChecked: Int,
    updatedAt: Long,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Flight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            "$flightCount flight${if (flightCount == 1) "" else "s"} · $offersChecked offers checked",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (updatedAt > 0L) {
            val locale = LocalLocale.current.platformLocale
            Text(
                "Updated " + SimpleDateFormat("HH:mm", locale).format(Date(updatedAt)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Filled.Refresh, "Refresh fares", Modifier.size(18.dp))
        }
    }
}

// ------------------------------------------- Ruh-style list pieces only

/** Ruh section header: bold title · count with a ▸/▾ chevron. */
@Composable
private fun RuhSectionHeader(
    title: String,
    count: Int,
    open: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count > 0) "$title · $count" else title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (open) "▾" else "▸",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ruh list row for a favourite flight. */
@Composable
private fun FavoriteRow(
    fav: com.flightwatch.app.data.FavoriteFlight,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append(fav.airline.ifBlank { fav.airlineCode })
                    if (fav.flightNumber.isNotBlank()) append(" ${fav.flightNumber}")
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(fav.origin)
                    append(" → ")
                    append(fav.destination)
                    if (fav.departureTime.isNotBlank()) {
                        append(" · ")
                        append(fav.departureTime.substringBefore('T').take(10))
                        val t = fav.departureTime.substringAfter('T', "").take(5)
                        if (t.isNotBlank()) append(" $t")
                    }
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatPrice(fav.price, fav.currency),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AccentOrange,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            "☆",
            fontSize = 20.sp,
            color = AccentOrange,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(4.dp),
        )
    }
}

/** Full-width section row: TITLE · N  with a ▸/▾ chevron. */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    accent: Color,
    onClick: () -> Unit,
    showCount: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (showCount && count >= 0) "$title · $count" else title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (collapsed) "▸" else "▾",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
// ------------------------------------------------------------------- card

/**
 * One flight. Shows the complete round-trip ticket with total price.
 * Collapsed: airline, total price, outbound & return times.
 * One complete round-trip ticket (outbound + one return leg) as its own card.
 * Collapsed: airline, total price, outbound & return times.
 * Expanded: details + book button (exact-flight link from chosen API).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundTripCard(
    vm: MainViewModel,
    ticket: RoundTripTicket,
    page: FlightPageConfig,
) {
    var expanded by remember { mutableStateOf(false) }
    var apiDataOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    val card = ticket.outbound
    val ret = ticket.returnLeg
    val totalPrice = ticket.totalPrice
    val currency = ticket.currency

    val favOn = favorites.any {
        it.origin.equals(page.origin, true) &&
            it.destination.equals(page.destination, true) &&
            it.flightNumber.uppercase().replace(Regex("[^A-Z0-9]"), "") ==
            card.flightNumber.uppercase().replace(Regex("[^A-Z0-9]"), "")
    }

    fun openBooking() {
        // Use the exact-flight link from the chosen API (SerpApi/OctoTrip).
        // No fallbacks, no constructed URLs.
        val url = ret.bookingUrl ?: card.bookingUrl
        if (url != null) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                android.widget.Toast
                    .makeText(context, "No browser found to open the link", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            android.widget.Toast
                .makeText(
                    context,
                    "No booking link for this exact flight. Select a links source (SerpApi/OctoTrip/ignav) in Settings.",
                    android.widget.Toast.LENGTH_LONG,
                )
                .show()
        }
    }

    Card(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {

            // ---- airline, total price, favorite
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirlineLogo(card.airlineCode, Modifier.width(46.dp).height(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    card.airline,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPrice(totalPrice, currency),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange,
                    maxLines = 1,
                    modifier = Modifier.clickable { openBooking() },
                )
                Text(
                    if (favOn) "★" else "☆",
                    fontSize = 22.sp,
                    color = if (favOn) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            vm.toggleFavorite(
                                card,
                                totalPrice,
                                currency,
                            )
                        },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- outbound and return times
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Outbound column
                Column {
                    Text(
                        FlightRules.formatTime(card.departureTime),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(page.origin, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                // Center: duration, stops
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 12.dp, top = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        formatDuration(card.durationMinutes + ret.durationMinutes),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (card.stops == 0 && ret.stops == 0) "Direct" else
                            "${card.stops + ret.stops} stop${if (card.stops + ret.stops == 1) "" else "s"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Return column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        FlightRules.formatTime(ret.departureTime),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(page.destination, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            // ---- return flight info line
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${ret.flightNumber} · ${FlightRules.formatDay(ret.departureTime)} · ${FlightRules.formatTime(ret.departureTime)} → ${FlightRules.formatTime(ret.arrivalTime)}",
                    fontSize = 13.sp,
                    color = TimeGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPrice(ret.price, ret.currency),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            // ---- expand chevron
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${card.flightNumber} / ${ret.flightNumber}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "Less" else "Details",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // ---- expanded details
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))

                DetailLine("Duration", formatDuration(card.durationMinutes + ret.durationMinutes))
                DetailLine("Stops", if (card.stops == 0 && ret.stops == 0) "Direct" else "${card.stops + ret.stops}")
                card.segments.firstOrNull()?.aircraft?.let { DetailLine("Aircraft", it) }
                card.conditions?.checkedBag?.let { DetailLine("Checked bag", it) }
                card.conditions?.carryOn?.let { DetailLine("Carry on", it) }
                DetailLine("Sold by", card.sourceLabel ?: "${card.airline} - its own site")
                val linkTarget = ret.bookingSeller ?: card.bookingSeller
                val linkPrice = ret.bookingPrice ?: card.bookingPrice
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openBooking() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Book",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        linkTarget?.takeIf { it != "Google Flights" } ?: "Exact-flight link",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.4f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        "Open booking link",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (linkTarget != null && linkPrice != null) {
                    DetailLine(
                        "Price there",
                        formatPrice(linkPrice, FlightRules.CURRENCY) + " on " + linkTarget,
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { apiDataOpen = !apiDataOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (apiDataOpen) "Hide API data" else "Show API data (raw)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (apiDataOpen) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Toggle API data",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (apiDataOpen) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        val o = card.offer
                        DetailLine("Offer id", o.id ?: "-")
                        DetailLine("Price", o.price?.let { formatPrice(it, o.currency ?: FlightRules.CURRENCY) } ?: "-")
                        DetailLine("Airline", o.airline ?: "-")
                        DetailLine("Airline code", o.airlineCode ?: "-")
                        DetailLine("Origin", o.origin ?: "-")
                        DetailLine("Origin name", o.originName ?: "-")
                        DetailLine("Destination", o.destination ?: "-")
                        DetailLine("Destination name", o.destinationName ?: "-")
                        DetailLine("Departure time", o.departureTime ?: "-")
                        DetailLine("Arrival time", o.arrivalTime ?: "-")
                        DetailLine("Duration minutes", o.durationMinutes?.toString() ?: "-")
                        DetailLine("Stops", o.stops?.toString() ?: "-")
                        DetailLine("Flight number", o.flightNumber ?: "-")
                        DetailLine("Source", o.source ?: "-")
                        DetailLine("Is combo", o.isCombo?.toString() ?: "-")
                        DetailLine("Quality", o.quality ?: "-")
                        DetailLine("Suspect reason", o.suspectReason ?: "-")
                        DetailLine("Long layover hours", o.longLayoverHours?.toString() ?: "-")
                        DetailLine("Seller kind", o.conditions?.sellerKind ?: "-")
                        DetailLine("Booking channel", o.conditions?.bookingChannel ?: "-")
                        DetailLine("Booking provider", o.conditions?.bookingProvider ?: "-")
                        DetailLine("Self transfer", o.conditions?.selfTransfer?.toString() ?: "-")
                        DetailLine("Fare type", o.conditions?.fareType ?: "-")
                        DetailLine("Refund before departure", o.conditions?.refundBeforeDeparture ?: "-")
                        DetailLine("Change before departure", o.conditions?.changeBeforeDeparture ?: "-")
                        DetailLine("Carry on", o.conditions?.carryOn ?: "-")
                        DetailLine("Checked bag", o.conditions?.checkedBag ?: "-")
                        DetailLine("Seat", o.conditions?.seat ?: "-")
                        DetailLine("Cabin verified", o.conditions?.cabinVerified?.toString() ?: "-")
                    }
                }
            }
        }
    }
}

/** The airline's mark; a plain code badge when we do not have one. */
@Composable
private fun AirlineLogo(airlineCode: String, modifier: Modifier = Modifier) {
    when (airlineCode.uppercase(Locale.ROOT)) {
        "SV" -> Image(
            painter = painterResource(R.drawable.logo_saudia),
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        "F3" -> Image(
            painter = painterResource(R.drawable.logo_saudia),
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        "TK" -> Image(
            painter = painterResource(R.drawable.logo_turkish),
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        "XY" -> Image(
            painter = painterResource(R.drawable.logo_flynas),
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        else -> Box(
            modifier.background(brandColor(airlineCode), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                airlineCode.take(2).uppercase(Locale.ROOT),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/** The airline's own colour, used for the code badge. */
@Composable
private fun brandColor(airlineCode: String): Color = when (airlineCode.uppercase(Locale.ROOT)) {
    "EK" -> Color(0xFFD71E28)
    "MS" -> Color(0xFF0A4B8C)
    "GA" -> Color(0xFFB08D57)
    "A3" -> Color(0xFF0A4B8C)
    "G9" -> Color(0xFF1E6E52)
    "PC" -> Color(0xFFE30613)
    else -> MaterialTheme.colorScheme.surfaceVariant
}

// -------------------------------------------------------- sort and filter

@Composable
private fun SortFilterButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sort & Filter", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The sheet behind the Sort & Filter button and the top-right icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterSheet(vm: MainViewModel, state: FlightsUiState, onDismiss: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val historyGroups = remember(history) { groupHistory(history) }
    var historyOpen by remember { mutableStateOf(false) }
    var lateArrival by remember { mutableStateOf(vm.settings.allowLateArrival) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Sort & Filter",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            SheetLabel("Sort")
            SheetOption(
                "Cheapest first",
                selected = state.sortByPrice,
                onClick = { vm.setSortByPrice(true) },
            )
            SheetOption(
                "Earliest departure",
                selected = !state.sortByPrice,
                onClick = { vm.setSortByPrice(false) },
            )

            Spacer(Modifier.height(8.dp))
            SheetLabel("Filters")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Allow arrival after midnight",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = lateArrival,
                    onCheckedChange = {
                        lateArrival = it
                        vm.setAllowLateArrival(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentOrange,
                        checkedThumbColor = Color.White,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            SheetLabel("Price history")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${historyGroups.size} flights · ${history.size} prices recorded",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { historyOpen = !historyOpen }) {
                        Text(if (historyOpen) "Hide" else "Show")
                    }
                }
            }
            if (historyOpen) {
                historyGroups.forEach { group -> HistoryChartCard(group) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 1.dp),
    )
}

@Composable
private fun SheetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AccentOrange),
        )
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun ReturnLine(
    leg: ReturnLeg,
    highlighted: Boolean,
    cutoff: com.flightwatch.app.data.ReturnCutoff,
    onClick: () -> Unit,
) {
    val qualifies = FlightRules.qualifies(leg, cutoff)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlighted) Color(0xFFE4EEFB)
                else Color.Transparent,
            )
            .clickable(enabled = qualifies, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${FlightRules.formatDay(leg.departureTime)} · " +
                    "${FlightRules.formatTime(leg.departureTime)} → " +
                    FlightRules.formatTime(leg.arrivalTime),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "${leg.flightNumber} · ${leg.stops} stop" + if (leg.stops == 1) "" else "s" +
                    if (qualifies) "" else " · before ${cutoff.label.removePrefix("Return after ")}",
                style = MaterialTheme.typography.labelSmall,
                color = if (qualifies) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828),
            )
        }
        Text(
            formatPrice(leg.price, leg.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (qualifies) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Group price points into one series per complete ticket (outbound + return). */
private fun groupHistory(points: List<PricePoint>): List<List<PricePoint>> =
    points.groupBy { it.flightKey }
        .values
        .map { series -> series.sortedBy { it.recordedAt } }
        .sortedByDescending { it.last().recordedAt }

/**
 * One chart per complete ticket: the same flight (outbound + return) seen at
 * different prices over time. Different flights never share a chart.
 */
@Composable
private fun HistoryChartCard(group: List<PricePoint>, onClick: (() -> Unit)? = null) {
    val first = group.first()
    val last = group.last()
    val prices = group.map { it.price }
    val low = prices.minOrNull() ?: 0.0
    val high = prices.maxOrNull() ?: 0.0

    Card(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (onClick != null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.background
            },
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Airline + both flight numbers + route.
            val title = buildString {
                val airline = first.airline.ifBlank { first.seller }.ifBlank { "Flight" }
                append(airline)
                if (first.flightNumber.isNotBlank()) {
                    append(' ')
                    append(first.flightNumber)
                }
                if (first.returnFlightNumber.isNotBlank()) {
                    append(" ↩ ")
                    append(first.returnFlightNumber)
                }
                if (first.origin.isNotBlank() && first.destination.isNotBlank()) {
                    append(" · ")
                    append(first.origin)
                    append('→')
                    append(first.destination)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPrice(last.price, last.currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentOrange,
                )
            }

            // Outbound and return times (airport-local, as reported).
            val when_ = buildString {
                val day = FlightRules.formatDay(first.departureTime)
                if (day.isNotBlank()) {
                    append(day)
                    append(' ')
                }
                val dep = FlightRules.formatTime(first.departureTime)
                if (dep != "--:--") append(dep)
                val arr = FlightRules.formatTime(first.arrivalTime)
                if (arr != "--:--") {
                    append(" → ")
                    append(arr)
                }
                if (first.returnFlightNumber.isNotBlank()) {
                    append(" · return ")
                    val rd = FlightRules.formatDay(first.returnDepartureTime)
                    if (rd.isNotBlank()) {
                        append(rd)
                        append(' ')
                    }
                    val rt = FlightRules.formatTime(first.returnDepartureTime)
                    if (rt != "--:--") append(rt)
                }
            }
            Text(
                when_,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            // The price line: one dot per recorded price.
            val lineColor = AccentOrange
            val dotCore = MaterialTheme.colorScheme.outline
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                if (group.isEmpty()) return@Canvas
                val w = size.width
                val h = size.height
                val padY = h * 0.18f
                val minP = low.toFloat()
                val range = high.toFloat() - minP
                val pts = group.mapIndexed { i, p ->
                    val x = if (group.size > 1) i * (w / (group.size - 1)) else w / 2f
                    val y = if (range <= 0f) h / 2f else {
                        val norm = (p.price.toFloat() - minP) / range
                        h - padY - norm * (h - 2 * padY)
                    }
                    Offset(x, y)
                }

                if (pts.size > 1) {
                    val line = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(pts.last().x, h)
                        lineTo(pts.first().x, h)
                        close()
                    }
                    drawPath(area, color = lineColor.copy(alpha = 0.12f))
                    drawPath(
                        line,
                        color = lineColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                pts.forEach { c ->
                    drawCircle(color = lineColor, radius = 3.dp.toPx(), center = c)
                    drawCircle(color = dotCore, radius = 1.2.dp.toPx(), center = c)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Range, how many prices were seen, and when.
            val stamp = remember(last.recordedAt) {
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(last.recordedAt))
            }
            val stats = buildString {
                append("Low ")
                append(formatPrice(low, first.currency))
                append(" · High ")
                append(formatPrice(high, first.currency))
                append(" · ")
                append(group.size)
                append(if (group.size == 1) " price" else " prices")
                append(" · last ")
                append(stamp)
            }
            Text(
                stats,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingState(note: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentOrange)
            Spacer(Modifier.height(16.dp))
            Text(
                note.ifBlank { "Searching live fares…" },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Each airline prices its own fares, so this takes 2–3 minutes.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, vm: MainViewModel, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Warning, null, Modifier.size(44.dp), tint = Color(0xFFC62828))
            Spacer(Modifier.height(12.dp))
            Text("Search failed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.search(force = true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = Color.White,
                ),
            ) { Text("Try again") }
        }
    }
}

@Composable
private fun EmptyState(state: FlightsUiState, vm: MainViewModel) {
    val f = state.funnel
    val origin = vm.settings.origin
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Icon(Icons.Filled.Info, null, Modifier.size(26.dp), tint = AccentOrange)
        Spacer(Modifier.height(8.dp))
        Text("No flights match your rules", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            if (FlightRules.isTimeRuledOrigin(origin)) {
                "Direct only, leaving after 4 PM, landing the same day by 11 PM, " +
                    "with a return after 6 PM."
            } else {
                "Direct flights only, any departure time, with a return after 6 PM."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (f != null && FlightRules.isTimeRuledOrigin(origin)) {
            Spacer(Modifier.height(10.dp))
            FunnelRow("Direct only", f.direct, f.total)
            FunnelRow("Leaves after 4 PM", f.afterDepartureTime, f.total)
            FunnelRow("Lands that night", f.afterSameDayArrival, f.total)
            FunnelRow("Lands by 11 PM / 2 AM", f.afterArrivalWindow, f.total)
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = { vm.setAllowLateArrival(!vm.settings.allowLateArrival) },
        ) {
            Text(
                if (vm.settings.allowLateArrival) "Same-day arrival only"
                else "Allow arrival after midnight",
            )
        }
    }
}

@Composable
private fun FunnelRow(label: String, kept: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            "$kept / $total",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (kept == 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onBackground,
        )
    }
}

internal fun formatPrice(price: Double, currency: String): String {
    val amount = if (price % 1.0 == 0.0) {
        String.format(Locale.US, "%,d", price.toLong())
    } else {
        String.format(Locale.US, "%,.2f", price)
    }
    return "$amount $currency"
}

internal fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
