# AGENTS.md

## Purpose
- Guide AI agents and humans through building, testing, and shipping the Android app.
- Builds are reproducible using the included Gradle Wrapper; never install a separate Gradle distribution.

## Repo Map
- `/app` — primary Android application module (`build.gradle.kts`, source, resources).
- `/gradle` — Gradle wrapper and version catalog (`libs.versions.toml`).
- `gradlew` / `gradlew.bat` — Gradle Wrapper scripts for Unix/Windows.
- `build.gradle.kts`, `settings.gradle.kts` — root project configuration.
- `README.md` — high-level project overview.
- `gradle.properties` — global Gradle configuration (do not commit secrets).
- `AGENTS.md` — this guide.

## Prerequisites
- **Git** — required to clone the repository.
- **JDK 17** (Temurin or Oracle). Verify via:
  ```bash
  java -version
  ```
  Expected output should report `openjdk version "17.x"`.
- **Android SDK** with commandline-tools. Install via Android Studio *or* `sdkmanager`.
- **Gradle Wrapper** — use `./gradlew` (macOS/Linux) or `gradlew.bat` (Windows). Do **not** install system Gradle.
- Optional tools (install only if needed):
  - **Emulator/Device drivers** for running instrumented tests.

## Environment Setup

### SDK & Tool Installation

| OS | Install JDK 17 | Install Android commandline-tools |
| --- | --- | --- |
| macOS | `brew install --cask temurin17` | `brew install --cask android-commandlinetools` |
| Windows | `winget install EclipseAdoptium.Temurin.17.JDK` *or* `choco install temurin17` | Download from <https://developer.android.com/studio#command-tools> and unzip to `C:\Android\cmdline-tools\latest` |
| Ubuntu/Debian | `sudo apt update && sudo apt install -y wget unzip` then install Temurin: `wget https://github.com/adoptium/temurin17-binaries/...` (or use `sudo apt install openjdk-17-jdk`) | Same download URL; unzip to `$HOME/Android/cmdline-tools/latest` |
| Fedora/RHEL | `sudo dnf install java-17-openjdk-devel` | Download and unzip as above |

> Ensure `cmdline-tools` directory structure matches `.../cmdline-tools/latest` (with `bin` inside).

### Environment Variables
Set these in your shell profile (macOS/Linux) or System Environment Variables (Windows).

```bash
# macOS/Linux
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"   # adjust for your install
export ANDROID_SDK_ROOT="$HOME/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

```powershell
# Windows PowerShell
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Eclipse Adoptium\jdk-17', 'User')
[System.Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', 'C:\Android', 'User')
$env:PATH += ';C:\Android\platform-tools'
```

Install required SDK packages after setting `sdkmanager` on PATH (e.g., `yes | sdkmanager --licenses`).

```bash
sdkmanager \
  "platforms;android-36" \
  "platforms;android-35" \
  "build-tools;36.0.0" \
  "platform-tools" \
  "cmdline-tools;latest"
```

Create `local.properties` at the project root pointing to your SDK:

```bash
# macOS/Linux
printf 'sdk.dir=%s/Android/sdk\n' "$HOME" > local.properties
```

```powershell
# Windows
"sdk.dir=C:\\Android" | Out-File -FilePath local.properties -Encoding utf8
```

## Project Facts
- **App name:** AdamAppLock
- **Package ID:** `com.example.adamapplock`
- **Min SDK / Target SDK:** 26 / 35 (compileSdk = 36)
- **Android Gradle Plugin:** 8.13.0 (managed via version catalog `libs.versions.toml`)
- **Gradle Wrapper:** 8.14.3 (see `gradle/wrapper/gradle-wrapper.properties`)
- **Required JDK:** 17
- **Kotlin:** 2.0.21
- **Compose:** Yes (Compose BOM `2024.10.00`; compose compiler supplied by `org.jetbrains.kotlin.plugin.compose`)
- **CI Platforms:** GitHub Actions (see template below; create `.github/workflows` as needed)
- **Primary Module:** `app`
- **Google Services / Firebase:** No
- **Signing:** Debug keystore generated automatically by Android Gradle plugin; configure release keystores externally if needed.
- **Native/NDK:** No native code.

## Dependency Management
- Dependencies are defined via the Gradle version catalog (`gradle/libs.versions.toml`). Update versions there and sync.
- `build.gradle.kts` files pull versions from the catalog using aliases.
- No Google Services/Firebase; if you add them, place `google-services.json` in `app/` and keep secrets out of git.
- For release signing, create `keystore.jks` outside the repo and reference it in `gradle.properties` using properties such as:
  ```properties
  RELEASE_STORE_FILE=/absolute/path/to/keystore.jks
  RELEASE_STORE_PASSWORD=***
  RELEASE_KEY_ALIAS=***
  RELEASE_KEY_PASSWORD=***
  ```
  Never commit actual credentials; use environment variables or encrypted CI secrets.

## Build Commands
```bash
./gradlew --version              # sanity check wrapper
./gradlew clean                  # clean build outputs
./gradlew :app:assembleDebug     # build debug APK (outputs to app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleRelease   # build release APK (signing config required)
./gradlew :app:installDebug      # install on connected device/emulator
./gradlew testDebugUnitTest      # run JVM unit tests
./gradlew connectedDebugAndroidTest   # run instrumented tests (requires running device/emulator)
./gradlew lint                   # Android Lint across modules
```

Artifacts are written under `app/build/outputs/` (APK: `apk/<buildType>/`, AAB if configured: `bundle/<buildType>/`).

## Code Style & Checks
- Kotlin/Java formatting: follow Android Studio defaults; no automatic formatter is configured in Gradle.
- Android Lint (`./gradlew lint`) runs static analysis; fix reported issues before shipping.
- No ktlint/detekt tasks are configured by default. You may add them if stricter linting is required.
- Configure `gradle.properties` with `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8` for stable builds on large projects.

## Continuous Integration (GitHub Actions Template)
Create `.github/workflows/android-ci.yml` with:
```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: |-
            platforms;android-36
            platforms;android-35
            build-tools;36.0.0
            platform-tools

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}
          restore-keys: |
            gradle-${{ runner.os }}-

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build & Test
        run: ./gradlew clean lint testDebugUnitTest assembleDebug
