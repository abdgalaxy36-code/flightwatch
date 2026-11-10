# FlightWatch - Complete Code Explanation

## Architecture Overview

```
User Interface (Compose)
    ↓
MainViewModel (State Management)
    ↓
SimpleSearchRepository (Search Logic)
    ↓
API Clients (LetsFG, Ignav, SerpApi, OctoTrip)
    ↓
Data Stores (Settings, Flights, PriceHistory)
```

## Data Layer

### Models.kt
Core data classes:
- `FlightCard`: One outbound+return pairing with price, times, links
- `Offer`: Raw search result from API
- `ReturnLeg`: Return flight details
- `BookingLink`: Booking URL with provider name/price

### FlightRules.kt
Business logic:
- `group()`: Creates FlightCards from offers (one per outbound+return pair)
- `filterOffers()`: Applies time/stops filters
- `parseDateTime()`: Parses ISO timestamps (handles timezones)
- `formatTime()`, `formatDay()`: Display formatting

### SettingsStore.kt
SharedPreferences wrapper:
- Search source (LetsFG/Ignav)
- Route (origin/destination)
- Dates (outbound/return)
- Filters (airlines, stops, times)
- API keys (LetsFG, Ignav, SerpApi, GitHub)

### SelectedFlightsStore.kt
Persistent flight storage:
- `save()`, `load()`, `updatePrices()`, `clear()`
- Thread-safe (synchronized)

## API Clients

### LetsfgApi.kt
- POST `/api/search` → search_id
- GET `/api/results/{id}` → poll for results
- OAuth2 authentication
- Rate limit handling (429)

### IgnavApi.kt
- POST `/api/fares/round-trip` → fares
- POST `/api/fares/booking-links` → booking URLs
- `market:"SA"` for SAR currency
- Time range filtering (`departure_time_range`)

### SerpApiClient.kt
- GET `google_flights` → search results
- `flightUrl()` → builds Google Flights URL locally
- `bookingUrl()` → resolves booking token to URL
- Caching per booking token

### OctoTripApi.kt
- MCP JSON-RPC protocol
- `initialize()` → `tools/call` flow
- Anti-bot User-Agent required

## UI Layer

### MainActivity.kt
- Mode switching (Simple/Extra)
- Refresh button + pull-to-refresh
- Flight management (add/delete)
- Navigation state

### SimpleHomeScreen.kt
- Flight cards with inline tabs
- Price display (`currentPrice ?: ret.price`)
- Booking links (airline + 3rd party)
- Details/History/Chart tabs
- Delete confirmation dialog

### SimpleSearchScreen.kt
- Search form (origin/destination/dates)
- 24-hour time selector dropdowns
- Stops filter (Any/Direct/1 stop)
- DateRangePicker calendar
- Results with checkboxes

### SimpleSearchRepository.kt
- `search()`: Search + filter + group + fetch links
- `refreshAll()`: Search both APIs + merge + dedupe
- `filterOffers()`: Client-side time/stops filtering
- `fetchBookingLinks()`: Parallel link fetching with cache

## Background Processing

### PriceCheckWorker.kt
- Periodic price checking
- Records to PriceHistoryStore
- Updates currentPrice on flights
- Notification on price changes

## Key Algorithms

### Flight Grouping (FlightRules.group)
```
Input: List<Offer> (raw search results)
1. Group by outbound flight key
2. For each outbound, find all return pairings
3. Create FlightCard per (outbound, return) pair
4. Set price = return's package price
Output: List<FlightCard>
```

### Deduplication (refreshAll)
```
Input: allOffers from both APIs
1. Group by (outbound + return) key
2. Keep cheapest offer per group
3. Result: deduped offers
```

### Client-side Filtering (filterOffers)
```
Input: offers + SearchFilters
1. Time filter: extract hour from departureTime/arrivalTime
2. Stops filter: stops <= maxStops
Output: filtered offers
```

### Price Display Logic
```
Display price = currentPrice (from PriceCheckWorker)
              ?: ret.price (from search pairing)
              ?: offer.price (fallback)
Never use booking link prices (they can be wrong)
```

## Error Handling

### Retry Logic (search())
- 3 attempts for transient errors
- NO retry for 429/401/403 (permanent)
- Falls back to cache on failure
- Reports progress to UI

### Cache Strategy
- SearchCache: 1-hour TTL, disk-based
- IgnavLinkCache: 24-hour TTL, in-memory
- SerpApiClient.resolvedUrls: per-token cache

## Thread Safety
- SelectedFlightsStore: synchronized(lock)
- ApiUsageStore: atomic operations
- SerpApiClient: Collections.synchronizedMap
- All network calls: Dispatchers.IO
