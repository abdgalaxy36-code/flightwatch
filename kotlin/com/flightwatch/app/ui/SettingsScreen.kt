package com.flightwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.flightwatch.app.data.ApiUsageStore
import com.flightwatch.app.data.SerpApiClient
import com.flightwatch.app.data.OctoTripApi
import com.flightwatch.app.ui.MainViewModel

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun UsageRow(name: String, count: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(value, fontSize = 13.sp) }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun toMillis(date: String): Long? = try {
    LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
} catch (e: Exception) {
    null
}

fun fromMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings = vm.settings
    val notificationsEnabled by settings.notificationsEnabledFlow.collectAsStateWithLifecycle()
    val notifyOnlyOfficial by settings.notifyOnlyOfficialFlow.collectAsStateWithLifecycle()
    val notifyOnPriceDrop by settings.notifyOnPriceDropFlow.collectAsStateWithLifecycle()
    val notifyOnPriceRise by settings.notifyOnPriceRiseFlow.collectAsStateWithLifecycle()
    val notifyOnNewLow by settings.notifyOnNewLowFlow.collectAsStateWithLifecycle()
    val notifyFavouriteChanges by settings.notifyFavouriteChangesFlow.collectAsStateWithLifecycle()
    val checkInterval by settings.checkIntervalHoursFlow.collectAsStateWithLifecycle(24)
    val linksSources by settings.linksSourcesFlow.collectAsStateWithLifecycle(setOf("serpapi", "octotrip", "ignav"))
    val scope = rememberCoroutineScope()

    val themeMode by settings.themeMode.collectAsStateWithLifecycle("system")
    val serpKey by settings.serpApiKeyFlow.collectAsStateWithLifecycle("")
    val ignavKey by settings.ignavApiKeyFlow.collectAsStateWithLifecycle("")
    val searchSource by settings.searchSourceFlow.collectAsStateWithLifecycle("letsfg")

    var showOutPicker by remember { mutableStateOf(false) }
    var outDate by remember { mutableStateOf(settings.outboundDate) }
    var retDate by remember { mutableStateOf(settings.returnDate) }
    var gapDays by remember { mutableStateOf(settings.tripGapDays.toString()) }
    var tokenInput by remember { mutableStateOf("") }
    var showTokenField by remember { mutableStateOf(false) }
    var serpKeyInput by remember { mutableStateOf(serpKey) }
    var ignavKeyInput by remember { mutableStateOf(ignavKey) }
    var notificationTestNote by remember { mutableStateOf("") }

    val auth by vm.authState.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Account ──────────────────────────────────────────────────
        SectionCard("Account") {
            if (vm.tokenStore.hasCredentials()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connected to LetsFG", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.disconnect() }) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            } else {
                Text(
                    "Connect once to search live fares. No card needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                when (val a = auth) {
                    is AuthState.Working -> Text(a.message, style = MaterialTheme.typography.bodySmall)
                    is AuthState.Failed -> Text(a.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    is AuthState.Ready -> Text("Open this to approve:\n${a.authorizeUrl}", style = MaterialTheme.typography.labelSmall)
                    is AuthState.Connected -> Text(a.message, style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { scope.launch { AuthCoordinator.begin(vm.context(), vm) } }, enabled = auth !is AuthState.Working) {
                    Text("Connect")
                }
                TextButton(onClick = { showTokenField = !showTokenField }) {
                    Text(if (showTokenField) "Hide" else "Paste token", fontSize = 13.sp)
                }
                if (showTokenField) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Bearer token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        vm.saveManualToken(tokenInput)
                        tokenInput = ""
                        showTokenField = false
                        vm.search(force = true)
                    }) { Text("Save") }
                }
            }
        }

        // ── Appearance ───────────────────────────────────────────────
        SectionCard("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System" to "system", "Light" to "light", "Dark" to "dark").forEach { (label, value) ->
                    val active = themeMode == value
                    if (active) {
                        Button(
                            onClick = { settings.setThemeMode(value) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1f),
                        ) { Text(label, fontSize = 13.sp) }
                    } else {
                        OutlinedButton(onClick = { settings.setThemeMode(value) }, modifier = Modifier.weight(1f)) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ── Trip dates ──────────────────────────────────────────────
        SectionCard("Trip dates") {
            Text(
                "The return is always the departure plus the gap below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            SettingRow(
                label = "Outbound date",
                value = outDate,
                onClick = { showOutPicker = true },
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Days between departure & return", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Return updates automatically",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = gapDays,
                    onValueChange = { v ->
                        if (v.length <= 2 && v.all { it.isDigit() }) {
                            gapDays = v
                            val days = v.toIntOrNull()
                            if (days != null && days in 1..90) {
                                settings.tripGapDays = days
                                settings.normalizeDates()
                                retDate = settings.returnDate
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(64.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Return date", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(retDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Notifications ────────────────────────────────────────────
        SectionCard("Notifications") {
            ToggleRow(
                title = "Price alerts",
                subtitle = "Notify when tracked prices change",
                checked = notificationsEnabled,
                onChange = { settings.notificationsEnabled = it },
            )
            if (notificationsEnabled) {
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Price drops",
                    subtitle = "Fare gets cheaper",
                    checked = notifyOnPriceDrop,
                    onChange = { settings.notifyOnPriceDrop = it },
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Price rises",
                    subtitle = "Fare gets more expensive",
                    checked = notifyOnPriceRise,
                    onChange = { settings.notifyOnPriceRise = it },
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "New low price",
                    subtitle = "Lowest price ever seen for a flight",
                    checked = notifyOnNewLow,
                    onChange = { settings.notifyOnNewLow = it },
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Airline site only",
                    subtitle = "Ignore third-party sellers",
                    checked = notifyOnlyOfficial,
                    onChange = { settings.notifyOnlyOfficial = it },
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Favourite price changes",
                    subtitle = "Notify when a starred flight's price moves",
                    checked = notifyFavouriteChanges,
                    onChange = { settings.notifyFavouriteChanges = it },
                )

                Spacer(Modifier.height(8.dp))
                Text("Check every", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(12, 24, 48).forEach { hours ->
                        val active = checkInterval == hours
                        FilterChip(
                            selected = active,
                            onClick = { settings.applyCheckInterval(hours) },
                            label = { Text(if (hours == 24) "Daily" else "${hours}h", fontSize = 13.sp) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.sendTestNotification { notificationTestNote = it } }) {
                        Text("Test notification", fontSize = 13.sp)
                    }
                    if (notificationTestNote.isNotBlank()) {
                        Text(notificationTestNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Booking links ────────────────────────────────────────────
        SectionCard("Booking links") {
            Text(
                "Source used to open the flight you tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "SerpApi" to "serpapi",
                    "OctoTrip" to "octotrip",
                    "ignav.com" to "ignav",
                    "Kiwi" to "kiwi",
                    "FlowExtract" to "flowextract",
                ).forEach { (label, value) ->
                    val active = linksSources.contains(value)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { settings.linksSources = if (active) linksSources - value else linksSources + value }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Checkbox(checked = active, onCheckedChange = { settings.linksSources = if (active) linksSources - value else linksSources + value })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SerpApi key", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (serpKey.isNotBlank()) {
                    Text(
                        "Saved · …${serpKey.takeLast(4)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = serpKeyInput,
                onValueChange = { serpKeyInput = it },
                label = { Text(if (serpKey.isNotBlank()) "Update SerpApi key" else "Paste SerpApi key") },
                placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        settings.serpApiKey = serpKeyInput.trim()
                        vm.refreshLinksNow()
                    },
                    enabled = serpKeyInput.isNotBlank() && serpKeyInput.trim() != serpKey,
                ) { Text("Save key") }
                if (serpKey.isNotBlank()) {
                    OutlinedButton(onClick = {
                        settings.serpApiKey = ""
                        serpKeyInput = ""
                        vm.refreshLinksNow()
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (serpKey.isNotBlank() && serpKeyInput.isNotBlank() && serpKeyInput.trim() == serpKey) {
                Text(
                    "Same as saved key.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Search API ──────────────────────────────────────────────
        SectionCard("Search API") {
            Text(
                "Engine used to price the flight search. Each keeps its own saved results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Triple("LetsFG", "Airline-only search · needs LetsFG sign-in", "letsfg"),
                    Triple("ignav.com", "Round-trip fares in SAR · needs ignav.com key", "ignav"),
                ).forEach { (label, subtitle, value) ->
                    val active = searchSource == value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.switchSearchSource(value) }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = active,
                            onClick = { vm.switchSearchSource(value) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (value == "ignav" && ignavKey.isNotBlank()) {
                            Text(
                                "key saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (searchSource == "ignav" && ignavKey.isBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add your ignav.com key below to search with ignav.com.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // ── ignav.com API key ─────────────────────────────────────
        SectionCard("ignav.com API key") {
            Text(
                "ignav.com provides round-trip search with exact-flight booking links via ignav_id.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ignav.com key", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (ignavKey.isNotBlank()) {
                    Text(
                        "Saved · …${ignavKey.takeLast(4)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = ignavKeyInput,
                onValueChange = { ignavKeyInput = it },
                label = { Text(if (ignavKey.isNotBlank()) "Update ignav.com key" else "Paste ignav.com key") },
                placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        settings.ignavApiKey = ignavKeyInput.trim()
                        vm.refreshLinksNow()
                    },
                    enabled = ignavKeyInput.isNotBlank() && ignavKeyInput.trim() != ignavKey,
                ) { Text("Save key") }
                if (ignavKey.isNotBlank()) {
                    OutlinedButton(onClick = {
                        settings.ignavApiKey = ""
                        ignavKeyInput = ""
                        vm.refreshLinksNow()
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (ignavKey.isNotBlank() && ignavKeyInput.isNotBlank() && ignavKeyInput.trim() == ignavKey) {
                Text(
                    "Same as saved key.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── API usage ────────────────────────────────────────────────
        SectionCard("API usage this month") {
            val usageCounts by vm.usage.counts.collectAsStateWithLifecycle()
            val letsfgSearches: Int = usageCounts[ApiUsageStore.LETSFG] ?: 0
            val serpRequests: Int = usageCounts[ApiUsageStore.SERPAPI] ?: 0
            val octoRequests: Int = usageCounts[ApiUsageStore.OCTOTRIP] ?: 0
            var serpLeft by remember { mutableStateOf<Int?>(null) }
            var serpTotal by remember { mutableStateOf<Int?>(null) }
            var accountNote by remember { mutableStateOf("") }
            var accountBusy by remember { mutableStateOf(false) }

            Column(Modifier.fillMaxWidth()) {
                UsageRow("LetsFG", "$letsfgSearches used", "${letsfgSearches * 32} credits")
                UsageRow("SerpApi", "$serpRequests used", when {
                    serpLeft != null && serpTotal != null -> "${serpLeft} left of $serpTotal"
                    serpLeft != null -> "${serpLeft} left"
                    else -> "$serpRequests of 250 used"
                })
                UsageRow("OctoTrip", "$octoRequests used", "free")
                if (accountNote.isNotBlank()) {
                    Text(
                        accountNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            accountBusy = true
                            accountNote = ""
                            scope.launch {
                                try {
                                    val info = SerpApiClient(serpKey, vm.usage).account()
                                    serpLeft = info.planSearchesLeft
                                    serpTotal = info.searchesPerMonth
                                    accountNote = "Updated"
                                } catch (e: Exception) {
                                    accountNote = "Refresh failed"
                                } finally {
                                    accountBusy = false
                                }
                            }
                        },
                        enabled = serpKey.isNotBlank() && !accountBusy,
                    ) { Text("Refresh SerpApi quota") }

                    OutlinedButton(
                        onClick = {
                            accountBusy = true
                            accountNote = ""
                            scope.launch {
                                try {
                                    OctoTripApi(vm.usage).sweep("IST", "JED", "2025-01-01", "2025-01-08", 1)
                                    accountNote = "OctoTrip reachable"
                                } catch (e: Exception) {
                                    accountNote = e.message?.take(60) ?: "Refresh failed"
                                } finally {
                                    accountBusy = false
                                }
                            }
                        },
                        enabled = !accountBusy,
                    ) { Text("Test OctoTrip") }
                }
            }
        }

        // ── Date pickers ─────────────────────────────────────────────────
        if (showOutPicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = toMillis(settings.outboundDate))
            DatePickerDialog(
                onDismissRequest = { showOutPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let {
                            val picked = fromMillis(it)
                            settings.outboundDate = if (settings.tripMode == "month") {
                                java.time.LocalDate.parse(picked).withDayOfMonth(1).toString()
                            } else {
                                picked
                            }
                            // The return is always departure + the gap setting.
                            settings.normalizeDates()
                            outDate = settings.outboundDate
                            retDate = settings.returnDate
                            vm.search(force = true)
                        }
                        showOutPicker = false
                    }) { Text("Set") }
                },
                dismissButton = { TextButton(onClick = { showOutPicker = false }) { Text("Cancel") } },
            ) { DatePicker(state = pickerState) }
        }
    }
}


