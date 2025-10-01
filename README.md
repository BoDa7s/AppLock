## [Releases History](../../releases)

Explore previous versions, changelogs, and downloadable artifacts on the project's Releases page.
# AppLock-Android
AppLock-Android is A Jetpack Compose that turns an Android device into an app locker. The app watches foreground apps with an <code>AccessibilityService</code>, block access with a fullscreen overlay, and gate everything behind a master passcode with optional biometric fallback.

## Project Stage
- Internal dogfooding builds only; no Play Store or GitHub release artifacts are published yet.
- Targets Android 12 (API 31) and above. The project compiles with SDK 36, targets SDK 34, and uses Kotlin 2.0.21 with the Compose 2024.10.00 BOM.
- Current focus: finalize fundemintal features, resolve remaining bugs, stabilize accessibility/onboarding UX, and gatherr testing feedback to prepare the app for its first official release.

## Features
- Compose-driven onboarding that guides users through passcode setup and locked-app selection.
- <code>AppLockAccessibilityService</code> watches window-state changes and launches a lock screen when a protected package comes to the foreground.
- Fullscreen <code>LockOverlayActivity</code> prompts for the passcode or triggers fingerprint authentication when enabled.
- Settings surface lets users change the passcode, toggle biometrics authentication, and choose light, dark, or system themes. 

## Requirements
- Android Studio Koala or newer with Android Gradle Plugin 8.10.
- JDK 11 (configured by the Gradle wrapper).
- Android device or emulator running Android 12 (API 31) or higher.
- Project targets SDK 35 and uses Kotlin 2.0.21 with the 2024.09 Compose BOM.

## Getting Started
Download the latest `AdamAppLock` APK from the [Releases](../../releases) page on GitHub and install it on your Android device (enable "Install unknown apps" if prompted).

## Using the App
1. Launch the app and follow the passcode setup flow.
2. Enable the **App Lock** accessibility service when redirected to system settings.
3. From the "Select Apps to Lock" screen, toggle the apps you want to protect.
4. Optionally open the settings panel to:
   - Change the master passcode.
   - Enable biometric unlock.

Once configured, switching to a locked app triggers the overlay requiring passcode or biometrics. Screen off/on events or idle timeouts trigger re-authentication based on your chosen auto-lock policy.

## Permissions
| Permission | Reason |
|------------|--------|
| <code>android.permission.POST_NOTIFICATIONS</code> | Warn users when the accessibility service is disabled. |
| <code>android.permission.BIND_ACCESSIBILITY_SERVICE</code> | Required to observe foreground apps and enforce locks. |

## Project Structure

        AppLock/
        ├─ build.gradle.kts
        ├─ settings.gradle.kts
        └─ app/
        ├─ build.gradle.kts
        └─ src/main/
        ├─ AndroidManifest.xml
        ├─ java/com/example/adamapplock/
        │ ├─ MainActivity.kt
        │ ├─ AppLockAccessibilityService.kt
        │ ├─ LockOverlayActivity.kt
        │ ├─ Prefs.kt
        │ └─ security/PasswordRepository.kt
        └─ res/
        ├─ xml/accessibility_service_config.xml


