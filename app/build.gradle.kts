plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Číslo verze = číslo sestavení na GitHubu (aktualizace v telce porovnává s tagem vN).
val vCode = (findProperty("vCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "cz.pupik.televize"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.pupik.televize"
        minSdk = 26
        targetSdk = 28 // telka má Android 9
        versionCode = vCode
        versionName = "1.$vCode"
    }

    signingConfigs {
        create("tv") {
            storeFile = file("televize.jks")
            storePassword = "televize"
            keyAlias = "televize"
            keyPassword = "televize"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("tv")
        }
        debug { signingConfig = signingConfigs.getByName("tv") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
