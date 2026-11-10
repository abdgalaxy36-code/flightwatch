package com.flightwatch.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flightwatch.app.FlightWatchApp
import com.flightwatch.app.ui.SettingsScreen
import com.flightwatch.app.ui.simple.SimpleHomeScreen
import com.flightwatch.app.ui.simple.SimpleSearchScreen
import com.flightwatch.app.ui.simple.SimpleSearchRepository
import com.flightwatch.app.data.SelectedFlightsStore
import com.flightwatch.app.data.PriceHistoryStore
import com.flightwatch.app.ui.simple.SimpleSettingsScreen
import com.flightwatch.app.ui.extra.ExtraHomeScreen
import com.flightwatch.app.ui.extra.ExtraSettingsScreen
import com.flightwatch.app.ui.theme.FlightWatchTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MainActivityContent(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val state = vm.state.collectAsStateWithLifecycle()
    val screenState = remember { mutableStateOf(Screen.FLIGHTS) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val appMode = remember { mutableStateOf("simple") }  // "simple" or "extra"
    val simpleScreen = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("home") }
    val flightsStore = remember { SelectedFlightsStore(context.applicationContext) }
    val historyStore = remember { PriceHistoryStore(context.applicationContext) }
    val selectedFlights = remember { mutableStateOf(flightsStore.load()) }
    val isRefreshing = remember { mutableStateOf(false) }
    val refreshProgress = remember { mutableStateOf("") }
    
    // Fetch booking links for stored flights on startup (don't clobber user changes)
    LaunchedEffect(Unit) {
        val loaded = flightsStore.load()
        if (loaded.isNotEmpty()) {
            val loadedKeys = loaded.map { it.key }.toSet()
            val withLinks = try {
                com.flightwatch.app.ui.simple.SimpleSearchRepository(vm.app).fetchBookingLinksFor(loaded)
            } catch (e: Exception) {
                loaded
            }
            // Only update if user hasn't changed flights during the fetch
            val currentKeys = selectedFlights.value.map { it.key }.toSet()
            if (currentKeys == loadedKeys) {
                selectedFlights.value = withLinks
                flightsStore.save(withLinks)
            }
        }
    }
    val activeIndex = vm.activePageIndex.collectAsStateWithLifecycle()
    val pages = vm.pages

    BackHandler(enabled = screenState.value == Screen.SETTINGS) {
        screenState.value = Screen.FLIGHTS
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button (when not on home)
                if (screenState.value == Screen.SETTINGS || (appMode.value == "simple" && simpleScreen.value != "home")) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (screenState.value == Screen.SETTINGS) screenState.value = Screen.FLIGHTS
                                else simpleScreen.value = "home"
                            }
                            .padding(6.dp)
                            .size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                // Title
                Text(
                    when {
                        screenState.value == Screen.SETTINGS -> "Settings"
                        appMode.value == "simple" && simpleScreen.value == "search" -> "Search Flights"
                        appMode.value == "simple" -> "Simple Mode"
                        else -> "Extra Mode"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // Mode switcher (matches test UI .tb-mode)
                Text(
                    if (appMode.value == "simple") "Switch to Extra" else "Switch to Simple",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable {
                            appMode.value = if (appMode.value == "simple") "extra" else "simple"
                            screenState.value = Screen.FLIGHTS
                            simpleScreen.value = "home"
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(8.dp))
                // Refresh links button
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh links",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (!isRefreshing.value) {
                                scope.launch {
                                    isRefreshing.value = true
                                    refreshProgress.value = "Starting refresh..."
                                    try {
                                        val st = vm.app.settings
                                        val oldFlights = selectedFlights.value
                                        val oldKeys = oldFlights.map { it.key }.toSet()
                                        val fresh = SimpleSearchRepository(vm.app).refreshAll(
                                            st.origin, st.destination, st.outboundDate, st.returnDate, st.adults,
                                            oldFlights = oldFlights,
                                        ) { msg -> refreshProgress.value = msg }
                                        val currentKeys = selectedFlights.value.map { it.key }.toSet()
                                        if (currentKeys == oldKeys) {
                                            val freshByKey = fresh.associateBy { it.key }
                                            val merged = oldFlights.map { old ->
                                                val new = freshByKey[old.key]
                                                if (new != null) {
                                                    val newPrice = new.currentPrice ?: new.returns.firstOrNull()?.price ?: new.offer.price
                                                    old.copy(
                                                        currentPrice = newPrice,
                                                        priceCheckedAt = System.currentTimeMillis(),
                                                        bookingLinks = if (new.bookingLinks.isNotEmpty()) new.bookingLinks else old.bookingLinks,
                                                        bookingUrl = new.bookingUrl ?: old.bookingUrl,
                                                    )
                                                } else old
                                            }
                                            selectedFlights.value = merged
                                            flightsStore.save(merged)
                                            val changed = merged.count { old ->
                                                val fresh = freshByKey[old.key]
                                                fresh != null && (fresh.returns.firstOrNull()?.price ?: fresh.offer.price) != (old.currentPrice ?: old.returns.firstOrNull()?.price ?: old.offer.price)
                                            }
                                            android.widget.Toast.makeText(context.applicationContext, "Refreshed: $changed prices changed", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context.applicationContext, "Refresh failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isRefreshing.value = false
                                        refreshProgress.value = ""
                                    }
                                }
                            }
                        }
                        .padding(6.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                // Settings gear
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { screenState.value = Screen.SETTINGS }
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
        },
    ) { innerPadding ->
        when {
            // Settings screen (both modes)
            screenState.value == Screen.SETTINGS -> {
                if (appMode.value == "simple") {
                    SimpleSettingsScreen(
                        vm = vm,
                        settings = vm.settings,
                        onTestLetsfg = {
                            scope.launch {
                                try {
                                    val response = vm.app.api.search(
                                        com.flightwatch.app.data.LetsfgApi.SearchParams(
                                            origin = "JED", destination = "IST",
                                            dateFrom = "2026-12-08", returnDate = "2026-12-18",
                                            adults = 1, children = 0,
                                            currency = "SAR", cabin = "M", airlineOnly = true
                                        )
                                    )
                                    android.widget.Toast.makeText(context.applicationContext, "LetsFG OK: ${response.offers?.size ?: 0} offers", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "LetsFG failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestIgnav = {
                            scope.launch {
                                try {
                                    val client = vm.app.ignavApi()
                                    if (client == null) {
                                        android.widget.Toast.makeText(context.applicationContext, "Add ignav.com key first", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val response = client.searchRoundTrip("JED", "IST", "2026-12-08", "2026-12-18", 1, 0, "SA", null)
                                        android.widget.Toast.makeText(context.applicationContext, "ignav OK: ${(response.itineraries ?: emptyList()).size} itineraries", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "ignav failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestSerpApi = {
                            scope.launch {
                                try {
                                    val client = vm.app.serpApi()
                                    if (client == null) {
                                        android.widget.Toast.makeText(context.applicationContext, "Add SerpApi key first", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val info = client.account()
                                        android.widget.Toast.makeText(context.applicationContext, "SerpApi OK: ${info.planSearchesLeft} searches left", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "SerpApi failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestOctoTrip = {
                            scope.launch {
                                try {
                                    val flights = vm.app.octoTripApi.sweep("IST", "JED", "2026-12-08", "2026-12-18", 1)
                                    android.widget.Toast.makeText(context.applicationContext, "OctoTrip OK: ${flights.size} flights", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "OctoTrip failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ExtraSettingsScreen(
                        vm = vm,
                        settings = vm.settings,
                        onTestLetsfg = {
                            scope.launch {
                                try {
                                    val response = vm.app.api.search(
                                        com.flightwatch.app.data.LetsfgApi.SearchParams(
                                            origin = "JED", destination = "IST",
                                            dateFrom = "2026-12-08", returnDate = "2026-12-18",
                                            adults = 1, children = 0,
                                            currency = "SAR", cabin = "M", airlineOnly = true
                                        )
                                    )
                                    android.widget.Toast.makeText(context.applicationContext, "LetsFG OK: ${response.offers?.size ?: 0} offers", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "LetsFG failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestIgnav = {
                            scope.launch {
                                try {
                                    val client = vm.app.ignavApi()
                                    if (client == null) {
                                        android.widget.Toast.makeText(context.applicationContext, "Add ignav.com key first", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val response = client.searchRoundTrip("JED", "IST", "2026-12-08", "2026-12-18", 1, 0, "SA", null)
                                        android.widget.Toast.makeText(context.applicationContext, "ignav OK: ${(response.itineraries ?: emptyList()).size} itineraries", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "ignav failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestSerpApi = {
                            scope.launch {
                                try {
                                    val client = vm.app.serpApi()
                                    if (client == null) {
                                        android.widget.Toast.makeText(context.applicationContext, "Add SerpApi key first", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val info = client.account()
                                        android.widget.Toast.makeText(context.applicationContext, "SerpApi OK: ${info.planSearchesLeft} searches left", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "SerpApi failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onTestOctoTrip = {
                            scope.launch {
                                try {
                                    val flights = vm.app.octoTripApi.sweep("IST", "JED", "2026-12-08", "2026-12-18", 1)
                                    android.widget.Toast.makeText(context.applicationContext, "OctoTrip OK: ${flights.size} flights", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "OctoTrip failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
            // Simple Mode
            appMode.value == "simple" -> {
                when (simpleScreen.value) {
                    "home" -> SimpleHomeScreen(
                        flights = selectedFlights.value,
                        isRefreshing = isRefreshing.value,
                        refreshProgress = refreshProgress.value,
                        onRefresh = {
                            if (!isRefreshing.value) {
                                scope.launch {
                                    isRefreshing.value = true
                                    refreshProgress.value = "Starting refresh..."
                                    try {
                                        val st = vm.app.settings
                                        val oldFlights = selectedFlights.value
                                        val oldKeys = oldFlights.map { it.key }.toSet()
                                        val fresh = SimpleSearchRepository(vm.app).refreshAll(
                                            st.origin, st.destination, st.outboundDate, st.returnDate, st.adults,
                                            oldFlights = oldFlights,
                                        ) { msg -> refreshProgress.value = msg }
                                        val currentKeys = selectedFlights.value.map { it.key }.toSet()
                                        if (currentKeys == oldKeys) {
                                            val freshByKey = fresh.associateBy { it.key }
                                            val merged = oldFlights.map { old ->
                                                val new = freshByKey[old.key]
                                                if (new != null) {
                                                    val newPrice = new.currentPrice ?: new.returns.firstOrNull()?.price ?: new.offer.price
                                                    old.copy(
                                                        currentPrice = newPrice,
                                                        priceCheckedAt = System.currentTimeMillis(),
                                                        bookingLinks = if (new.bookingLinks.isNotEmpty()) new.bookingLinks else old.bookingLinks,
                                                        bookingUrl = new.bookingUrl ?: old.bookingUrl,
                                                    )
                                                } else old
                                            }
                                            selectedFlights.value = merged
                                            flightsStore.save(merged)
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context.applicationContext, "Refresh failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isRefreshing.value = false
                                        refreshProgress.value = ""
                                    }
                                }
                            }
                        },
                        historyStore = historyStore,
                        linksSource = vm.settings.linksSources.firstOrNull() ?: "api",
                        onDeleteFlight = { flight ->
                            selectedFlights.value = selectedFlights.value.filter { it.key != flight.key }
                            flightsStore.save(selectedFlights.value)
                            android.widget.Toast.makeText(context.applicationContext, "Flight removed", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        departure = vm.settings.origin,
                        arrival = vm.settings.destination,
                        onSearchClick = { simpleScreen.value = "search" },
                        onBookClick = { flight: com.flightwatch.app.data.FlightCard ->
                            val ret = flight.returns.firstOrNull()
                            val url = ret?.bookingUrl ?: flight.bookingUrl
                            if (url != null) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context.applicationContext, "No booking link available", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                    "search" -> SimpleSearchScreen(
                        onSearch = { dep, arr, depDate, retDate, adults, filters, onProgress ->
                            SimpleSearchRepository(vm.app).search(dep, arr, depDate, retDate, adults, onProgress, filters = filters)
                        },
                        onBookClick = { flight: com.flightwatch.app.data.FlightCard ->
                            val ret = flight.returns.firstOrNull()
                            val url = ret?.bookingUrl ?: flight.bookingUrl
                            if (url != null) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context.applicationContext, "No booking link available", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddSelected = { flights ->
                            // MERGE: replace old with new (new has booking links)
                            val existing = selectedFlights.value
                            val merged = existing.toMutableList()
                            flights.forEach { new ->
                                val idx = merged.indexOfFirst { it.key == new.key }
                                if (idx >= 0) {
                                    merged[idx] = new  // Replace with new (has links)
                                } else {
                                    merged.add(new)
                                }
                            }
                            selectedFlights.value = merged
                            flightsStore.save(merged)
                            simpleScreen.value = "home"
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
            // Extra Mode
            else -> {
                Box(Modifier.padding(innerPadding).fillMaxSize()) {
                    ExtraHomeScreen(
                        flights = state.value.sections.flatMap { it.flights }.distinctBy { it.key },
                        onRefresh = { vm.search(force = true) },
                        onBookClick = { flight: com.flightwatch.app.data.FlightCard ->
                            val ret = flight.returns.firstOrNull()
                            val url = ret?.bookingUrl ?: flight.bookingUrl
                            if (url != null) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context.applicationContext, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context.applicationContext, "No booking link available", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

enum class Screen {
    FLIGHTS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        handleOAuthIntent(intent)
        setContent {
            val app = application as FlightWatchApp
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle("system")
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app))
            FlightWatchTheme(themeMode) {
                MainActivityContent(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "flightwatch" && data.host == "oauth") {
            AuthCoordinator.onRedirect(this, data)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
