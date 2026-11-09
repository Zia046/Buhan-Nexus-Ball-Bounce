plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from environment variables (set by GitHub Actions secrets).
// When they are missing (e.g. a local build) the release APK is signed with the debug key.
val bnKeystorePath: String? = System.getenv("BN_KEYSTORE_PATH")
val hasReleaseKey = bnKeystorePath != null && file(bnKeystorePath).exists()

android {
    namespace = "com.burhannexus.ballbounce"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.burhannexus.ballbounce"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(bnKeystorePath!!)
                storePassword = System.getenv("BN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BN_KEY_ALIAS")
                keyPassword = System.getenv("BN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "Ball Bounce")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Ball Bounce (Debug)")
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
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
