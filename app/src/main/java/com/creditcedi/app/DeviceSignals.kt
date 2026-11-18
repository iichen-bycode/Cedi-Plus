package com.creditcedi.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager

internal fun Context.deviceRequestFacts(): Map<String, String> {
    val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val hasPhoneAccess = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    val slots = runCatching {
        if (!hasPhoneAccess) 0
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephony.activeModemCount
        else telephony.phoneCount
    }.getOrDefault(0)
    val activeSims = runCatching {
        if (hasPhoneAccess) getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoCount.coerceAtMost(slots) else 0
    }.getOrDefault(0)
    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val vpn = runCatching {
        val network = connectivity.activeNetwork
        network != null && connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)
    val fingerprint = (Build.FINGERPRINT + Build.MODEL + Build.MANUFACTURER).lowercase()
    val emulator = listOf("generic", "emulator", "sdk_gphone", "goldfish", "ranchu").any(fingerprint::contains)
    val rooted = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk"
    ).any { path -> runCatching { java.io.File(path).exists() }.getOrDefault(false) }
    return mapOf(
        ApiField.IS_EMULATOR to if (emulator) "1" else "0",
        ApiField.IS_VPN to if (vpn) "1" else "0",
        ApiField.IS_ROOT to if (rooted) "1" else "0",
        ApiField.SIM_SLOTS to slots.toString(),
        ApiField.ACTIVE_SIMS to activeSims.toString()
    )
}
