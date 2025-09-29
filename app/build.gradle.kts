plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")   // <— required with Kotlin 2.x
}

android {
    namespace = "com.example.adamapplock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.adamapplock"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.2"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    // With Kotlin 2.x + compose plugin, DO NOT set composeOptions{}.
    // The plugin supplies the matching compose compiler automatically.

    compileOptions {
        // Make Java compile to 17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Optional: if you use newer java.time APIs on old devices
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES"
            )
        }
    }

}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose BOM keeps compose libs in sync
    implementation(platform(libs.androidx.compose.bom.v20241000))

    // Core Compose
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.ui.tooling)

    // Material3
    implementation(libs.material3)
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.ui.text)

    // Compose host Activity
    implementation(libs.androidx.activity.compose.v192)

    // AndroidX core + lifecycle, etc.
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.androidx.lifecycle.runtime.ktx.v284)

    // Biometric
    implementation(libs.androidx.biometric.v110)

    // Optional (Material components in views)
    implementation(libs.material)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.material.icons.extended)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
