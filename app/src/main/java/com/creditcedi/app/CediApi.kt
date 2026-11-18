package com.creditcedi.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object ApiField {
    const val SIGNATURE = "Paw9RfBednbaaZSy1bW8h16j"
    const val NONCE = "iezboTIQil02BZN7Bfi21IXJ6NOcelBypE2G"
    const val RESULT = "Fn337ItaYuQQcVYsPgI"
    const val MESSAGE = "AC8LChNSt2UaRrYCVk3xpOxYB"
    const val PHONE = "TVBtH9wZFb7xYFXjcunj4D5FwyWvn19BXYb"
    const val CODE = "MuC9tWP"
    const val SIM_SLOTS = "S3lVuQANnjeLvbWMM8hsM8d6dbVP36a"
    const val ACTIVE_SIMS = "b3O9q1CJZTdnQqf8Mg4PpNxpFrDBj"
    const val IS_EMULATOR = "TJHNgKLBCzlU5mrUEnJ5R45D6IA9eYt09Kpu"
    const val IS_VPN = "srEMM8ULe0x7XOC6"
    const val IS_ROOT = "LFKoS2WC"
    const val TYPE = "FvrVrV3ebC2"
    const val PRODUCT_ID = "aJgUaIA9"
    const val PRODUCT_LIST = "aXJbCCRLZ8I3XsJGLl"
    const val PRODUCT_NAME = "xYv1wsXnomukKPpQVVwCVJg8BqpCzpjSp1v"
    const val PRODUCT_LOGO = "K17GJGCM56tTaCNHvMkWb"
    const val PRODUCT_DAYS = "BCwLFc"
    const val PRODUCT_AMOUNT = "JfOu7kcI88W9TBzo00r"
    const val LOAN_AMOUNT = "dR7400pEJiSCUt6CmZnxintCEG2PkkPP82"
    const val ORDER_APPLY_DATE = "ASI1FhRn9Oc0DkP9kOIHF19HKdpZgetK"
    const val PRODUCT_RATE = "kOnLw71EhoWj35wY1"
    const val PRODUCT_APR = "y92eu3PHgZmuAWYRAO7fwYB5tk"
    const val LOGIN_TOKEN = "UWB6OANc"
    const val REGISTERED = "K1jMPLmPfBXOvafyriRJWfKZrVkDV4xKHkfF"
    const val FIRST_APPLICATION = "xoTfIDf4pIeb90lOzHtpveqQdTcEzI2"
    const val USER_STATUS = "XBpKalOp6vf4M"
    const val USER_LIVENESS = "FhA2nvy8tZANrvy"
    const val NEED_COLLECT_INFO = "CMV0oqiFrrXE8d1S2nbBlU2q"
    const val USER_PAY_FAIL = "udqkCW8RFtdFtR4uuXQ3e9ZqmFhFPl"
    const val PAY_FAIL_CONTENT = "Ty49iIO4rWoIiPpvxGDAQEDjvtolfFaG"
    const val FEATURE_TYPE = "N72HBUwMcWzuscNUqIUJFpc"
    const val RECOMMENDED = "KdpW3jUCxJ7Mbs2iJwOb4HIS7yeg7UmKs4C"
    const val AUTH_STEP = "CUzM2bydF8mkwf8jWg1U"
    const val FRONT_IMAGE = "ylRFC8yXcjzStKHYQFx72YUiolT"
    const val BACK_IMAGE = "tGVPdt"
    const val FULL_NAME = "AQJRSp6DuHuCyOW8CRU"
    const val ID_NUMBER = "rApkj6CUNrlL"
    const val BIRTH_DATE = "DLt54NjnbKl7h6fJ7UAj9"
    const val GENDER = "uWu2kb7tZLYyRtJsRaI99wmADw"
    const val MARITAL_STATUS = "OgHYDrz1J7u2tjOim"
    const val EDUCATION = "xUaqlj3v2bQtEJNyMe6vf2FE79wacUbLY"
    const val ADDRESS = "HHOcYVHrmk8x8w5sk9qkxAu9gNEj"
    const val INDUSTRY = "WZEFTkubWcYICe5u"
    const val JOB_TITLE = "jZ90q6Z"
    const val MONTHLY_INCOME = "yckpyaB4LSkVvcpUOJ94kamgT7f51bg"
    const val EMAIL = "GmHw3WlHRXAmnME89acjoKS02"
    const val MESSAGING_ACCOUNT = "zIRbftlbW0m8bpAcHwE1"
    const val PAN_IMAGE = "UcVoDXTasKB23y5TqyGMJpxdO0LvCAxXbOD7"
    const val PAN_NUMBER = "c7GlXHofp6NtizcAFb"
    const val APPLY_AMOUNT = "ki841Jze78nD2axH5xOGATauVUVNt5UE3"
    const val CONTACTS = "V2riUJ2sLZ13f0p30lQ3mnf"
    const val CONTACT_COUNT = "IWnfliS3dz9Jf6R"
    const val CONTACT_NAME = "EnNXM1QVA3NkDNCE8MuXhKS"
    const val CONTACT_PHONE = "FzAqXfNV1POfpuFPPutDGSQErIOHm"
    const val CONTACT_RELATION = "b8XeMSKRbifVmINIWn"
    const val BANK_NAME = "GMdb35kxqCWvDOTDZvXRsoYVpmoDw9RxvDsl"
    const val BANK_ACCOUNT = "kAyzZ6gpsz"
    const val BANK_CODE = "leXiX4JL01YyMVVgF6E76J9U"
    const val CARD_TYPE = "BlaDIqxR"
    const val PAY_CYCLE = "qXD6g3mD"
    const val FRONT_LICENSE = "vf2xp6xYyVacgSYOBYPATWv0"
    const val BACK_LICENSE = "YAEOwsyOcWdgZ0FK9DLx8PI9Qxy4DdXoE"
    const val LICENSE_NUMBER = "iC3AzysyhvcqcEtJP5K2XUY5gmektN"
    const val FRONT_VOTER_CARD = "HVwahKH2IVEWSR"
    const val BACK_VOTER_CARD = "XfugbTHs"
    const val VOTER_NUMBER = "PGM4HcNvHEwC6EPWp0jHc"
    const val WALLET_ACCOUNT = "U6BbTEFuGZyMIMfOBF7YtCjqed9KklXRg3y"
    const val WALLET_TYPE = "InoJcGhR34mxk8zbVNDl0"
    const val CHILDREN = "bYxyRilo7Vox1zClirmXVBy749PEoee8wfjLT"
    const val PROVINCE = "LXdNF87LTxv6oYzKLEs55Fepn"
    const val DISTRICT = "piRoQYfiObcN"
    const val FACE_STEP = "YCVhohRSomcL5WviR4ZMpKXV"
    const val FACE_IMAGE = "otKOKZBe9SuBOdayz"
    const val FACE_SCORE = "fl63eBqCeQlnXmzk"
    const val AWS_BUCKET = "tTVJ4ZvbgpNRlcbfc5F5U1GbLZmKmW9"
    const val AWS_FOLDER = "vwcco8zXOrv52ZVz"
    const val AWS_REGION = "J0LbJt0zNiR1w5RvvlTJb4bP16DDrv0K3tY"
    const val AWS_HOST = "sCbaSstnBD7h36Wlqw0d"
    const val AWS_CREDENTIALS = "aELyXVckH8CUnb5f"
    const val SPACE_STATUS = "YfUlEt5plgtrahjNRQu53Up9i2MK80zfXY"
    const val LOAN_PRODUCT = "Nbul5DjbUrZBFQ"
    const val ORDER_INFO = "NgVWh7Oyvb"
    const val LOAN_STATUS = "cEZbFwcFzGHskRuHFTZZBM13C2I14E08eg2"
    const val PAYOUT_FAILED_FLAG = "ZgmeJg5rolZmox5CbrwOJJJcxoK"
    const val OVERDUE_DAYS = "sNrOU8KPLNjTPL"
    const val FREEZE_DAYS = "vNC3k6nz"
    const val ORDER_NO = "ZCPLpDfz3NTWyPOB"
    const val PAGE = "aztJ55T0eYX6radDZZvd6HupQ4RHCC"
    const val PAGE_SIZE = "YigeBsIvWwD8Uyzj7hQC9ayVYxITQEywsH"
    const val ORDER_STATUS = "BJW34CAvUgrp"
    const val ORDER_GROUP = "olDyMP09l7ENT9EobHAVLxz"
    const val ORDER_LIST = "fK3Y2H8xO0uo"
    const val FEEDBACK_TYPE = "W2hzQ2o5DutDXe1QLtq"
    const val FEEDBACK_TEXT = "qTLXmQ2tzWW2tjwcs8tCo"
    const val FEEDBACK_IMAGES = "im68CI54ud1AmFajgS6JZY6D9GZDRhA0SmTI"
    const val FEEDBACK_FILES = "xVqStN"
    const val FEEDBACK_LIST = "rNSVOXfxnVX"
    const val REPAYMENT_TYPE = "VzGODyJvvP6ECRHOOhy98Ob4T03"
    const val REPAYMENT_DATE = "H4ybNEwpNBNOinM"
    const val REPAYMENT_PATH = "HovLVNHbuTJCrbUGEIVP5PZ0"
    const val EXTENSION_REPAY_DATE = "HcxF0OV8Y6Q19IvupapfjuGb8tviyGO40"
    const val WEBVIEW_TYPE = "ewNtovCLbvDfnhJgcWDaZ"
    const val EXTENSION_DAYS = "uxyNMiHtJWXzVJUCPzzP"
    const val POPUP_ACTION = "Lub5OGJ8"
    const val SCORE = "eeII2Z7DjpWMLUfvVNUZTArb"
    const val RATING_TEXT = "akQzcFKDEIwVMjbu1a6AYGEX"
    const val RATING_DIALOG = "UOG4koLIHSDs0u2FpIew1NnPnYK9g4ax"
    const val LOW_SCORE_TIPS = "bWUJKvKWqYhRjm8j7FKJtqeanr"
    const val DEVICE_DATA = "rH5ZuZZvEw8yRgFfySOSuw"
    const val EVENT_LIST = "rClAKpPexTNJ32vVZXYmlvVK"
    const val EVENT_PARAMETER = "l9x53xpMq5IDuGQyy8nwf8LZtcze5c"

    const val HEADER_LANGUAGE = "zWBh4QqCc7nn4"
    const val HEADER_COUNTRY = "cYMOErLbBAJD1wxAedGd1ds6eBwpE"
    const val HEADER_TOKEN = "eCDN7KmfpiGXX"
    const val HEADER_DEVICE = "PwS5PMbnCPAuAvalHhO1pvpGfabGnCzoUx"
    const val DEVICE_APP_VERSION = "jIQXU1WwhYBpbdhwtVYrl"
    const val DEVICE_PACKAGE = "T41AKreLQ8mdW67tdDu"
    const val DEVICE_BRAND = "K9KWYEodQw3TD3ax0HorhoG1K1enr"
    const val DEVICE_CHANNEL = "bTFQ6zSsYP9l0QoF61a7"
    const val DEVICE_MODEL = "gPJwnKYYMH4vKtv3fsP04ED"
    const val DEVICE_MAC = "opYwcfYR3yXfRZudU5"
    const val DEVICE_OS = "bby8ifneiPBjSZTQs"
    const val DEVICE_OS_VERSION = "KrsbjrOPCakk27"
    const val DEVICE_FEATURE_TYPE = "N72HBUwMcWzuscNUqIUJFpc"
    const val DEVICE_ANDROID_ID = "f3xQgxyub4YCyN2CTwXTlLCMlxhkHRjif"
    const val DEVICE_AD_ID = "pK1yXlC9RZuug94O7Gz8IIhdq0"
    const val DEVICE_UUID = "am1XPAk2Jw9t42hSpnGNHf"
    const val DEVICE_NATIVE_CHANNEL = "J7Mmnkh"
    const val DEVICE_ADJUST_ID = "Ppr0duLBANvtQbyjJtNBdUViSypn"
    const val DEVICE_CAMPAIGN = "plpLsDqNnaz1LDsgYV9ixQ5"
    const val DEVICE_FINGERPRINT = "osIrCvAADjbAyoyivDdl4cGvF77oLCKlJNh24"
}

