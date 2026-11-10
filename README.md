# FlightWatch - Android Flight Price Tracker

An Android app that tracks flight prices across multiple APIs, shows price history charts, and backs up data to GitHub.

## 📱 Quick Install (No Build Needed)

Download `flightwatch.apk` and install on your Android device:
```
adb install flightwatch.apk
```
Or transfer the APK to your device and tap to install.

## 🏗️ Build From Source

### Prerequisites
- Android Studio Hedgehog (2023.1+) or newer
- JDK 17+ (bundled with Android Studio)
- Android SDK 36 (installed via SDK Manager)

### Build Steps
```bash
# 1. Clone this repo
git clone https://github.com/abdgalaxy36-code/flightwatch.git
cd flightwatch

# 2. Open in Android Studio
# File > Open > select the project folder

# 3. Build APK
./gradlew assembleDebug

# 4. APK location
# app/build/outputs/apk/debug/app-debug.apk
```

### Build via Command Line (without Android Studio)
```bash
# Install JDK 17
sudo apt install openjdk-17-jdk

# Install Android SDK
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME
# Download commandline-tools from developer.android.com
# Then install SDK 36:
yes | sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-36" "build-tools;34.0.0"

# Build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
```

### Install on Device
```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or transfer APK and tap to install
```

## 📁 Project Structure

```
app/src/main/kotlin/com/flightwatch/app/
├── FlightWatchApp.kt          # Application class (singletons)
├── data/                      # Data layer
│   ├── Models.kt              # FlightCard, Offer, ReturnLeg data classes
│   ├── FlightRules.kt         # Flight grouping, filtering, time parsing
│   ├── SettingsStore.kt       # App settings (SharedPreferences)
│   ├── SelectedFlightsStore.kt # User's saved flights (persistent)
│   ├── PriceHistoryStore.kt   # Price history for charts
│   ├── HistoryStore.kt        # SQLite price history
│   ├── ApiUsageStore.kt       # API usage tracking
│   ├── TokenStore.kt          # Auth tokens
│   ├── LetsfgApi.kt           # LetsFG search API client
│   ├── IgnavApi.kt            # Ignav search API client
│   ├── SerpApiClient.kt       # Google Flights URLs via SerpApi
│   ├── OctoTripApi.kt         # OctoTrip MCP API client
│   ├── GitHubBackup.kt        # GitHub Gist backup/restore
│   ├── SearchCache.kt         # Disk cache for search results
│   └── IgnavLinkCache.kt      # Cache for ignav booking links
├── ui/
│   ├── MainActivity.kt        # Main entry, mode switching
│   ├── MainViewModel.kt       # State management
│   ├── AuthCoordinator.kt     # OAuth flow (LetsFG)
│   ├── simple/                # Simple Mode UI
│   │   ├── SimpleHomeScreen.kt    # Flight cards + tabs
│   │   ├── SimpleSearchScreen.kt  # Search form + calendar + filters
│   │   ├── SimpleSearchRepository.kt # Search + link fetching
│   │   ├── SimpleSettingsScreen.kt   # Settings
│   │   └── PriceHistoryChart.kt     # Chart rendering
│   ├── extra/                 # Extra Mode UI
│   │   ├── ExtraHomeScreen.kt
│   │   └── ExtraSettingsScreen.kt
│   └── FlightsScreen.kt       # Legacy flights screen
├── work/
│   └── PriceCheckWorker.kt    # Background price checking
└── diagnostics/               # Debug tools
    ├── AppLogger.kt           # Centralized logging
    ├── CrashReporter.kt       # Crash handling
    ├── DeviceDiagnostics.kt   # Device info
    └── DiagnosticsScreen.kt   # Debug UI
```

## 🔧 Key Features

### Multi-API Search
- **LetsFG** - Main search API (airline-only fares)
- **Ignav** - Alternative search API (with `market:"SA"` for SAR)
- **SerpApi** - Google Flights URLs for 3rd party prices
- **OctoTrip** - MCP-based search API

### Flight Cards
- Outbound + Return pairings as separate cards
- Price from search results (never from booking links)
- Google Flights link for 3rd party prices
- Inline Details / History / Chart tabs
- Delete with confirmation dialog

### Filters (24-hour time selector)
- **Stops**: Any / Direct / 1 stop
- **Departure time**: From/To hour dropdowns (00:00-23:00)
- **Arrival time**: From/To hour dropdowns
- Applied client-side AND sent to API

### Price Tracking
- `PriceCheckWorker` checks prices periodically
- Records to `PriceHistoryStore` for charts
- Updates `currentPrice` on flight cards
- Shows price changes (↑↓) in refresh

### Refresh
- Searches BOTH APIs (LetsFG + ignav)
- Deduplicates by outbound+return pairing
- Only fetches links for YOUR selected flights
- Loading bar with real progress
- Pull-to-refresh gesture

### Backup
- Pushes to GitHub Gist
- Saves flights, settings, price history
- Restore from Gist

## 🔑 API Keys

### LetsFG (Free tier available)
1. Sign up at letsfg.co
2. Get API credentials
3. Enter in Settings > Search API

### Ignav (Free tier: 1,000 requests/month)
1. Sign up at ignav.com
2. Get API key
3. Enter in Settings > Search API

### SerpApi (Free tier: 250 requests/month)
1. Sign up at serpapi.com
2. Get API key
3. Enter in Settings

### GitHub (for backup)
1. Go to github.com > Settings > Developer settings > Personal access tokens
2. Generate new token (classic)
3. Select `gist` scope
4. Enter in Settings > Backup

## 📊 Data Storage

- **SharedPreferences**: Settings, flights, API usage
- **SQLite**: Price history (HistoryStore)
- **JSON files**: Price history (PriceHistoryStore)
- **Disk cache**: Search results (SearchCache)

## 🐛 Troubleshooting

### "No results" on search
- Check API key is valid
- Check internet connection
- Try different dates

### Prices not updating
- Refresh button re-searches both APIs
- Check PriceCheckWorker is scheduled
- Verify API credits not exhausted

### Backup fails
- Verify GitHub token has `gist` scope
- Check internet connection

## 📄 License

MIT
