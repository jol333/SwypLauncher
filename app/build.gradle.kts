import java.util.Properties
import java.io.FileInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

// AGP 9.0: Configure Kotlin via kotlin extension instead of kotlinOptions
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.joyal.swyplauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.joyal.swyplauncher"
        minSdk = 34
        targetSdk = 36
        versionCode = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
            .format(DateTimeFormatter.ofPattern("yyyyMMddHH"))
            .toInt()
        versionName = "1.6"
    }

    // Load keystore properties if available
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 full mode. If something only breaks in release, look for a class that is
            // created by name (reflection) and see the notes in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        // Benchmark build type for startup profiling
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    
    // AGP 9.0: Use androidResources for locale filtering
    androidResources {
        localeFilters += listOf("en", "ml", "zh", "es", "pt", "de", "hi")
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    
    // ML Kit
    implementation(libs.mlkit.digital.ink.recognition)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    
    // Coil for image loading
    implementation(libs.coil.compose)

    // Biometric
    implementation(libs.androidx.biometric)
    
    // Reorderable
    implementation(libs.reorderable)
    
    // Glance (App Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Glance's WorkManager, declared directly for LauncherApplication's on-demand initialization
    implementation(libs.androidx.work.runtime)

    // Baseline Profile & Startup Tracing
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
}