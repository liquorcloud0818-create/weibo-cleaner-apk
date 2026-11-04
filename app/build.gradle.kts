plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.starry.weibocleaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.starry.weibocleaner"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
}
