// Root build file — no external dependencies, no extra plugins.
// Kotlin compilation is provided by AGP built-in Kotlin support (AGP 9.x),
// enabled by default since AGP 9.0.
//
// Toolchain versions (verified against the real local environment on
// 23/09/2026 — see IMPLEMENTATION_REPORT.md):
//   - Gradle 9.3.1  (the exact version Google documents as compatible with AGP 9.1.x)
//   - AGP 9.1.0
//   - Kotlin 2.2.21 (>= AGP 9.1 minimum KGP 2.2.10; pinned via buildscript
//     classpath, the documented way to set KGP with built-in Kotlin)

plugins {
    id("com.android.application") version "9.1.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}