internal enum class ApiRoute(val code: String) {
    FIRST_OPEN("2.3.1.0"), PRODUCT_LIST("2.3.1.1"), SEND_CODE("2.3.1.2"), LOGIN("2.3.1.3"),
    LOGOUT("2.3.1.4"), USER_INFO("2.3.1.5"), AUTH_INFO("2.3.1.7"), SAVE_AUTH("2.3.1.8"),
    UPDATE_WALLET("2.3.1.9"), AWS_UPLOAD_INFO("2.3.1.10"), SAVE_LIVENESS("2.3.1.12"),
    SPACE_INFO("2.3.2.0"), CREATE_ORDER("2.3.2.1"), ORDER_LIST("2.3.2.2"), ORDER_DETAIL("2.3.2.3"),
    SAVE_FEEDBACK("2.3.2.4"), FEEDBACK_LIST("2.3.2.5"), REPAYMENT_PATH("2.3.2.6"), EXTENSION_INFO("2.3.2.7"),
    DEVICE_REFRESH("2.3.2.8"), RATING("2.3.1.32"), EVENT_ANONYMOUS("2.3.1.98"), EVENT_AUTHENTICATED("2.3.2.99")
}

internal open class ApiException(message: String, val code: Int? = null) : Exception(message)
internal class ExpiredSessionException : ApiException("Login expired. Please sign in again.", -1)

