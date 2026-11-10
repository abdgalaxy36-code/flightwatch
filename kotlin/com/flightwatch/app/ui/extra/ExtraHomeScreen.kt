package com.flightwatch.app.ui.extra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.ui.AccentOrange
import com.flightwatch.app.ui.simple.AirlineLogoBadge
import com.flightwatch.app.ui.TimeGreen
import com.flightwatch.app.ui.formatDuration
import com.flightwatch.app.ui.formatPrice

/**
 * Extra Mode: Home screen - matches test UI extra/home.html.
 * Ticket cards + favourites + price history sections.
 */
@Composable
fun ExtraHomeScreen(
    flights: List<FlightCard>,
    onRefresh: () -> Unit,
    onBookClick: (com.flightwatch.app.data.FlightCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Meta row
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("${flights.size} flights · ${flights.size * 3} offers checked", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("Updated 14:32", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", Modifier.size(18.dp)) }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(flights, key = { it.key }) { flight ->
                ExtraTicketCard(flight, onBookClick)
            }
        }
    }
}

@Composable
private fun ExtraTicketCard(
    flight: FlightCard,
    onBookClick: (com.flightwatch.app.data.FlightCard) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirlineLogoBadge(flight.airlineCode, Modifier.size(46.dp, 36.dp))
                Spacer(Modifier.width(12.dp))
                Text(flight.airline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatPrice(flight.offer.price ?: 0.0, flight.offer.currency ?: "SAR"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.clickable { onBookClick(flight) })
                Text("★", fontSize = 22.sp, color = AccentOrange, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column {
                    Text(FlightRules.formatTime(flight.departureTime), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text(flight.offer.origin ?: "", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(flight.durationMinutes), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(if (flight.stops == 0) "Direct" else "${flight.stops} stop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(FlightRules.formatTime(flight.returns.firstOrNull()?.departureTime ?: ""), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text(flight.offer.destination ?: "", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Spacer(Modifier.height(8.dp))
            val ret = flight.returns.firstOrNull()
            if (ret != null) {
                Text(
                    "${ret.flightNumber} · ${FlightRules.formatDay(ret.departureTime)} · ${FlightRules.formatTime(ret.departureTime)} → ${FlightRules.formatTime(ret.arrivalTime)}",
                    fontSize = 13.sp,
                    color = TimeGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
