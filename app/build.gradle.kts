plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingFile = System.getenv("SIGNING_KEY_FILE")
val signingPassword = System.getenv("KEYSTORE_PASSWORD")
val signingAlias = System.getenv("KEY_ALIAS")
val signingKeyPassword = System.getenv("KEY_PASSWORD")
val signedRelease = !signingFile.isNullOrBlank() &&
    !signingPassword.isNullOrBlank() &&
    !signingAlias.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.sockc.niuenhance"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sockc.niuenhance"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signedRelease) {
            create("fixedRelease") {
                storeFile = file(signingFile!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            // Do not obfuscate LSPosed entry class until keep rules are verified.
            isMinifyEnabled = false
            if (signedRelease) {
                signingConfig = signingConfigs.getByName("fixedRelease")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    // The running LSPosed framework supplies this API; never use implementation.
    compileOnly("de.robv.android.xposed:api:82")
}
