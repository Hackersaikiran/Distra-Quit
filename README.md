# Distra-Quit

A digital wellbeing Android application that helps users reduce smartphone addiction through smart features like grayscale filtering, app blocking, and screen time management.

## Overview

Distra-Quit (formerly DetoxDroid) is designed to help users reclaim control of their smartphone usage without punitive measures. Instead of time-based contracts or financial penalties, it provides tools that work seamlessly in the background to reduce compulsive phone use.

## Key Features

- **Grayscale Overlay System**: Permission-free grayscale filtering using AccessibilityService overlays (no root or ADB required)
- **Smart App Exceptions**: Select specific apps to remain in color while the rest of your phone stays grayscale
- **Do Not Disturb Automation**: Automatically manage notification settings based on schedules
- **App Deactivation**: Temporarily hide and disable distracting apps during productive hours
- **Infinite Scroll Detection**: Identify and break doom-scrolling patterns
- **Scheduled Automation**: Configure features to activate based on time of day
- **Daily Screen Time Limits**: Set color screen time quotas with automatic grayscale enforcement

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Coroutines and Flow
- **Dependency Injection**: Hilt
- **Build System**: Gradle (Kotlin DSL)
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 13+ (API 33+)

## Project Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK with API 26-34
- Git

### Clone the Repository

```bash
git clone https://github.com/Hackersaikiran/Distra-Quit.git
cd Distra-Quit
```

### Build Instructions

#### Using Android Studio (Recommended)

1. Open Android Studio
2. Select **File** → **Open** and navigate to the cloned directory
3. Wait for Gradle sync to complete
4. Connect an Android device or start an emulator
5. Click **Run** (Shift+F10) to build and install

#### Using Command Line

**Windows:**
```bash
.\gradlew clean assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
```

**macOS/Linux:**
```bash
./gradlew clean assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Running Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Configuration

### Required Permissions

The app requires the following permissions to function properly:

1. **Accessibility Service** - For overlay rendering and app detection
2. **Usage Stats Access** - For screen time tracking
3. **Do Not Disturb Access** - For notification management (optional)
4. **Device Admin** - For app deactivation features (optional)

### First-Time Setup

1. Install the APK on your Android device
2. Open the app and grant **Accessibility Service** permission when prompted
3. Enable **Usage Stats Access** in Android Settings → Apps → Special Access → Usage Access
4. Configure your desired features from the home screen
5. Add apps to exception lists as needed

## Development

### Project Structure

```
app/src/main/java/com/flx_apps/digitaldetox/
├── features/                    # Feature implementations
│   ├── GrayscaleAppsFeature.kt # Grayscale overlay logic
│   ├── DisableAppsFeature.kt   # App blocking
│   └── ...
├── system_integration/          # Android system integrations
│   ├── GrayscaleOverlayManager.kt  # Overlay rendering
│   ├── DetoxDroidAccessibilityService.kt
│   └── ...
├── ui/                         # Compose UI screens
│   ├── screens/
│   └── widgets/
└── data/                       # Data layer
    └── DataStore.kt
```

### Key Components

**GrayscaleOverlayManager**  
Singleton manager that handles full-screen grayscale overlay rendering using `TYPE_ACCESSIBILITY_OVERLAY`. Supports dynamic alpha adjustment for intensity control.

**GrayscaleAppsFeature**  
Main feature logic that determines when to show/hide grayscale based on:
- App exception lists (ONLY_LIST or NOT_LIST modes)
- Daily color screen time limits
- Schedule settings
- Fullscreen detection

**DetoxDroidAccessibilityService**  
Core accessibility service that monitors app changes and triggers feature actions.

### Customization

#### Adjusting Grayscale Intensity

Edit `GrayscaleAppsFeature.kt` line 260:

```kotlin
// Default values: extraDim ON = 0.9f (darker), OFF = 0.7f (lighter)
val alpha = if (extraDim) 0.9f else 0.7f

// Examples:
// Maximum contrast: if (extraDim) 1.0f else 0.75f
// Subtle effect: if (extraDim) 0.7f else 0.5f
```

Alpha range: `0.5f` (very light) to `1.0f` (fully opaque)

## Troubleshooting

### Grayscale Not Working

1. Verify **Accessibility Service** is enabled: Settings → Accessibility → Distra-Quit
2. Check that the app is selected in the feature's exception list
3. Ensure **Daily Color Screen Time** is set to 0 for immediate grayscale
4. Confirm **Ignore non-fullscreen apps** setting matches your use case

### App Not Detecting Opens

1. Grant **Usage Stats Access** permission
2. Enable **Accessibility Service**
3. Disable battery optimization for Distra-Quit
4. Check logcat for debugging information

### Pixel/Android 12+ "Access Denied" Error

1. Open **Settings** → **Apps** → **Distra-Quit**
2. Tap overflow menu (⋮) → **Allow restricted settings**
3. Grant permission

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

### How to Contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the GNU General Public License v3.0 - see the LICENSE file for details.

## Acknowledgments

Built with inspiration from the Center for Humane Technology's principles and research on attention-grabbing design patterns in mobile applications.

