plugins {
    id("com.android.library") version "8.1.1"
    id("org.jetbrains.kotlin.android") version "1.8.22"
}

android {
    namespace = "com.exemple"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    // Intègre les outils de scraping de CloudStream
    implementation("com.lagradost:cloudstream3:latest.release")
}
