plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tw.fitcar.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "tw.fitcar.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.4.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    // Signing material is injected only into the CI release step. No debug fallback.
    val fitStorePath = System.getenv("FIT_KEYSTORE_PATH")
    val fitPassword = System.getenv("FIT_KEYSTORE_PASSWORD")
    val fitAlias = System.getenv("FIT_KEY_ALIAS")
    val hasFitSigning = !fitStorePath.isNullOrBlank() && !fitPassword.isNullOrBlank() && !fitAlias.isNullOrBlank()
    signingConfigs {
        if (hasFitSigning) create("fitRelease") {
            storeFile = file(fitStorePath!!)
            storePassword = fitPassword
            keyAlias = fitAlias
            keyPassword = fitPassword
            storeType = "PKCS12"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasFitSigning) signingConfig = signingConfigs.getByName("fitRelease")
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
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
