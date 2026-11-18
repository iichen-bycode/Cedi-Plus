package com.creditcedi.app

import android.app.Application
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig

class CediPlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val environment = if (BuildConfig.DEBUG) {
            AdjustConfig.ENVIRONMENT_SANDBOX
        } else {
            AdjustConfig.ENVIRONMENT_PRODUCTION
        }
        val config = AdjustConfig(this, APP_TOKEN, environment)
        Adjust.initSdk(config)
    }

    private companion object {
        const val APP_TOKEN = "ulzlndkx0cg0"
    }
}
