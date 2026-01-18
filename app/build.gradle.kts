plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.x + Compose
}

android {
    namespace = "com.awi.lock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.awi.lock"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.8"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Add more Kotlin compiler flags here if needed
    }
}

dependencies {

    implementation(libs.androidx.core.splashscreen)


    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))

    // Compose core
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.ui.tooling)

    // Material3 + extras
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.material.icons.extended)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // AndroidX core + appcompat
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Biometric + Security Crypto
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Material (Views)
    implementation(libs.material)

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

