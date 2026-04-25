import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Load signing credentials from keystore.properties (git-ignored).
// Falls back to environment variables for CI/CD pipelines.
val keystoreProps = Properties().also { props ->
    val file = rootProject.file("keystore.properties")
    if (file.exists()) props.load(file.inputStream())
}

fun keystoreProp(key: String): String =
    keystoreProps.getProperty(key) ?: System.getenv(key.uppercase().replace('.', '_')) ?: ""

android {
    namespace = "com.travellog.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.travellog.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val sf = keystoreProp("storeFile")
            if (sf.isNotEmpty()) storeFile = file(sf)
            storePassword = keystoreProp("storePassword")
            keyAlias      = keystoreProp("keyAlias")
            keyPassword   = keystoreProp("keyPassword")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "TravelLog-${variant.versionName}.apk"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    // Reproducible builds
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Maps — MapLibre (free, no API key needed)
    implementation(libs.maplibre.android)
    // Location — GPS tracking via FusedLocationProviderClient
    implementation(libs.play.services.location)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.exifinterface)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media
    implementation(libs.media3.exoplayer)

    // JSON
    implementation(libs.gson)

    // Vosk — on-device offline speech recognition
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.espresso)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
}