```
- Inject secrets (e.g., `FIREBASE_TOKEN`, signing passwords) via `env:` blocks referencing `${{ secrets.SECRET_NAME }}`. Never hardcode secrets in workflow files.
- For release signing, upload encrypted keystore to GitHub Actions secrets (e.g., using `actions/upload-artifact` or Base64) and decode during the workflow.

## Product Flavors & Build Variants
- Only default `debug` and `release` build types are defined. Use standard Gradle tasks as shown above.

## Troubleshooting
- **"SDK location not found"**: ensure `local.properties` exists with `sdk.dir=...` and `ANDROID_SDK_ROOT` points to a valid SDK.
- **Version mismatch errors**: confirm you are using JDK 17, Gradle Wrapper 8.14.3, and AGP 8.13.0.
- **Windows path issues**: avoid spaces in SDK path (e.g., use `C:\Android`). Run shells with admin privileges when needed.
- **Resource merge/compile failures**: run `./gradlew clean`, ensure resource qualifiers (e.g., `values-fr`) are correct, keep files UTF-8 encoded.
- **Gradle daemon OOM**: increase heap in `gradle.properties` (`org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`).
- **Proxy/corporate networks**: configure `gradle.properties`:
  ```properties
  systemProp.http.proxyHost=proxy.example.com
  systemProp.http.proxyPort=8080
  systemProp.https.proxyHost=proxy.example.com
  systemProp.https.proxyPort=8080
  ```

## Security & Secrets
- Do not commit keystores, service account files, or API keys. Keep them out of version control via `.gitignore`.
- Store secrets in environment variables, `gradle.properties` (excluded from VCS), or CI secret stores.
- Provide teammates with a sanitized `gradle.properties.example` containing placeholders, e.g.:
  ```properties
  RELEASE_STORE_FILE=/path/to/keystore.jks
  RELEASE_STORE_PASSWORD=changeme
  RELEASE_KEY_ALIAS=app_release
  RELEASE_KEY_PASSWORD=changeme
  ```

## Quick Start Checklist
1. Clone: `git clone <repo-url>`
2. Install JDK 17 (Temurin/Oracle).
3. Install Android SDK + required packages (`sdkmanager` command above).
4. Create `local.properties` with your `sdk.dir`.
5. Verify wrapper: `./gradlew --version`.
6. Build debug APK: `./gradlew assembleDebug`.
7. Find artifact: `app/build/outputs/apk/debug/app-debug.apk`.

## Reproducibility
- Pin versions: AGP 8.13.0, Gradle Wrapper 8.14.3, Kotlin 2.0.21, JDK 17.
- Dependency versions tracked in `gradle/libs.versions.toml` (acts as a lockfile).
- To refresh the wrapper safely: `./gradlew wrapper --gradle-version 8.14.3 --distribution-type all` (maintain checksum validation).

## Monorepo Note
- This repository contains a single Android app module (`app`). If additional modules appear, consult any nested `AGENTS.md` files for module-specific guidance before editing.