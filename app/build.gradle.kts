plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun loadKeystoreProperties(file: java.io.File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val index = line.indexOf('=')
            line.substring(0, index).trim() to line.substring(index + 1).trim()
        }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = loadKeystoreProperties(keystorePropertiesFile)

android {
    namespace = "com.crystalolympus.crystalolympusgame"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crystalolympus.crystalolympusgame"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.3"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties["storeFile"]
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties["storePassword"]
                keyAlias = keystoreProperties["keyAlias"]
                keyPassword = keystoreProperties["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    androidResources {
        // Sprite atlases are decoded at runtime, they must stay byte-identical in the APK.
        noCompress += listOf("webp", "mp3")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)

    // Install attribution analytics only (organic vs. non-organic on the AppsFlyer dashboard).
    // No config endpoint, no WebView routing, nothing in the app branches on the result.
    implementation("com.appsflyer:af-android-sdk:6.18.0")
    implementation("com.android.installreferrer:installreferrer:2.2")

    debugImplementation(libs.androidx.ui.tooling)
}
