package com.flightwatch.app.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightwatch.app.R
import com.flightwatch.app.data.FlightCard
import com.flightwatch.app.data.FlightRules
import com.flightwatch.app.ui.AccentOrange
import com.flightwatch.app.ui.formatDuration
import com.flightwatch.app.ui.formatPrice

/**
 * Simple Mode: Search screen - matches test UI simple/search.html exactly.
 * Search form + results with checkboxes + Add to My Flights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSearchScreen(
    onSearch: suspend (String, String, String, String, Int, com.flightwatch.app.data.SearchFilters, (String) -> Unit) -> List<FlightCard>,
    onBookClick: (FlightCard) -> Unit = {},
    onAddSelected: (List<FlightCard>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var departure by remember { mutableStateOf("JED") }
    var arrival by remember { mutableStateOf("IST") }
    var departDate by remember { mutableStateOf("2026-12-08") }
    var returnDate by remember { mutableStateOf("2026-12-18") }
    var results by remember { mutableStateOf<List<FlightCard>>(emptyList()) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var searched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progressMsg by remember { mutableStateOf("Starting search...") }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showDateRangePicker by remember { mutableStateOf(false) }
    var maxStops by remember { mutableStateOf<Int?>(null) } // null=any, 0=direct, 1=1 stop
    var departureTimeFrom by remember { mutableStateOf<Int?>(null) }
    var departureTimeTo by remember { mutableStateOf<Int?>(null) }
    var arrivalTimeFrom by remember { mutableStateOf<Int?>(null) }
    var arrivalTimeTo by remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search form
            item(key = "form") {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Search Flights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Enter your route and dates to find flights.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))

                        Text("Departure airport (IATA)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = departure,
                            onValueChange = { departure = it.uppercase() },
                            placeholder = { Text("e.g. JED") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Arrival airport (IATA)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = arrival,
                            onValueChange = { arrival = it.uppercase() },
                            placeholder = { Text("e.g. IST") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Travel dates", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { showDateRangePicker = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) { Text("$departDate  →  $returnDate", fontSize = 14.sp) }

                        Spacer(Modifier.height(12.dp))
                        Text("Stops", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("Any" to null, "Direct" to 0, "1 stop" to 1).forEach { (label, value) ->
                                FilterChip(
                                    selected = maxStops == value,
                                    onClick = { maxStops = value },
                                    label = { Text(label, fontSize = 12.sp) },
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Departure time (24h)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            TimeHourDropdown(
                                label = "From",
                                selectedHour = departureTimeFrom,
                                onSelected = { departureTimeFrom = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            TimeHourDropdown(
                                label = "To",
                                selectedHour = departureTimeTo,
                                onSelected = { departureTimeTo = it },
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Arrival time (24h)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            TimeHourDropdown(
                                label = "From",
                                selectedHour = arrivalTimeFrom,
                                onSelected = { arrivalTimeFrom = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            TimeHourDropdown(
                                label = "To",
                                selectedHour = arrivalTimeTo,
                                onSelected = { arrivalTimeTo = it },
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                searched = true
                                searchError = null
                                isLoading = true
                                scope.launch {
                                    try {
                                        if (returnDate <= departDate) {
                                            searchError = "Return date must be after departure date"
                                            results = emptyList()
                                            isLoading = false
                                            return@launch
                                        }
                                        val filters = com.flightwatch.app.data.SearchFilters(
                                            maxStops = maxStops,
                                            departureTimeFrom = departureTimeFrom,
                                            departureTimeTo = departureTimeTo,
                                            arrivalTimeFrom = arrivalTimeFrom,
                                            arrivalTimeTo = arrivalTimeTo,
                                        )
                                        // Save route to settings so refresh matches
                                        val st = (context.applicationContext as com.flightwatch.app.FlightWatchApp).settings
                                        st.origin = departure
                                        st.destination = arrival
                                        st.outboundDate = departDate
                                        st.returnDate = returnDate
                                        results = onSearch(departure, arrival, departDate, returnDate, 1, filters) { progressMsg = it }
                                        android.util.Log.i("FlightWatch", "Search returned ${results.size} flights")
                                    } catch (e: Exception) {
                                        android.util.Log.e("FlightWatch", "Search failed: ${e.message}", e)
                                        results = emptyList()
                                        searchError = e.message ?: "Search failed"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Search") }
                    }
                }
            }

            // Results section
            if (isLoading) {
                item(key = "loading") {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Searching flights...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(progressMsg, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (searchError != null) {
                item(key = "error") {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Text(
                            searchError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            if (searched && results.isNotEmpty()) {
                item(key = "results") {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Search Results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Found ${results.size} flights", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                items(results, key = { it.key }) { flight ->
                    SearchResultRow(
                        flight = flight,
                        selected = flight.key in selectedIds,
                        onToggle = {
                            selectedIds = if (flight.key in selectedIds) selectedIds - flight.key else selectedIds + flight.key
                        },
                        onBookClick = onBookClick,
                    )
                }
                item(key = "add-btn") {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp, 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${selectedIds.size} selected", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                val selected = results.filter { it.key in selectedIds }
                                if (selected.isNotEmpty()) onAddSelected(selected)
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) { Text("Add to My Flights") }
                    }
                }
            } else if (searched) {
                item(key = "empty") {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Search Results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Found 0 flights", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Date Range Picker dialog
    if (showDateRangePicker) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val startMillis = try { fmt.parse(departDate)?.time } catch (e: Exception) { null }
        val endMillis = try { fmt.parse(returnDate)?.time } catch (e: Exception) { null }
        val rangeState = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = startMillis,
            initialSelectedEndDateMillis = endMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        rangeState.selectedStartDateMillis?.let {
                            departDate = fmt.format(java.util.Date(it))
                        }
                        rangeState.selectedEndDateMillis?.let {
                            returnDate = fmt.format(java.util.Date(it))
                        }
                        showDateRangePicker = false
                    },
                    enabled = rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null,
                ) { Text("OK") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            },
        ) {
            androidx.compose.material3.DateRangePicker(
                state = rangeState,
                modifier = Modifier.weight(1f).padding(16.dp),
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    flight: FlightCard,
    selected: Boolean,
    onToggle: () -> Unit,
    onBookClick: (FlightCard) -> Unit = {},
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            AirlineLogoBadge(flight.airlineCode, Modifier.size(36.dp, 28.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${flight.airline} ${flight.flightNumber}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val ret = flight.returns.firstOrNull()
                Text(
                    "${FlightRules.formatTime(flight.departureTime)} → ${FlightRules.formatTime(flight.arrivalTime)} · ${formatDuration(flight.durationMinutes)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ret != null) {
                    Text(
                        "${ret.flightNumber}: ${FlightRules.formatTime(ret.departureTime)} → ${FlightRules.formatTime(ret.arrivalTime)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(formatPrice(flight.currentPrice ?: flight.returns.firstOrNull()?.price ?: flight.offer.price ?: 0.0, flight.offer.currency ?: "SAR"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.clickable { onBookClick(flight) })
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeHourDropdown(
    label: String,
    selectedHour: Int?,
    onSelected: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = if (selectedHour == null) "Any" else String.format("%02d:00", selectedHour)

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(90.dp)) {
            Text(displayText, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Any", fontSize = 13.sp) },
                onClick = { onSelected(null); expanded = false },
            )
            for (hour in 0..23) {
                DropdownMenuItem(
                    text = { Text(String.format("%02d:00", hour), fontSize = 13.sp) },
                    onClick = { onSelected(hour); expanded = false },
                )
            }
        }
    }
}
