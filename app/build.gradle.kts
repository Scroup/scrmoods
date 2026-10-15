plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.scrmoods.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scrmoods.app"
        minSdk = 26
        // Google Play requires apps to target the latest Android (API 36 since 31 August 2026).
        targetSdk = 36
        // Every GitHub build gets a higher version number, so a new APK installs over the old one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // A fixed signing key kept in the repo. Because every build is signed with the same key,
    // new versions install as updates and your data on the phone is kept.
    signingConfigs {
        create("fixed") {
            storeFile = file("signing/scrmoods.jks")
            storePassword = "scrmoods"
            keyAlias = "scrmoods"
            keyPassword = "scrmoods"
        }
        // Google Play: the private upload key, from the repository's secrets (never stored in the repo).
        // Google then re-signs the app with its own key (Play App Signing).
        val uploadStore = System.getenv("UPLOAD_STORE_FILE")
        if (uploadStore != null && file(uploadStore).exists()) create("upload") {
            storeFile = file(uploadStore)
            storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }

    // Two versions of the same app:
    //  github: installed from the GitHub release; can download and install its own updates.
    //  play:   for Google Play, which updates it itself (no self-update, no internet at all).
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            signingConfig = signingConfigs.getByName("fixed")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("fixed")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = null // set per store above (github: the fixed key)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
