plugins {
    id("com.android.application")
}

android {
    namespace = "com.minh.statusbarclock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minh.statusbarclock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Product code: zero external dependencies (Android SDK + Kotlin stdlib only).
    // Kotlin stdlib is added automatically by AGP built-in Kotlin.
    // Test-only: JUnit 4 to satisfy the mandatory "./gradlew test" validation.
    testImplementation("junit:junit:4.13.2")
}
