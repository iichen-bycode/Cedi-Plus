plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.creditcedi.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 25
    }
}

dependencies {
    api("androidx.constraintlayout:constraintlayout:2.2.2")
}

