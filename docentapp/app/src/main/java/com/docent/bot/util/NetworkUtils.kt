package com.docent.bot.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String? {
        try {
            // Try to get from WifiManager first
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.ipAddress?.let { ip ->
                if (ip != 0) {
                    return intToIp(ip)
                }
            }

            // Fallback to NetworkInterface
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun getSubnetAddress(context: Context): String? {
        val localIp = getLocalIpAddress(context) ?: return null
        return localIp.substringBeforeLast(".")
    }

    fun getBroadcastAddress(context: Context): InetAddress? {
        try {
            val localIp = getLocalIpAddress(context) ?: return null
            val subnet = localIp.substringBeforeLast(".")
            return InetAddress.getByName("$subnet.255")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
