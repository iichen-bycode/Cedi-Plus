package com.creditcedi.app

import android.app.Application
import android.app.Activity
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.facebook.FacebookSdk

class CediPlusApplication : Application() {
    private val attributionStore by lazy { getSharedPreferences("attribution", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FACEBOOK_APP_ID.isNotBlank() && BuildConfig.FACEBOOK_CLIENT_TOKEN.isNotBlank()) {
            FacebookSdk.sdkInitialize(this)
        }
        val environment = if (BuildConfig.DEBUG) {
            AdjustConfig.ENVIRONMENT_SANDBOX
        } else {
            AdjustConfig.ENVIRONMENT_PRODUCTION
        }
        val config = AdjustConfig(this, APP_TOKEN, environment)
        config.setOnAttributionChangedListener { attribution ->
            attributionStore.edit()
                .putString("channel", attribution.network?.takeIf(String::isNotBlank) ?: DEFAULT_CHANNEL)
                .putString("campaign", attribution.campaign.orEmpty())
                .apply()
        }
        Adjust.initSdk(config)
        attributionStore.edit().putString("channel", attributionStore.getString("channel", DEFAULT_CHANNEL)).apply()
        Adjust.getAdid { adid -> attributionStore.edit().putString("adjustId", adid.orEmpty()).apply() }
        registerActivityLifecycleCallbacks(AdjustLifecycleCallbacks())
        readInstallReferrer()
    }

    private fun readInstallReferrer() {
        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                    runCatching { referrerClient.installReferrer.installReferrer }
                        .onSuccess { value -> attributionStore.edit().putString("nativeChannel", value).apply() }
                }
                referrerClient.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() = Unit
        })
    }

    private class AdjustLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = Adjust.onResume()
        override fun onActivityPaused(activity: Activity) = Adjust.onPause()
        override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val APP_TOKEN = "ulzlndkx0cg0"
        const val DEFAULT_CHANNEL = "Other"
    }
}
