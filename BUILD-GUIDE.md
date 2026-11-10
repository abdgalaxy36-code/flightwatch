# FlightWatch - Build Guide

## Prerequisites

### Option A: Android Studio (Recommended)
1. Download Android Studio from https://developer.android.com/studio
2. Install with default settings
3. Open SDK Manager (Tools > SDK Manager)
4. Install:
   - Android 14 (API 36)
   - Android SDK Build-Tools 34
   - Android SDK Platform-Tools

### Option B: Command Line Only

#### Ubuntu/Debian
```bash
# Install JDK 17
sudo apt update
sudo apt install openjdk-17-jdk

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Install Android SDK
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools

# Download commandline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest

# Accept licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses

# Install SDK
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
  "platforms;android-36" \
  "build-tools;34.0.0" \
  "platform-tools"
```

#### macOS
```bash
# Install JDK 17
brew install openjdk@17

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Android SDK (same as Linux)
export ANDROID_HOME=$HOME/Library/Android/sdk
# ... (same steps as Linux)
```

#### Windows
1. Install JDK 17 from https://adoptium.net
2. Set JAVA_HOME environment variable
3. Download Android SDK commandline-tools
4. Run sdkmanager to install platforms

## Build Steps

### 1. Get Source Code
```bash
# Clone the repo
git clone https://github.com/abdgalaxy36-code/flightwatch.git
cd flightwatch

# OR copy files from this directory
```

### 2. Configure SDK Path
Create `local.properties` file:
```properties
sdk.dir=/path/to/your/android-sdk
```

### 3. Build Debug APK
```bash
# Using Gradle wrapper
./gradlew assembleDebug

# OR with explicit Java path
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk ./gradlew assembleDebug
```

### 4. Find APK
```
app/build/outputs/apk/debug/app-debug.apk
```

### 5. Install on Device
```bash
# Via ADB (device connected via USB)
adb install app/build/outputs/apk/debug/app-debug.apk

# OR transfer APK to device and tap to install
```

## Build for Release (Optional)

### Generate Signed APK
```bash
# Generate keystore
keytool -genkey -v -keystore release.keystore -alias flightwatch \
  -keyalg RSA -keysize 2048 -validity 10000

# Build release
./gradlew assembleRelease

# Sign APK
apksigner sign --ks release.keystore \
  --out flightwatch-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## Troubleshooting

### "SDK not found"
```bash
# Check local.properties
cat local.properties
# Should have: sdk.dir=/path/to/sdk

# Set ANDROID_HOME
export ANDROID_HOME=$HOME/android-sdk
```

### "JAVA_HOME not set"
```bash
# Find Java
which java
/usr/bin/java

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### "License not accepted"
```bash
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
```

### "Build failed: missing dependencies"
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug
```

## Testing

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Run Lint
```bash
./gradlew lintDebug
```

### Install and Launch
```bash
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.flightwatch.app/.ui.MainActivity
```

## Project Requirements

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 36 (Android 14)
- **Kotlin**: 1.9+
- **Compose**: 1.4+
- **JDK**: 17+

## Dependencies

- Jetpack Compose (UI)
- Material3 (Design)
- Gson (JSON parsing)
- WorkManager (Background tasks)
- kotlinx.coroutines (Async)
