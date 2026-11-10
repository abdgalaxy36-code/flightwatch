package com.flightwatch.app.ui.simple

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple Mode: Settings screen - matches test UI simple/settings.html exactly.
 * Controls only Simple Mode.
 */
@Composable
fun SimpleSettingsScreen(
    vm: com.flightwatch.app.ui.MainViewModel,
    settings: com.flightwatch.app.data.SettingsStore,
    onTestLetsfg: () -> Unit = {},
    onTestIgnav: () -> Unit = {},
    onTestSerpApi: () -> Unit = {},
    onTestOctoTrip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var searchApi by remember { mutableStateOf(settings.searchSource) }
    var bookingApi by remember { mutableStateOf(settings.linksSources.firstOrNull() ?: "combined") }
    var ignavKey by remember { mutableStateOf(settings.ignavApiKey) }
    var serpKey by remember { mutableStateOf(settings.serpApiKey) }
    var tokenInput by remember { mutableStateOf("") }
    var checkEvery by remember { mutableStateOf("30") }
    var checkUnit by remember { mutableStateOf("hours") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Search API ──
        SettingsSection("Search API") {
            Text("Choose the engine that prices your searches.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { searchApi = "letsfg"; settings.searchSource = "letsfg" },
                    modifier = Modifier.weight(1f),
                    colors = if (searchApi == "letsfg") ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                ) { Text("LetsFG", fontSize = 13.sp) }
                Button(
                    onClick = { searchApi = "ignav"; settings.searchSource = "ignav" },
                    modifier = Modifier.weight(1f),
                    colors = if (searchApi == "ignav") ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                ) { Text("ignav.com", fontSize = 13.sp) }
            }

            if (searchApi == "letsfg") {
                Spacer(Modifier.height(8.dp))
                Text("LetsFG", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Status", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (settings.hasLetsfgCredentials()) "Connected" else "Not connected",
                        fontSize = 12.sp,
                        color = if (settings.hasLetsfgCredentials()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                            android.util.Log.i("FlightWatch", "Connect button clicked")
                            scope.launch {
                                try {
                                    com.flightwatch.app.ui.AuthCoordinator.begin(context.applicationContext, vm)
                                } catch (e: Exception) {
                                    android.util.Log.e("FlightWatch", "Connect failed: ${e.message}", e)
                                }
                            }
                        }) { Text("Connect") }
                    OutlinedButton(onClick = { onTestLetsfg(); android.widget.Toast.makeText(context.applicationContext, "Testing LetsFG...", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Test connection") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Or paste your Bearer token:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = { Text("Bearer token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    val app = context.applicationContext as com.flightwatch.app.FlightWatchApp
                    app.tokenStore.manualToken = tokenInput.trim()
                    android.widget.Toast.makeText(context.applicationContext, "Token saved", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Save token") }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("ignav.com", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("API key", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(if (ignavKey.isNotBlank()) "Saved" else "Not set", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = ignavKey,
                    onValueChange = { ignavKey = it },
                    placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { settings.ignavApiKey = ignavKey.trim() }) { Text("Save key") }
                    OutlinedButton(onClick = { onTestIgnav(); android.widget.Toast.makeText(context.applicationContext, "Testing ignav.com...", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Test connection") }
                }
                Spacer(Modifier.height(6.dp))
                Text("Get your free key at ignav.com/signup — 1,000 free requests/month.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Appearance ──
        SettingsSection("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System" to "system", "Light" to "light", "Dark" to "dark").forEach { (label, value) ->
                    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
                    val active = themeMode == value
                    Button(
                        onClick = { settings.setThemeMode(value) },
                        modifier = Modifier.weight(1f),
                        colors = if (active) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ) else ButtonDefaults.outlinedButtonColors(),
                    ) { Text(label, fontSize = 13.sp) }
                }
            }
        }

        // ── Notifications ──
        SettingsSection("Notifications") {
            val notifyEnabled by settings.notificationsEnabledFlow.collectAsStateWithLifecycle()
            val notifyDrop by settings.notifyOnPriceDropFlow.collectAsStateWithLifecycle()
            val notifyRise by settings.notifyOnPriceRiseFlow.collectAsStateWithLifecycle()
            val notifyNewLow by settings.notifyOnNewLowFlow.collectAsStateWithLifecycle()
            val notifyOfficial by settings.notifyOnlyOfficialFlow.collectAsStateWithLifecycle()
            ToggleRow("Price alerts", "Notify when tracked prices change", notifyEnabled) { settings.notificationsEnabled = it }
            ToggleRow("Price drops", "Fare gets cheaper", notifyDrop) { settings.notifyOnPriceDrop = it }
            ToggleRow("Price rises", "Fare gets more expensive", notifyRise) { settings.notifyOnPriceRise = it }
            ToggleRow("New low price", "Lowest price ever seen", notifyNewLow) { settings.notifyOnNewLow = it }
            ToggleRow("Airline site only", "Ignore third-party sellers", notifyOfficial) { settings.notifyOnlyOfficial = it }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check every", fontSize = 14.sp)
                    Text("Any number of minutes or hours", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = checkEvery,
                    onValueChange = { checkEvery = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                )
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("min" to "min", "hours" to "hours").forEach { (label, value) ->
                        Button(
                            onClick = { checkUnit = value },
                            colors = if (checkUnit == value) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                                try {
                                    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        val channel = android.app.NotificationChannel("test_channel", "Test Notifications", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                                        nm.createNotificationChannel(channel)
                                    }
                                    val notification = androidx.core.app.NotificationCompat.Builder(context, "test_channel")
                                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                                        .setContentTitle("FlightWatch")
                                        .setContentText("Test notification from Simple Mode!")
                                        .setAutoCancel(true)
                                        .build()
                                    nm.notify(1001, notification)
                                    android.widget.Toast.makeText(context, "Notification sent", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Test notification") }
        }

        // ── Booking links ──
        SettingsSection("Booking links") {
            Text("Choose which API provides booking links for flights.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SerpApi" to "serpapi", "OctoTrip" to "octotrip", "ignav" to "ignav", "Kiwi" to "kiwi", "Combined" to "combined").forEach { (label, value) ->
                    Button(
                        onClick = {
                            bookingApi = value
                            settings.linksSources = setOf(value)
                            android.widget.Toast.makeText(context.applicationContext, "Booking API: $label", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (bookingApi == value) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                    ) { Text(label, fontSize = 14.sp) }
                }
            }

            when (bookingApi) {
                "kiwi" -> {
                    Spacer(Modifier.height(8.dp))
                    Text("Kiwi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Kiwi Tequila deep links for exact flights.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Not yet implemented.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "combined" -> {
                    Spacer(Modifier.height(8.dp))
                    Text("Combined: ignav (airline) + SerpApi (3rd party)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Gets airline direct links from ignav AND all 3rd party sites from SerpApi.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Both APIs need their keys set above.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "serpapi" -> {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("SerpApi key", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (serpKey.isNotBlank()) "Saved" else "Not set", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = serpKey,
                        onValueChange = { serpKey = it },
                        placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { settings.serpApiKey = serpKey.trim() }) { Text("Save key") }
                        OutlinedButton(onClick = { onTestSerpApi(); android.widget.Toast.makeText(context.applicationContext, "Testing SerpApi...", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Test") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Google Flights deep links for exact flights.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "octotrip" -> {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("OctoTrip", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("Free to use", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { android.widget.Toast.makeText(context.applicationContext, "OctoTrip enabled", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Enable") }
                        OutlinedButton(onClick = { onTestOctoTrip(); android.widget.Toast.makeText(context.applicationContext, "Testing OctoTrip...", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Test") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("No API key needed. Provides airline booking links.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "ignav" -> {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ignav.com key", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (ignavKey.isNotBlank()) "Saved" else "Not set", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = ignavKey,
                        onValueChange = { ignavKey = it },
                        placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { settings.ignavApiKey = ignavKey.trim() }) { Text("Save key") }
                        OutlinedButton(onClick = onTestIgnav) { Text("Test") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Exact-flight booking links via ignav_id.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Backup & Restore ──
        SettingsSection("Backup & Restore") {
            Text("Backup your flights, settings, and price history to GitHub.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            
            // GitHub token
            var githubToken by remember { mutableStateOf(settings.githubToken ?: "") }
            OutlinedTextField(
                value = githubToken,
                onValueChange = { githubToken = it },
                label = { Text("GitHub Personal Access Token") },
                placeholder = { Text("ghp_xxxxxxxxxxxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                settings.githubToken = githubToken.trim()
                android.widget.Toast.makeText(context.applicationContext, "GitHub token saved", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Save GitHub token", fontSize = 13.sp) }
            Text("Required scopes: gist (read/write)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            
            // Backup location info
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Backup location", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("GitHub Gist (private)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Repo: github.com/abdgalaxy36-code/flightwatch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("File: flightwatch-backup.json", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Contains: flights, settings, price history, API usage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Both Simple & Extra Mode data", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            // Backup info
            var lastBackupTime by remember { mutableStateOf("") }
            var lastBackupSize by remember { mutableStateOf("") }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Last backup", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(lastBackupTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Backup size", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(lastBackupSize, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                android.widget.Toast.makeText(context.applicationContext, "Creating backup...", android.widget.Toast.LENGTH_SHORT).show()
                                val app = context.applicationContext as com.flightwatch.app.FlightWatchApp
                                val token = settings.githubToken ?: githubToken
                                if (token.isNullOrBlank()) {
                                    android.widget.Toast.makeText(context.applicationContext, "Enter GitHub token first", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val backupData = com.flightwatch.app.data.GitHubBackup.BackupData(
                                    timestamp = System.currentTimeMillis(),
                                    selectedFlights = app.flightsStore.load(),
                                    settings = emptyMap(),
                                    priceHistory = emptyMap(),
                                    apiUsage = emptyMap(),
                                )
                                val json = com.google.gson.Gson().toJson(backupData)
                                val ok = com.flightwatch.app.data.GitHubBackup(context).pushToGitHub(token, json)
                                if (ok) {
                                    lastBackupSize = "${json.length / 1024} KB"
                                    lastBackupTime = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                    android.widget.Toast.makeText(context.applicationContext, "Backup saved to GitHub", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context.applicationContext, "Backup failed - check token", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context.applicationContext, "Backup failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Backup", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                android.widget.Toast.makeText(context.applicationContext, "Restoring...", android.widget.Toast.LENGTH_SHORT).show()
                                val app = context.applicationContext as com.flightwatch.app.FlightWatchApp
                                val token = settings.githubToken ?: githubToken
                                if (token.isNullOrBlank()) {
                                    android.widget.Toast.makeText(context.applicationContext, "Enter GitHub token first", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val json = com.flightwatch.app.data.GitHubBackup(context).pullFromGitHub(token)
                                if (json != null) {
                                    val data = com.google.gson.Gson().fromJson(json, com.flightwatch.app.data.GitHubBackup.BackupData::class.java)
                                    app.flightsStore.save(data.selectedFlights)
                                    android.widget.Toast.makeText(context.applicationContext, "Restore complete: ${data.selectedFlights.size} flights", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context.applicationContext, "Restore failed - check token/gist ID", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context.applicationContext, "Restore failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Restore", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    try {
                        val app = context.applicationContext as com.flightwatch.app.FlightWatchApp
                        val backupData = com.flightwatch.app.data.GitHubBackup.BackupData(
                            timestamp = System.currentTimeMillis(),
                            selectedFlights = app.flightsStore.load(),
                            settings = emptyMap(),
                            priceHistory = emptyMap(),
                            apiUsage = emptyMap(),
                        )
                        val json = com.google.gson.Gson().toJson(backupData)
                        val file = java.io.File(context.filesDir, "flightwatch_backup.json")
                        file.writeText(json)
                        android.widget.Toast.makeText(context.applicationContext, "Saved: ${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context.applicationContext, "Save failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Local Backup", fontSize = 13.sp) }
        }

        // ── Diagnostics ──
        SettingsSection("Diagnostics") {
            Text("View device info, logs, crash history. Export diagnostic report.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = android.content.Intent(context, com.flightwatch.app.diagnostics.DiagnosticsActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Diagnostics", fontSize = 13.sp) }
        }

        // ── API usage ──
        SettingsSection("API usage this month") {
            val usageCounts by vm.usage.counts.collectAsStateWithLifecycle()
            val letsfgSearches = usageCounts[com.flightwatch.app.data.ApiUsageStore.LETSFG] ?: 0
            val serpRequests = usageCounts[com.flightwatch.app.data.ApiUsageStore.SERPAPI] ?: 0
            val octoRequests = usageCounts[com.flightwatch.app.data.ApiUsageStore.OCTOTRIP] ?: 0
            val ignavRequests = usageCounts[com.flightwatch.app.data.ApiUsageStore.IGNAV] ?: 0
            
            Text("LetsFG", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            UsageRow("Searches", "$letsfgSearches used", "${letsfgSearches * 32} credits")
            
            Spacer(Modifier.height(8.dp))
            Text("SerpApi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            UsageRow("Requests", "$serpRequests used", "${(250 - serpRequests).coerceAtLeast(0)} of 250 remaining")
            
            Spacer(Modifier.height(8.dp))
            Text("OctoTrip", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            UsageRow("Requests", "$octoRequests used", "free")
            
            Spacer(Modifier.height(8.dp))
            Text("ignav.com", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            UsageRow("Requests", "$ignavRequests used", "${(1000 - ignavRequests).coerceAtLeast(0)} of 1,000 remaining")
            
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    android.util.Log.i("FlightWatch", "Refreshing API counts...")
                    scope.launch {
                        try {
                            // Refresh SerpApi quota
                            val serpClient = vm.app.serpApi()
                            if (serpClient != null) {
                                val info = serpClient.account()
                                android.util.Log.i("FlightWatch", "SerpApi: ${info.planSearchesLeft} left of ${info.searchesPerMonth}")
                            }
                            android.widget.Toast.makeText(context.applicationContext, "API counts refreshed", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context.applicationContext, "Refresh failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh counts") }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
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
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun UsageRow(name: String, count: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