internal class CediApi(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()
    private val preferences = context.getSharedPreferences("api-session", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    suspend fun firstOpen() = post(ApiRoute.FIRST_OPEN)
    suspend fun firstOpenIfNeeded() {
        if (!preferences.getBoolean("firstOpenSent", false)) {
            firstOpen()
            preferences.edit().putBoolean("firstOpenSent", true).apply()
        }
    }
    suspend fun products() = post(ApiRoute.PRODUCT_LIST)
    suspend fun requestCode(phone: String, device: Map<String, String>) = post(ApiRoute.SEND_CODE, device + mapOf(
        ApiField.PHONE to phone,
        ApiField.TYPE to "1"
    ))
    suspend fun login(phone: String, code: String, device: Map<String, String>): JSONObject = post(
        ApiRoute.LOGIN,
        device + mapOf(ApiField.PHONE to phone, ApiField.CODE to code)
    ).also { response ->
        response.optString(ApiField.LOGIN_TOKEN).takeIf(String::isNotBlank)?.let {
            preferences.edit().putString("token", it).apply()
        }
    }
    suspend fun logout() = post(ApiRoute.LOGOUT)
    suspend fun userInfo(recommended: Boolean = false, device: Map<String, String> = emptyMap()) = post(
        ApiRoute.USER_INFO,
        device + mapOf(ApiField.RECOMMENDED to if (recommended) "1" else "0")
    ).also { response ->
        response.optString(ApiField.FEATURE_TYPE).takeIf(String::isNotBlank)?.let {
            preferences.edit().putString("featureType", it).apply()
        }
    }
    suspend fun authenticationInfo() = post(ApiRoute.AUTH_INFO)
    suspend fun saveAuthentication(parameters: Map<String, String>) = post(ApiRoute.SAVE_AUTH, parameters)
    suspend fun updateWallet(account: String, type: String, mode: String = "2") = post(ApiRoute.UPDATE_WALLET, mapOf(
        ApiField.WALLET_ACCOUNT to account,
        ApiField.WALLET_TYPE to type,
        ApiField.TYPE to mode
    ))
    suspend fun freshUploadCredentials() = post(ApiRoute.AWS_UPLOAD_INFO)
    suspend fun saveLiveness(imagePath: String, score: String = "") = post(ApiRoute.SAVE_LIVENESS, buildMap {
        put(ApiField.FACE_STEP, "liveness")
        put(ApiField.FACE_IMAGE, imagePath)
        if (score.isNotBlank()) put(ApiField.FACE_SCORE, score)
    })
    suspend fun spaceInfo(productId: String, device: Map<String, String>) = post(
        ApiRoute.SPACE_INFO,
        device + mapOf(ApiField.PRODUCT_ID to productId)
    )
    suspend fun createOrder(productId: String, compressedDevice: String, device: Map<String, String>) = post(
        ApiRoute.CREATE_ORDER,
        device + mapOf(ApiField.PRODUCT_ID to productId, ApiField.DEVICE_DATA to compressedDevice)
    )
    suspend fun orderList(page: Int, pageSize: Int, status: Int? = null) = post(ApiRoute.ORDER_LIST, buildMap {
        put(ApiField.PAGE, page.toString())
        put(ApiField.PAGE_SIZE, pageSize.toString())
        status?.let { put(ApiField.ORDER_STATUS, it.toString()) }
    })
    suspend fun orderDetail(orderNumber: String, device: Map<String, String>) = post(
        ApiRoute.ORDER_DETAIL,
        device + mapOf(ApiField.ORDER_NO to orderNumber)
    )
    suspend fun saveFeedback(orderNumber: String, type: String, text: String, images: String, files: String) = post(
        ApiRoute.SAVE_FEEDBACK,
        mapOf(
            ApiField.ORDER_NO to orderNumber,
            ApiField.FEEDBACK_TYPE to type,
            ApiField.FEEDBACK_TEXT to text,
            ApiField.FEEDBACK_IMAGES to images,
            ApiField.FEEDBACK_FILES to files
        )
    )
    suspend fun feedbackList() = post(ApiRoute.FEEDBACK_LIST)
    suspend fun repaymentPath(orderNumber: String, type: String, date: String) = post(ApiRoute.REPAYMENT_PATH, buildMap {
        put(ApiField.ORDER_NO, orderNumber)
        put(ApiField.REPAYMENT_TYPE, type)
        put(ApiField.REPAYMENT_DATE, date)
    })
    suspend fun extensionInfo(orderNumber: String) = post(ApiRoute.EXTENSION_INFO, mapOf(ApiField.ORDER_NO to orderNumber))
    suspend fun refreshDevice(compressedDevice: String) = post(ApiRoute.DEVICE_REFRESH, mapOf(ApiField.DEVICE_DATA to compressedDevice))
    suspend fun submitRating(action: String, score: Int? = null, text: String = "") = post(ApiRoute.RATING, buildMap {
        put(ApiField.POPUP_ACTION, action)
        put(ApiField.SCORE, score?.toString().orEmpty())
        put(ApiField.RATING_TEXT, text)
    })
    suspend fun sendEvents(eventsJson: String, authenticated: Boolean) = post(
        if (authenticated) ApiRoute.EVENT_AUTHENTICATED else ApiRoute.EVENT_ANONYMOUS,
        mapOf(ApiField.EVENT_LIST to eventsJson)
    )
    suspend fun sendEvent(type: String, page: String, authenticated: Boolean) = sendEvents(
        org.json.JSONArray().put(JSONObject().put(ApiField.EVENT_PARAMETER, page).put(ApiField.TYPE, type)).toString(),
        authenticated
    )

    suspend fun compressedDevicePayload(): String = withContext(Dispatchers.Default) {
        val header = JSONObject(DeviceHeader(context, preferences).asJson())
        val raw = DevicePayloadCollector(context, header).asJson().toByteArray(Charsets.UTF_8)
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(raw)
            deflater.finish()
            val buffer = ByteArray(1024)
            val output = java.io.ByteArrayOutputStream()
            while (!deflater.finished()) {
                val length = deflater.deflate(buffer)
                output.write(buffer, 0, length)
            }
            android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        } finally {
            deflater.end()
        }
    }

    fun clearSession() = preferences.edit().remove("token").remove("featureType").apply()

    fun hasSession() = !preferences.getString("token", null).isNullOrBlank()

    internal suspend fun post(route: ApiRoute, parameters: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val nonce = nonce()
            val values = LinkedHashMap(parameters.filterKeys { it != ApiField.SIGNATURE && it != ApiField.NONCE })
            values[ApiField.NONCE] = nonce
            values[ApiField.SIGNATURE] = signature(values)
            val form = FormBody.Builder().apply { values.forEach { (key, value) -> add(key, value) } }.build()
            val base = BuildConfig.API_BASE_URL.trimEnd('/')
            val deviceInfo = DeviceHeader(context, preferences).asJson()
            val request = Request.Builder()
                .url("$base/${route.code}")
                .header(ApiField.HEADER_LANGUAGE, "en")
                .header(ApiField.HEADER_COUNTRY, "GH")
                .header(ApiField.HEADER_TOKEN, preferences.getString("token", "").orEmpty())
                .header(ApiField.HEADER_DEVICE, deviceInfo)
                .post(form)
                .build()
            val body = client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException("Service request failed (${response.code}).")
                payload
            }
            val response = runCatching { JSONObject(body) }.getOrElse { throw ApiException("Invalid service response.") }
            when (response.optInt(ApiField.RESULT, 0)) {
                -1 -> {
                    clearSession()
                    throw ExpiredSessionException()
                }
                0 -> throw ApiException(response.optString(ApiField.MESSAGE).ifBlank { "Request could not be completed." }, 0)
                1 -> response
                else -> throw ApiException("Invalid service response.")
            }
        }

    private fun signature(parameters: Map<String, String>): String {
        val key = CediPlusConfig.API_SIGNING_KEY
        if (key.isBlank()) throw ApiException("API signing key is missing. Add cedi.signingKey to local.properties.")
        val canonical = parameters.asSequence()
            .filter { (name, value) -> name != ApiField.SIGNATURE && value.isNotEmpty() }
            .sortedBy { it.key }
            .joinToString("&") { (name, value) -> "$name=$value" } + "&${CediPlusConfig.API_SIGNING_KEY_PARAMETER}"
        val digest = MessageDigest.getInstance("MD5").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(Locale.US, it) }
    }

    private fun nonce(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(24) { repeat(24) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}

private class DeviceHeader(private val context: Context, private val preferences: android.content.SharedPreferences) {
    private val attribution = context.getSharedPreferences("attribution", Context.MODE_PRIVATE)

    suspend fun asJson(): String {
        val uuid = preferences.getString("deviceUuid", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            preferences.edit().putString("deviceUuid", it).apply()
        }
        val advertisingId = runCatching {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (info.isLimitAdTrackingEnabled) "" else info.id.orEmpty()
        }.getOrDefault("")
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
        return JSONObject().apply {
            put(ApiField.DEVICE_APP_VERSION, version)
            put(ApiField.DEVICE_PACKAGE, CediPlusConfig.HEADER_PACKAGE)
            put(ApiField.DEVICE_BRAND, Build.BRAND.orEmpty())
            put(ApiField.DEVICE_CHANNEL, attribution.getString("channel", "Other").takeUnless { it.isNullOrBlank() } ?: "Other")
            put(ApiField.DEVICE_MODEL, Build.MODEL.orEmpty())
            put(ApiField.DEVICE_MAC, "")
            put(ApiField.DEVICE_OS, "android")
            put(ApiField.DEVICE_OS_VERSION, Build.VERSION.RELEASE.orEmpty())
            put(ApiField.DEVICE_FEATURE_TYPE, preferences.getString("featureType", "").orEmpty())
            put(ApiField.DEVICE_ANDROID_ID, Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty())
            put(ApiField.DEVICE_AD_ID, advertisingId)
            put(ApiField.DEVICE_UUID, uuid)
            put(ApiField.DEVICE_NATIVE_CHANNEL, attribution.getString("nativeChannel", "").orEmpty())
            put(ApiField.DEVICE_ADJUST_ID, attribution.getString("adjustId", "").orEmpty())
            put(ApiField.DEVICE_CAMPAIGN, attribution.getString("campaign", "").orEmpty())
            put(ApiField.DEVICE_FINGERPRINT, "")
        }.toString()
    }
}

internal class S3ImageUploader(private val context: Context, private val api: CediApi) {
    suspend fun upload(bitmap: Bitmap): String {
        val imageBytes = withContext(Dispatchers.Default) { jpegBytes(bitmap) }
        return uploadObject(imageBytes, "image/jpeg", "jpg")
    }

    suspend fun uploadUri(uri: Uri, isImage: Boolean): String {
        if (isImage) {
            val bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ApiException("Unable to read the selected image.")
                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 4096) sampleSize *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw ApiException("Unable to read the selected image.")
            }
            return try {
                upload(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ApiException("Unable to read the selected document.")
        }
        if (bytes.size > 20 * 1024 * 1024) throw ApiException("The selected PDF is too large.")
        return uploadObject(bytes, "application/pdf", "pdf")
    }

    private suspend fun uploadObject(bytes: ByteArray, contentType: String, extension: String): String {
        val parameters = api.freshUploadCredentials()
        val credentials = parameters.optJSONObject(ApiField.AWS_CREDENTIALS)
            ?: throw ApiException("Upload credentials are missing.")
        val bucket = parameters.optString(ApiField.AWS_BUCKET)
        val folder = parameters.optString(ApiField.AWS_FOLDER).trim('/')
        val region = parameters.optString(ApiField.AWS_REGION)
        val access = credentials.optString("accessKeyId")
        val secret = credentials.optString("accessKeySecret")
        val session = credentials.optString("securityToken")
        if (bucket.isBlank() || region.isBlank() || access.isBlank() || secret.isBlank() || session.isBlank()) {
            throw ApiException("Upload credentials are incomplete.")
        }
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val fileName = buildString(24) {
            val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            repeat(24) { append(alphabet[SecureRandom().nextInt(alphabet.length)]) }
        }
        val pathSegments = folder.split('/').filter(String::isNotBlank).toMutableList()
        if (extension == "pdf") {
            if (pathSegments.lastOrNull()?.equals("img", ignoreCase = true) == true) pathSegments[pathSegments.lastIndex] = "pdf"
            else if (pathSegments.lastOrNull()?.equals("pdf", ignoreCase = true) != true) pathSegments += "pdf"
        }
        val key = (pathSegments + date + "$fileName.$extension").joinToString("/")
        aws.sdk.kotlin.services.s3.S3Client.fromEnvironment {
            this.region = region
            credentialsProvider = aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider {
                accessKeyId = access
                secretAccessKey = secret
                sessionToken = session
            }
        }.use { s3 ->
            val request = aws.sdk.kotlin.services.s3.model.PutObjectRequest {
                this.bucket = bucket
                this.key = key
                this.contentType = contentType
                this.body = aws.smithy.kotlin.runtime.content.ByteStream.fromBytes(bytes)
            }
            s3.putObject(request)
        }
        return key
    }

    private fun jpegBytes(source: Bitmap): ByteArray {
        val shortest = minOf(source.width, source.height)
        val longest = maxOf(source.width, source.height)
        if (shortest <= 0 || longest <= 0 || longest.toFloat() / shortest > 16f) {
            throw ApiException("Image dimensions are not supported.")
        }
        val scale = when {
            longest > 4096 -> 4096f / longest
            shortest < 256 -> 256f / shortest
            else -> 1f
        }
        val resized = if (scale != 1f) Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(256),
            (source.height * scale).toInt().coerceAtLeast(256),
            true
        ) else source
        try {
            var quality = 95
            var encoded = java.io.ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, encoded)
            while (encoded.size() > 400 * 1024 && quality > 50) {
                quality -= 5
                encoded = java.io.ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, encoded)
            }
            return encoded.toByteArray()
        } finally {
            if (resized !== source) resized.recycle()
        }
    }
}
