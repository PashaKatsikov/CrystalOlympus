package com.crystalolympus.crystalolympusgame.waypoint

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class LinkSensor(ctx: Context) {

    private val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

    /** Whether some network is currently active and claims internet capability. */
    fun isConnected(): Boolean {
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Whether a TCP handshake actually completes — capability can lie. */
    suspend fun hasRealInternet(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress("1.1.1.1", 53), 3_000)
                true
            }
        } catch (_: Exception) { false }
    }

    /**
     * Streams the up/down state of the DEFAULT network. A default-network callback
     * is used instead of a capability-filtered request because only the former
     * reports onLost the instant Wi-Fi or cellular is switched off. That is what
     * lets the WebView host react while no page request is outstanding — the case
     * that otherwise leaves a frozen page in front of the user.
     */
    val connectivityFlow: Flow<Boolean> = callbackFlow {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onUnavailable() { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        trySend(isConnected())
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }.distinctUntilChanged()
}
