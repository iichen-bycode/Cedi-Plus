import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}
val apiSigningKey = localProperties.getProperty("cedi.signingKey", "")

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.creditcedi.app"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = 25
        targetSdk = 36
        applicationId = "com.cedi.plus.mobile.app"
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_SIGNING_KEY", "\"$apiSigningKey\"")
        buildConfigField("String", "API_HEADER_PACKAGE", "\"com.cedi.plus.mobile.app\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":access"))
    implementation(project(":credit"))
    implementation(project(":identity"))
    implementation(project(":account"))
    implementation("com.adjust.sdk:adjust-android:5.8.1")
    implementation("com.android.installreferrer:installreferrer:2.2")
}


