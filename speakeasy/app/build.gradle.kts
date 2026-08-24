plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tarkeshstack.speakeasy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tarkeshstack.speakeasy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // Debug DEX with zero shrinking pulls in every unused class from Compose,
        // Play Services, and ML Kit's transitive dependency graph — 43MB of classes.dex
        // alone before this. R8 code shrinking (still debuggable, no resource shrinking
        // so ML Kit's own runtime-loaded res/raw and assets/ files stay untouched) cuts
        // that down a lot without changing behavior; AARs ship their own consumer
        // ProGuard/keep rules, so no extra rules should be needed here.
        debug {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    // ML Kit bundles native inference libraries per CPU architecture, which balloons a
    // universal APK to ~80MB. Split into one APK per ABI instead: arm64-v8a is what
    // virtually every real phone since ~2017 uses, x86_64 is what the CI smoke-test
    // emulator uses — no need to also ship the now-rare 32-bit armeabi-v7a/x86 slices.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.mlkit:translate:17.0.2")
    implementation("com.google.mlkit:language-id:17.0.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
