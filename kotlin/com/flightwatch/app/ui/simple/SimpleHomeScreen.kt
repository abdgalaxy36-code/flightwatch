package com.flightwatch.app.ui.simple

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.ui.AccentOrange
import com.flightwatch.app.ui.TimeGreen
import com.flightwatch.app.ui.formatDuration
import com.flightwatch.app.ui.formatPrice

/**
 * Simple Mode: Home screen - matches test UI simple/home.html exactly.
 * Flight cards with inline Details / History / Chart tabs.
 */
@Composable
fun SimpleHomeScreen(
    flights: List<FlightCard>,
    departure: String,
    arrival: String,
    onSearchClick: () -> Unit,
    onBookClick: (com.flightwatch.app.data.FlightCard) -> Unit,
    onDeleteFlight: (com.flightwatch.app.data.FlightCard) -> Unit = {},
    historyStore: com.flightwatch.app.data.PriceHistoryStore,
    linksSource: String = "api",
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    refreshProgress: String = "",
    onRefresh: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Loading bar at top with real progress
        if (isRefreshing) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(refreshProgress, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (flights.isEmpty()) {
            // Empty state - matches test UI
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No flights selected yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Search for flights to get started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSearchClick) { Text("Search Flights") }
            }
        } else {
            val sortedFlights = flights.sortedBy { it.departureTime }
            val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedFlights, key = { it.key }) { flight ->
                        SimpleFlightCard(flight, departure, arrival, onBookClick, onDeleteFlight, historyStore, linksSource)
                    }
                    item(key = "add-flight") {
                        Button(
                            onClick = onSearchClick,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text("+ Add Flight")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Flight card - matches test UI exactly:
 * - Logo + airline name + flight number + price
 * - Times row: dep | dur/stops | arr
 * - Return info line (white font)
 * - Tabs: Details / History / Chart
 */
@Composable
private fun SimpleFlightCard(
    flight: FlightCard,
    departure: String,
    arrival: String,
    onBookClick: (com.flightwatch.app.data.FlightCard) -> Unit,
    onDeleteFlight: (com.flightwatch.app.data.FlightCard) -> Unit = {},
    historyStore: com.flightwatch.app.data.PriceHistoryStore,
    linksSource: String = "api",
) {
    var expandedTab by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val ret = flight.returns.firstOrNull()

    val context = androidx.compose.ui.platform.LocalContext.current

    if (showDeleteDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Delete flight?", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${flight.airline} ${flight.flightNumber}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteFlight(flight)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // Card-in: padding 10px 12px
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // Logo + airline + price
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AirlineLogoBadge(flight.airlineCode, Modifier.size(46.dp, 36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(flight.airline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(flight.flightNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val pairPrice = ret?.price ?: flight.offer.price ?: 0.0
                    val airlinePrice = flight.currentPrice ?: pairPrice
                    Column(modifier = Modifier.weight(1f)) {

                        Text(formatPrice(airlinePrice, flight.offer.currency ?: "SAR"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentOrange, maxLines = 1, modifier = Modifier.clickable { onBookClick(flight) })
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "\u2715",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showDeleteDialog = true }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Date range: depart .. return
                val retDate = ret?.departureTime?.let { FlightRules.formatDay(it) } ?: ""
                Text(
                    "${FlightRules.formatDay(flight.departureTime)} .. $retDate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                // Times row
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column {
                        Text(FlightRules.formatTime(flight.departureTime), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(2.dp))
                        Text(flight.origin?.ifEmpty { departure } ?: departure, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatDuration(flight.durationMinutes), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(if (flight.stops == 0) "Direct" else "${flight.stops} stop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(FlightRules.formatTime(flight.arrivalTime), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(2.dp))
                        Text(flight.destination?.ifEmpty { arrival } ?: arrival, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                // Return info line - prominent
                Spacer(Modifier.height(8.dp))
                if (ret != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Return", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${ret.flightNumber} · ${FlightRules.formatDay(ret.departureTime)} ${FlightRules.formatTime(ret.departureTime)} → ${FlightRules.formatTime(ret.arrivalTime)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                
                // Booking links (other providers with prices)
                val allLinks = flight.bookingLinks.orEmpty()
                android.util.Log.i("FlightWatch", "UI: flight=${flight.flightNumber} links=${allLinks.size} isAirline=${allLinks.count { it.isAirline }} other=${allLinks.count { !it.isAirline }}")
                if (allLinks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val otherLinks = allLinks.filter { !it.isAirline }.sortedBy { it.price ?: Double.MAX_VALUE }
                    if (otherLinks.isNotEmpty()) {
                        var showAllLinks by remember { mutableStateOf(false) }
                        val displayLinks = if (showAllLinks) otherLinks else otherLinks.take(3)
                        Text("Other booking options:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        displayLinks.forEach { link ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val url = if (link.url.startsWith("http")) link.url else "https://${link.url}"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        context.applicationContext.startActivity(intent)
                                    } catch (e: Exception) {}
                                }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    link.providerName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (link.price != null) {
                                    Text(
                                        "${link.price.toLong()} ${link.currency ?: "SAR"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentOrange,
                                    )
                                }
                            }
                        }
                        if (otherLinks.size > 3) {
                            Text(
                                if (showAllLinks) "Show less" else "+${otherLinks.size - 3} more",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showAllLinks = !showAllLinks }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Tabs: Details / History / Chart
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                listOf("Details", "History", "Chart").forEachIndexed { index, title ->
                    val active = expandedTab == index
                    Box(
                        Modifier.weight(1f)
                            .background(if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { expandedTab = if (active) null else index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(title, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Tab content
            when (expandedTab) {
                0 -> DetailsTabContent(flight, onBookClick)
                1 -> HistoryTabContent(flight)
                2 -> ChartTabContent(flight, historyStore)
                null -> {} // All collapsed
            }
        }
    }
}

@Composable
private fun DetailsTabContent(flight: FlightCard, onBookClick: (FlightCard) -> Unit) {
    val o = flight.offer
    val ret = flight.returns.firstOrNull()
    
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        
        // Flight info
        DetailRow("Airline", flight.airline)
        DetailRow("Airline code", flight.airlineCode)
        DetailRow("Outbound flight", flight.flightNumber)
        if (ret != null) DetailRow("Return flight", ret.flightNumber)
        DetailRow("Route", "${o.origin ?: ""} → ${o.destination ?: ""}")
        DetailRow("Departure", FlightRules.formatDay(flight.departureTime) + " " + FlightRules.formatTime(flight.departureTime))
        DetailRow("Arrival", FlightRules.formatDay(flight.arrivalTime) + " " + FlightRules.formatTime(flight.arrivalTime))
        DetailRow("Duration", formatDuration(flight.durationMinutes))
        DetailRow("Stops", if (flight.stops == 0) "Direct" else "${flight.stops} stop${if (flight.stops == 1) "" else "s"}")
        
        // Return info
        if (ret != null) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DetailRow("Return departure", FlightRules.formatDay(ret.departureTime) + " " + FlightRules.formatTime(ret.departureTime))
            DetailRow("Return arrival", FlightRules.formatDay(ret.arrivalTime) + " " + FlightRules.formatTime(ret.arrivalTime))
            DetailRow("Return duration", formatDuration(ret.durationMinutes))
            DetailRow("Return stops", if (ret.stops == 0) "Direct" else "${ret.stops} stop${if (ret.stops == 1) "" else "s"}")
        }
        
        // Aircraft & baggage
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        flight.segments.firstOrNull()?.aircraft?.let { DetailRow("Aircraft", it) }
        flight.segments.firstOrNull()?.cabin?.let { DetailRow("Cabin", it) }
        flight.conditions?.checkedBag?.let { DetailRow("Checked bag", it) }
        flight.conditions?.carryOn?.let { DetailRow("Carry on", it) }
        flight.conditions?.seat?.let { DetailRow("Seat", it) }
        
        // Seller & fare
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DetailRow("Sold by", flight.sourceLabel ?: "${flight.airline} - its own site")
        DetailRow("Seller type", o.conditions?.sellerKind ?: "-")
        DetailRow("Booking channel", o.conditions?.bookingChannel ?: "-")
        DetailRow("Booking provider", o.conditions?.bookingProvider ?: "-")
        DetailRow("Fare type", o.conditions?.fareType ?: "-")
        DetailRow("Source", o.source ?: "-")
        
        // Refund & change
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DetailRow("Refund before departure", o.conditions?.refundBeforeDeparture ?: "-")
        DetailRow("Change before departure", o.conditions?.changeBeforeDeparture ?: "-")
        DetailRow("Self transfer", o.conditions?.selfTransfer ?: "-")
        DetailRow("Cabin verified", o.conditions?.cabinVerified ?: "-")
        
        // API raw data
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DetailRow("Offer ID", o.id ?: "-")
        DetailRow("Currency", o.currency ?: "-")
        DetailRow("Duration min", o.durationMinutes?.toString() ?: "-")
        DetailRow("Quality", o.quality ?: "-")
        DetailRow("Is combo", o.isCombo?.toString() ?: "-")
        
        // Book row
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth().clickable { onBookClick(flight) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Book", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Exact-flight link", style = MaterialTheme.typography.bodySmall, color = AccentOrange, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HistoryTabContent(flight: FlightCard) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        val ret = flight.returns.firstOrNull()
        val price = flight.currentPrice ?: flight.returns.firstOrNull()?.price ?: flight.offer.price ?: 0.0
        val currency = flight.offer.currency ?: "SAR"
        
        // History card with real flight data
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(flight.airline, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(flight.flightNumber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                            if (ret != null) {
                                Text(" ↩ ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ret.flightNumber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                            }
                        }
                    }
                    Text(formatPrice(price, currency), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(FlightRules.formatDay(flight.departureTime), fontSize = 11.sp, color = TimeGreen)
                    Spacer(Modifier.width(4.dp))
                    Text(FlightRules.formatTime(flight.departureTime), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(" → ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(FlightRules.formatTime(flight.arrivalTime), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    if (ret != null) {
                        Text(" · return ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FlightRules.formatTime(ret.departureTime), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Current: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPrice(price, currency), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                    if (ret != null) {
                        Text(" · Return: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatPrice(ret.price, currency), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Price info", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        DetailRow("Round-trip price", formatPrice(price, currency))
        DetailRow("Price includes", "Outbound + Return tickets")
    }
}

@Composable
private fun ChartTabContent(flight: FlightCard, historyStore: com.flightwatch.app.data.PriceHistoryStore) {
    val ret = flight.returns.firstOrNull()
    val price = flight.currentPrice ?: ret?.price ?: flight.offer.price ?: 0.0
    val currency = flight.offer.currency ?: "SAR"
    
    // Get REAL price history from PriceHistoryStore
    val flightKey = "${flight.flightNumber}-${ret?.flightNumber ?: ""}"
    val rawHistory = historyStore.history(flightKey)
    val processed = PriceHistoryProcessor.process(rawHistory)
    
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text("Price Chart", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        
        // Current price card
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("Round-trip price", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(formatPrice(price, currency), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Spacer(Modifier.height(4.dp))
                Text("${flight.flightNumber} ${if (ret != null) "/ ${ret.flightNumber}" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // REAL data-driven chart
        PriceHistoryChart(data = processed)
    }
}

@Composable
private fun PriceLogRow(date: String, time: String, price: String, isLow: Boolean, isHigh: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Text(date, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(price, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = when {
            isLow -> TimeGreen
            isHigh -> MaterialTheme.colorScheme.error
            else -> AccentOrange
        })
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AirlineLogoBadge(code: String, modifier: Modifier = Modifier) {
    val logoRes = when (code.uppercase()) {
        "SV" -> com.flightwatch.app.R.drawable.logo_saudia
        "TK" -> com.flightwatch.app.R.drawable.logo_turkish
        "XY" -> com.flightwatch.app.R.drawable.logo_flynas
        else -> null
    }
    if (logoRes != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(logoRes),
            contentDescription = code,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    } else {
        val color = when (code.uppercase()) {
            "F3" -> Color(0xFF00897B)
            "EK" -> Color(0xFFD71E28)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val textColor = if (code.uppercase() == "TK") Color.Black else Color.White
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(code.take(2).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}
