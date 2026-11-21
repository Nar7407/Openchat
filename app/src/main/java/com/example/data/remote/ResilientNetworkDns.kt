package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Wi-Fi & mobile-resilient DNS resolver for OkHttp.
 *
 * Addresses false [UnknownHostException] errors on Android devices (especially after idle/Doze,
 * Wi-Fi <-> cellular transitions, or dual-stack IPv6 routers) by:
 * 1. Resolving against the active [android.net.Network] via [ConnectivityManager] first.
 * 2. Falling back to [Dns.SYSTEM] and retrying once after a brief backoff if internet is active.
 * 3. Ordering IPv4 addresses ahead of IPv6 so broken IPv6 routes do not stall connections.
 */
class ResilientNetworkDns(private val context: Context) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim()
        if (cleanHost.isEmpty()) throw UnknownHostException("Empty hostname")

        var lastError: UnknownHostException? = null
        val maxAttempts = if (hasActiveInternet()) 2 else 1

        for (attempt in 1..maxAttempts) {
            val resolved = resolveOnce(cleanHost)
            if (resolved.isNotEmpty()) {
                return prioritizeIpv4(resolved)
            }
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(250L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        // Final standard OkHttp system lookup so the exact platform exception is preserved
        return try {
            prioritizeIpv4(Dns.SYSTEM.lookup(cleanHost))
        } catch (uhe: UnknownHostException) {
            lastError = uhe
            throw lastError
        }
    }

    private fun resolveOnce(hostname: String): List<InetAddress> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val viaActive = runCatching { activeNetwork.getAllByName(hostname).toList() }
                    .getOrNull()
                if (!viaActive.isNullOrEmpty()) return viaActive
            }
        }
        return runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
    }

    private fun hasActiveInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> {
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        val others = addresses.filter { it !is Inet4Address }
        return ipv4 + others
    }
}
