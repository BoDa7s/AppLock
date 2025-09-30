## [Releases History](../../releases)

Explore previous versions, changelogs, and downloadable artifacts on the project's Releases page.
# AppLock-Android

A Jetpack Compose sample that turns an Android device into an app locker. The app teaches how to watch foreground apps with an <code>AccessibilityService</code>, block access with a fullscreen overlay, and gate everything behind a master passcode with optional biometric fallback.

## Features
- Compose-driven onboarding that guides users through passcode setup and locked-app selection.
- <code>AppLockAccessibilityService</code> watches window-state changes and launches a lock screen when a protected package comes to the foreground.
- Fullscreen <code>LockOverlayActivity</code> asks for the shared passcode before the target app is shown.
- Optional biometric authentication, configurable auto-lock timers, and a basic settings surface exposed via Material 3 components.
- Background <code>LockService</code> monitors screen on/off events so the lock policy is re-applied after timeouts or device unlocks.

## How it Works
| Component | Responsibility |
|-----------|----------------|
| <code>MainActivity</code> (<code>app/src/main/java/com/example/adamapplock/MainActivity.kt</code>) | Compose UI for authentication, passcode setup, app selection, and settings. Persists state in <code>SharedPreferences</code>. |
| <code>AppLockAccessibilityService</code> | Accessibility hook that detects app launches and starts the overlay when a locked package appears. |
| <code>LockOverlayActivity</code> | Fullscreen overlay that blocks the target app until the stored passcode is entered. |
| <code>LockService</code> | Foreground monitor that listens for screen events and forces re-authentication based on the auto-lock policy. |
| <code>SharedPreferences</code> helpers | Store passcode, biometric opt-in, auto-lock timer, and locked package list. |

## Requirements
- Android Studio Koala or newer with Android Gradle Plugin 8.10.
- JDK 11 (configured by the Gradle wrapper).
- Android device or emulator running Android 12 (API 31) or higher.

Project targets SDK 35 and uses Kotlin 2.0.21 with the 2024.09 Compose BOM.

## Getting Started
1. Clone or download the repository.
2. Open the project root in Android Studio and let it sync Gradle.
3. Build and run on a device/emulator with:

        ./gradlew installDebug

4. Grant the app notification permission (Android 13+) when prompted so service status warnings can be shown.

## Using the App
1. Launch the app and follow the passcode setup flow.
2. Enable the **App Lock** accessibility service when redirected to system settings.
3. From the "Select Apps to Lock" screen, toggle the apps you want to protect.
4. Optionally open the settings panel to:
   - Change the master passcode.
   - Enable biometric unlock.
   - Choose an auto-lock interval (immediate, 1–10 minutes, or on screen lock).

Once configured, switching to a locked app triggers the overlay requiring passcode or biometrics. Screen off/on events or idle timeouts trigger re-authentication based on your chosen auto-lock policy.

## Permissions
| Permission | Reason |
|------------|--------|
| <code>android.permission.POST_NOTIFICATIONS</code> | Warn users when the accessibility service is disabled. |
| <code>android.permission.BIND_ACCESSIBILITY_SERVICE</code> | Required to observe foreground apps and enforce locks. |

## Project Structure

        applock-android/
        ├─ app/
        │  ├─ src/main/
        │  │  ├─ java/com/example/adamapplock/
        │  │  │  ├─ MainActivity.kt
        │  │  │  ├─ AppLockAccessibilityService.kt
        │  │  │  └─ LockOverlayActivity.kt
        │  │  └─ res/
        │  │     ├─ xml/accessibility_service_config.xml
        │  │     └─ values/strings.xml
        ├─ build.gradle.kts
        ├─ gradle/libs.versions.toml
        └─ settings.gradle.kts


