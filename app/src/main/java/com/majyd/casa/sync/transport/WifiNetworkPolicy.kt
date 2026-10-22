package com.majyd.casa.sync.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Applies the optional household SSID restriction without treating SSID as authentication. */
@Singleton
class WifiNetworkPolicy @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)

    fun isAllowed(configuredSsid: String): Boolean {
        // Pairing is restricted to the active Wi-Fi transport; Android may hide the SSID.
        return isConnectedToWifi()
    }

    fun isConnectedToWifi(): Boolean {
        val network = runCatching { connectivityManager?.activeNetwork }.getOrNull() ?: return false
        val capabilities = runCatching { connectivityManager?.getNetworkCapabilities(network) }.getOrNull()
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun currentSsid(): String? {
        val actual = runCatching { wifiManager?.connectionInfo?.ssid.orEmpty() }.getOrDefault("")
            .trim()
            .trim('"')
        return actual.takeUnless { it.isEmpty() || it == UNKNOWN_SSID }
    }

    companion object {
        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
