package com.seca.core.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether this app can reach the Internet. Android reports no network both
 * without a connection and when the owner keeps the app off the network, as
 * GrapheneOS allows; neither comes with a prompt the app could show.
 */
class NetworkAccess(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun available(): Boolean {
        val manager = connectivity ?: return false
        // Null when there is no default network, and when it is blocked for this app.
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** The access as it changes, starting with the current one. */
    fun changes(): Flow<Boolean> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            trySend(false)
            awaitClose()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var internet = false
            private var blocked = false

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(internet && !blocked)
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                this.blocked = blocked
                trySend(internet && !blocked)
            }

            override fun onLost(network: Network) {
                internet = false
                trySend(false)
            }
        }
        trySend(available())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
