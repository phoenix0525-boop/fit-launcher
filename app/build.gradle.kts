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
        versionCode = 38
        versionName = "0.4.31"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    // Signing material is injected only into the CI release step. No debug fallback.
    val fitStorePath = System.getenv("FIT_KEYSTORE_PATH")
    val fitPassword = System.getenv("FIT_KEYSTORE_PASSWORD")
    val fitAlias = System.getenv("FIT_KEY_ALIAS")
    val hasFitSigning = !fitStorePath.isNullOrBlank() && !fitPassword.isNullOrBlank() && !fitAlias.isNullOrBlank()
    val testPath = System.getenv("FIT_TEST_KEYSTORE_PATH")
    val testPassword = System.getenv("FIT_TEST_KEYSTORE_PASSWORD")
    val testAlias = System.getenv("FIT_TEST_KEY_ALIAS")
    val hasTestSigning = !testPath.isNullOrBlank() && !testPassword.isNullOrBlank() && !testAlias.isNullOrBlank()
    signingConfigs {
        if (hasTestSigning) create("fitTest") {
            storeFile = file(testPath!!)
            storePassword = testPassword
            keyAlias = testAlias
            keyPassword = testPassword
            storeType = "PKCS12"
        }
        if (hasFitSigning) create("fitRelease") {
            storeFile = file(fitStorePath!!)
            storePassword = fitPassword
            keyAlias = fitAlias
            keyPassword = fitPassword
            storeType = "PKCS12"
        }
    }
    buildTypes {
        debug { if (hasTestSigning) signingConfig = signingConfigs.getByName("fitTest") }
        release {
            // Production vehicle head unit is arm64. Debug keeps x86/x86_64 for emulator tests.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    packaging {
        resources {
            // kotlinx-coroutines debug probe metadata is not needed in production or this launcher.
            excludes += "DebugProbesKt.bin"
        }
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
    // 側載安裝也能套用 Compose 與本 App 的 Baseline Profile，減少首次開啟與首次滑動卡頓
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
