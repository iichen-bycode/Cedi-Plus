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
val apiBaseUrl = localProperties.getProperty("cedi.apiBaseUrl", "https://api-ghana.kamfams.com")
val apiLogEnabled = localProperties.getProperty("cedi.apiLogEnabled", "false").toBoolean()
val apiHeaderPackage = localProperties.getProperty("cedi.headerPackage", "tuopes.nmbesf.truehgc")
val facebookAppId = localProperties.getProperty("cedi.facebookAppId", "")
val facebookClientToken = localProperties.getProperty("cedi.facebookClientToken", "")

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
        buildConfigField("String", "API_HEADER_PACKAGE", "\"$apiHeaderPackage\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("boolean", "API_LOG_ENABLED", apiLogEnabled.toString())
        buildConfigField("String", "FACEBOOK_APP_ID", "\"$facebookAppId\"")
        buildConfigField("String", "FACEBOOK_CLIENT_TOKEN", "\"$facebookClientToken\"")
        resValue("string", "facebook_app_id", facebookAppId)
        resValue("string", "facebook_client_token", facebookClientToken)
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.facebook.android:facebook-core:19.0.1")
    implementation("aws.sdk.kotlin:s3:1.5.123")
    implementation("com.google.android.gms:play-services-ads-identifier:18.3.0")
}


