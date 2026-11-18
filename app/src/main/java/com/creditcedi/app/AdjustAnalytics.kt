package com.creditcedi.app

import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.facebook.FacebookSdk
import android.content.Context
import com.creditcedi.core.Screen

/** App identity and API signing configuration supplied for this Cedi Plus build. */
internal object CediPlusConfig {
    const val HEADER_PACKAGE = BuildConfig.API_HEADER_PACKAGE
    const val API_SIGNING_KEY = BuildConfig.API_SIGNING_KEY
    const val API_SIGNING_KEY_PARAMETER = "lc=$API_SIGNING_KEY"
}

/** Adjust events use the event tokens configured for the Cedi Plus app. */
internal object AdjustAnalytics {
    private const val REGISTERED = "p7raoa"
    private const val FIRST_LOAN_APPLICATION = "7ihdcu"
    private const val LOAN_SUCCESS = "42a3ku"
    private const val RISK_SUCCESS = "89myht"
    private const val OTP_BUTTON_TAP = "1do039"
    private const val LOGIN_BUTTON_TAP = "cs547j"
    private const val IDENTITY_STEP_ONE_TAP = "kfxgle"
    private const val IDENTITY_STEP_TWO_TAP = "1ll60x"
    private const val IDENTITY_STEP_THREE_TAP = "qulqh4"
    private const val IDENTITY_STEP_FOUR_TAP = "rag3l6"
    private const val SELFIE_SUBMIT_TAP = "4lbpmz"

    fun otpButtonTapped() = track(OTP_BUTTON_TAP)

    fun loginButtonTapped() = track(LOGIN_BUTTON_TAP)

    fun identitySubmitTapped(screen: Screen) {
        when (screen) {
            Screen.WALLET_SETUP -> track(IDENTITY_STEP_ONE_TAP)
            Screen.CONTACTS -> track(IDENTITY_STEP_TWO_TAP)
            Screen.PERSONAL_INFO -> track(IDENTITY_STEP_THREE_TAP)
            Screen.IDENTITY_CARD, Screen.IDENTITY_FILLED, Screen.DRIVER_LICENSE, Screen.VOTER_CARD ->
                track(IDENTITY_STEP_FOUR_TAP)
            Screen.FACE_PREVIEW -> track(SELFIE_SUBMIT_TAP)
            else -> Unit
        }
    }

    /** Call only when the login response reports that this is a newly registered user. */
    fun loginResponse(context: Context, isRegistered: Boolean) {
        if (isRegistered) {
            track(REGISTERED)
            facebookEvent(context, AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION)
        }
    }

    /** Call with the first-loan flag returned by the successful loan application response. */
    fun loanApplicationResponse(context: Context, isFirstApply: Boolean) {
        if (isFirstApply) {
            track(FIRST_LOAN_APPLICATION)
            facebookEvent(context, AppEventsConstants.EVENT_NAME_ADDED_TO_CART)
        }
    }

    /** Call after the backend confirms the loan was successfully issued. */
    fun loanSucceeded() = track(LOAN_SUCCESS)

    /** Call after the backend confirms risk verification succeeded. */
    fun riskSucceeded() = track(RISK_SUCCESS)

    private fun track(token: String) {
        Adjust.trackEvent(AdjustEvent(token))
    }

    private fun facebookEvent(context: Context, eventName: String) {
        if (BuildConfig.FACEBOOK_APP_ID.isNotBlank() &&
            BuildConfig.FACEBOOK_CLIENT_TOKEN.isNotBlank() && FacebookSdk.isInitialized()
        ) {
            runCatching { AppEventsLogger.newLogger(context.applicationContext).logEvent(eventName) }
        }
    }
}
